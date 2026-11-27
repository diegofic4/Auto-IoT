"""Run the actual Auto-IoT C controller and HTTP handlers against virtual ESP32 pins."""

import argparse
import ctypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import time


HERE = Path(__file__).resolve().parent
FIRMWARE = HERE.parents[1]
MOCKED_HEADERS = (
    "driver/gpio.h", "esp_check.h", "esp_err.h", "esp_event.h", "esp_http_server.h",
    "esp_log.h", "esp_netif.h", "esp_rom_sys.h", "esp_timer.h", "esp_wifi.h",
    "freertos/FreeRTOS.h", "freertos/semphr.h", "freertos/task.h", "nvs_flash.h",
    "sdkconfig.h",
)


def build_library(compiler, output):
    output.mkdir(parents=True, exist_ok=True)
    include_dir = output / "includes"
    for name in MOCKED_HEADERS:
        header = include_dir / name
        header.parent.mkdir(parents=True, exist_ok=True)
        header.write_text('#include "esp_mock.h"\n', encoding="utf-8")

    command = [str(compiler)]
    is_zig = compiler.stem.lower() == "zig"
    if is_zig:
        command.append("cc")
    if os.name == "nt" and is_zig:
        command += ["-target", "x86_64-windows-gnu"]
    if os.name != "nt":
        command.append("-fPIC")

    library = output / ("auto_iot_bench.dll" if os.name == "nt" else "libauto_iot_bench.so")
    command += [
        "-shared", "-std=c11", "-Wall", "-Wextra", "-Werror", "-DAUTO_IOT_HOST_SIM",
        "-I", str(include_dir), "-I", str(HERE),
        str(HERE / "bench.c"), str(FIRMWARE / "src" / "network.c"),
        "-o", str(library),
    ]
    subprocess.run(command, check=True)
    return library


class FirmwareBench:
    def __init__(self, library):
        self.device = ctypes.CDLL(str(library))
        self.device.bench_start.restype = ctypes.c_int
        self.device.bench_station.argtypes = [ctypes.c_bool]
        self.device.bench_station.restype = None
        self.device.bench_sample.argtypes = [ctypes.c_uint32, ctypes.c_float]
        self.device.bench_sample.restype = None
        self.device.bench_pins.restype = ctypes.c_uint
        self.device.bench_http.argtypes = [
            ctypes.c_char_p, ctypes.c_size_t, ctypes.c_char_p, ctypes.c_size_t,
        ]
        self.device.bench_http.restype = ctypes.c_int
        result = self.device.bench_start()
        if result != 0:
            raise RuntimeError(f"El firmware virtual devolvió {result} al iniciar")

    def station(self, connected):
        self.device.bench_station(connected)

    def sample(self, cm, elapsed_us=70_000):
        self.device.bench_sample(elapsed_us, cm if cm is not None else -1)

    def pins(self):
        mask = self.device.bench_pins()
        return {"IO25_IN1": (mask >> 2) & 1, "IO26_IN2": (mask >> 1) & 1, "IO27_ENA": mask & 1}

    def request(self, path, content_length=0):
        body = ctypes.create_string_buffer(512)
        status = self.device.bench_http(path.encode("ascii"), content_length, body, len(body))
        return status, json.loads(body.value)


def check_firmware(bench):
    def signal(in1, in2, ena):
        assert bench.pins() == {"IO25_IN1": in1, "IO26_IN2": in2, "IO27_ENA": ena}, bench.pins()

    def exchange(path, expected_status):
        status, payload = bench.request(path)
        assert status == expected_status, (path, status, payload)
        return payload

    bench.sample(30)
    assert exchange("/telemetria", 200)["distancia"] == 30
    exchange("/adelante", 503)
    signal(0, 0, 0)
    bench.station(True)

    assert exchange("/adelante", 200)["estadoMotor"] == "AVANZANDO"
    signal(1, 0, 1)
    bench.sample(15)
    signal(0, 0, 0)
    obstacle = exchange("/telemetria", 200)
    assert obstacle["distancia"] == 15 and obstacle["estadoMotor"] == "DETENIDO"
    assert obstacle["frenoActivo"] and obstacle["motivoFreno"] == "OBSTACULO"
    assert exchange("/adelante", 409)["resultado"] == "ERR"

    bench.sample(50)
    signal(0, 0, 0)
    exchange("/izquierda", 422)
    exchange("/derecha", 422)
    exchange("/adelante?velocidad=999", 400)
    exchange("/desconocido", 404)
    assert bench.request("/adelante", 3)[0] == 400
    signal(0, 0, 0)

    assert exchange("/atras", 200)["estadoMotor"] == "RETROCEDIENDO"
    signal(0, 1, 1)
    assert exchange("/parar", 200)["estadoMotor"] == "DETENIDO"
    signal(0, 0, 0)

    exchange("/adelante", 200)
    bench.sample(None)
    signal(0, 0, 0)
    fault = exchange("/telemetria", 200)
    assert not fault["distanciaValida"] and fault["distancia"] == 0
    assert fault["frenoActivo"] and fault["motivoFreno"] == "SIN_ECO"

    bench.sample(40)
    exchange("/adelante", 200)
    bench.sample(40, elapsed_us=2_700_000)
    signal(1, 0, 1)
    exchange("/telemetria", 200)
    bench.sample(40, elapsed_us=2_700_000)
    signal(1, 0, 1)
    bench.sample(40, elapsed_us=310_000)
    signal(0, 0, 0)
    assert exchange("/telemetria", 200)["motivoFreno"] == "SIN_ENLACE"

    exchange("/adelante", 200)
    bench.station(False)
    signal(0, 0, 0)
    exchange("/adelante", 503)
    bench.station(True)
    assert exchange("/telemetria", 200)["estadoMotor"] == "DETENIDO"
    signal(0, 0, 0)
    print("OK: telemetría, enlace, avance, reversa, parada, freno autónomo a 15 cm, eco inválido,", flush=True)
    print("rutas inválidas, corte por desconexión y por 3 s sin tráfico, recuperación sin reanudar movimiento.", flush=True)


def serve(bench, port, starting_distance):
    target_distance = starting_distance
    station_connected = True
    bench.station(True)
    bench.sample(target_distance)

    lock = threading.Lock()
    stopping = threading.Event()
    boot = secrets.token_hex(8)
    started = time.monotonic_ns()
    archive = []

    def reading(historical):
        _, payload = bench.request("/telemetria")
        uptime = (time.monotonic_ns() - started) // 1_000_000
        payload.update(
            idLectura=f"{boot}-{uptime:016x}",
            capturaMs=uptime,
            edadMs=0,
            historial=historical,
        )
        return payload

    def sample_forever():
        previous = time.monotonic()
        next_archive = previous + 15
        while not stopping.wait(0.07):
            now = time.monotonic()
            with lock:
                bench.sample(target_distance, int((now - previous) * 1_000_000))
                if now >= next_archive:
                    if not station_connected and len(archive) < 32:
                        archive.append(reading(True))
                    next_archive = now + 15
            previous = now

    class Handler(BaseHTTPRequestHandler):
        def reply(self, status, value):
            content = json.dumps(value).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(content)

        def event_stream(self):
            with lock:
                if not station_connected:
                    self.reply(503, {"resultado": "ERR", "error": "ENLACE_SIMULADO_CAIDO"})
                    return
                pending = archive.copy()
            self.send_response(200)
            self.send_header("Content-Type", "application/x-ndjson")
            self.end_headers()
            try:
                for record in pending:
                    archived = dict(record)
                    archived["edadMs"] = (time.monotonic_ns() - started) // 1_000_000 - record["capturaMs"]
                    self.wfile.write(json.dumps(archived, separators=(",", ":")).encode() + b"\n")
                while not stopping.is_set():
                    with lock:
                        if not station_connected:
                            return
                        live = reading(False)
                    self.wfile.write(json.dumps(live, separators=(",", ":")).encode() + b"\n")
                    self.wfile.flush()
                    stopping.wait(0.65)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                return

        def do_GET(self):
            if self.path == "/eventos":
                self.event_stream()
                return
            with lock:
                if self.path == "/__bench/pins":
                    self.reply(200, bench.pins())
                    return
                if not station_connected:
                    self.reply(503, {"resultado": "ERR", "error": "ENLACE_SIMULADO_CAIDO"})
                    return
                status, payload = bench.request(self.path, int(self.headers.get("Content-Length", "0")))
            self.reply(status, payload)

        def do_POST(self):
            nonlocal station_connected, target_distance
            if self.path == "/latido" or self.path.startswith("/latido?ack="):
                with lock:
                    if not station_connected:
                        self.reply(503, {"resultado": "ERR", "error": "ENLACE_SIMULADO_CAIDO"})
                        return
                    ack = self.path.partition("ack=")[2]
                    for index, record in enumerate(archive):
                        if record["idLectura"] == ack:
                            del archive[:index + 1]
                            break
                self.reply(200, {"resultado": "ACK"})
                return
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 1024:
                    raise ValueError
                payload = json.loads(self.rfile.read(size))
                with lock:
                    if self.path == "/__bench/distance":
                        cm = payload["cm"]
                        if cm is not None and (type(cm) not in (int, float) or not 2 <= cm <= 400):
                            raise ValueError
                        target_distance = cm
                    elif self.path == "/__bench/station":
                        connected = payload["connected"]
                        if type(connected) is not bool:
                            raise ValueError
                        bench.station(connected)
                        station_connected = connected
                    else:
                        self.reply(404, {"error": "Comando de simulación no encontrado"})
                        return
            except (TypeError, KeyError, ValueError):
                self.reply(400, {"error": "Entrada inválida"})
                return
            self.reply(200, {"distanciaSimulada": target_distance})

        def log_message(self, pattern, *values):
            if self.path != "/telemetria" and not self.path.startswith("/latido"):
                super().log_message(pattern, *values)

    sample_thread = threading.Thread(target=sample_forever, daemon=True)
    with ThreadingHTTPServer(("127.0.0.1", port), Handler) as server:
        print(f"Banco en 127.0.0.1:{port}; desde Android Emulator usa 10.0.2.2:{port}", flush=True)
        sample_thread.start()
        try:
            server.serve_forever()
        finally:
            stopping.set()
            sample_thread.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cc", type=Path, default=Path("zig"))
    parser.add_argument("--output", type=Path, default=FIRMWARE / "build" / "host")
    parser.add_argument("--library", type=Path, help="Usar una biblioteca ya compilada")
    parser.add_argument("--serve", action="store_true")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--distance", type=float, default=45.0)
    args = parser.parse_args()

    library = args.library if args.library else build_library(args.cc, args.output)
    bench = FirmwareBench(library.resolve())
    if args.serve:
        try:
            serve(bench, args.port, args.distance)
        except KeyboardInterrupt:
            pass
    else:
        check_firmware(bench)


if __name__ == "__main__":
    main()
