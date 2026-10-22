#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=========================================================================================
SIMULADOR DE NODO ESP32 — VEHÍCULO AUTÓNOMO / MÓVIL DE INSPECCIÓN IoT
AP Wi-Fi Local ("Auto_IoT_Control" - 192.168.4.1) & Bluetooth RFCOMM SPP
=========================================================================================
Emula el comportamiento físico del microcontrolador ESP32 con:
  - Sensor Ultrasónico HC-SR04 (distancia en cm)
  - Puente H L298N (estados: "DETENIDO", "AVANZANDO", "RETROCEDIENDO")
  - Freno de Emergencia Automático y LED de Advertencia cuando distancia <= 15 cm

Endpoints REST HTTP soportados (GET):
  - /telemetria -> {"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}
  - /adelante   -> Ordena avanzar (bloqueado automáticamente si distancia <= 15 cm)
  - /atras      -> Ordena retroceder (incrementa distancia al obstáculo)
  - /parar      -> Detiene los motores inmediatamente

Uso en Laboratorio / PC:
  1. Modo Servidor HTTP ESP32 (puerto 8080 o 80):
     python3 simulador_esp32.py --mode http --port 8080

  2. Modo Bluetooth Clásico RFCOMM (SPP UUID 00001101-0000-1000-8000-00805F9B34FB):
     python3 simulador_esp32.py --mode bt-rfcomm --bt-mac AA:BB:CC:DD:EE:FF --channel 1
=========================================================================================
"""

import argparse
import json
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Dict, Any

SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
CRITICAL_BRAKE_DISTANCE_CM = 15


class Esp32VehicleState:
    """
    Estado físico sincronizado del Vehículo de Inspección IoT (HC-SR04 + L298N + LED).
    """

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.distancia: int = 45
        self.estado_motor: str = "DETENIDO"
        self.freno_activo: bool = False

    def step_physics(self) -> None:
        """Actualiza la cinemática del vehículo según el estado del Puente H L298N."""
        with self._lock:
            if self.estado_motor == "AVANZANDO":
                self.distancia = max(8, self.distancia - 4)
            elif self.estado_motor == "RETROCEDIENDO":
                self.distancia = min(150, self.distancia + 5)

            if self.distancia <= CRITICAL_BRAKE_DISTANCE_CM:
                self.freno_activo = True
                if self.estado_motor == "AVANZANDO":
                    print(
                        f"[⚠️ FRENO ACTIVO] Obstáculo a {self.distancia} cm (<= 15 cm). "
                        "Puente H L298N detenido automáticamente y LED de alerta encendido."
                    )
                    self.estado_motor = "DETENIDO"
            else:
                self.freno_activo = False

    def apply_command(self, cmd: str) -> Dict[str, Any]:
        clean = cmd.strip().upper().lstrip("/")
        with self._lock:
            if clean == "ADELANTE":
                if self.distancia <= CRITICAL_BRAKE_DISTANCE_CM:
                    self.freno_activo = True
                    self.estado_motor = "DETENIDO"
                    print(
                        f"[BLOQUEO SEGURIDAD] Orden /adelante rechazada: "
                        f"distancia={self.distancia} cm <= 15 cm (Freno Activo)."
                    )
                else:
                    self.estado_motor = "AVANZANDO"
                    print("[L298N] Motor -> AVANZANDO")
            elif clean in ("IZQUIERDA", "IZQ"):
                self.estado_motor = "GIRANDO_IZQUIERDA"
                print("[L298N] Motor -> GIRANDO_IZQUIERDA")
            elif clean in ("DERECHA", "DER"):
                self.estado_motor = "GIRANDO_DERECHA"
                print("[L298N] Motor -> GIRANDO_DERECHA")
            elif clean in ("ATRAS", "ATRÁS"):
                self.estado_motor = "RETROCEDIENDO"
                print("[L298N] Motor -> RETROCEDIENDO")
            elif clean in ("PARAR", "DETENER"):
                self.estado_motor = "DETENIDO"
                print("[L298N] Motor -> DETENIDO")

            return self._snapshot_unlocked()

    def get_telemetry(self) -> Dict[str, Any]:
        self.step_physics()
        with self._lock:
            return self._snapshot_unlocked()

    def _snapshot_unlocked(self) -> Dict[str, Any]:
        return {
            "distancia": int(self.distancia),
            "estadoMotor": self.estado_motor,
            "frenoActivo": bool(self.freno_activo),
        }


VEHICLE_STATE = Esp32VehicleState()


class Esp32HttpRequestHandler(BaseHTTPRequestHandler):
    """Manejador de peticiones HTTP que emula el WebServer embebido del ESP32."""

    def do_GET(self) -> None:
        path = self.path.split("?")[0].lower()

        if path == "/adelante":
            payload = VEHICLE_STATE.apply_command("ADELANTE")
            self._send_json(200, payload)
        elif path == "/izquierda":
            payload = VEHICLE_STATE.apply_command("IZQUIERDA")
            self._send_json(200, payload)
        elif path == "/derecha":
            payload = VEHICLE_STATE.apply_command("DERECHA")
            self._send_json(200, payload)
        elif path == "/atras":
            payload = VEHICLE_STATE.apply_command("ATRAS")
            self._send_json(200, payload)
        elif path == "/parar":
            payload = VEHICLE_STATE.apply_command("PARAR")
            self._send_json(200, payload)
        elif path == "/telemetria" or path == "/":
            payload = VEHICLE_STATE.get_telemetry()
            self._send_json(200, payload)
        else:
            self._send_json(
                404,
                {
                    "error": f"Endpoint no encontrado: {path}",
                    "endpointsValidos": ["/adelante", "/izquierda", "/derecha", "/atras", "/parar", "/telemetria"],
                },
            )

    def _send_json(self, status_code: int, data: Dict[str, Any]) -> None:
        raw = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt: str, *args: Any) -> None:
        state = VEHICLE_STATE._snapshot_unlocked()
        print(
            f"[ESP32-HTTP] {self.address_string()} -> {fmt % args} | "
            f"JSON={json.dumps(state)}"
        )


def run_http_simulator(bind_ip: str, port: int) -> None:
    server = HTTPServer((bind_ip, port), Esp32HttpRequestHandler)
    print("=========================================================================")
    print(" SIMULADOR ESP32 ('Auto_IoT_Control') ACTIVO EN MODO WI-FI HTTP REST")
    print(f" Escuchando en: http://{bind_ip}:{port}")
    print(" Endpoints disponibles:")
    print("   GET /telemetria -> {\"distancia\": 45, \"estadoMotor\": \"DETENIDO\", \"frenoActivo\": false}")
    print("   GET /adelante   -> Acciona Puente H L298N hacia adelante")
    print("   GET /atras      -> Acciona Puente H L298N en reversa")
    print("   GET /parar      -> Detiene motores")
    print("=========================================================================")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[!] Apagando Simulador HTTP ESP32 de forma segura...")
    finally:
        server.server_close()


def run_bluetooth_rfcomm_simulator(bt_mac: str, channel: int, interval: float) -> None:
    if not hasattr(socket, "AF_BLUETOOTH") or not hasattr(socket, "BTPROTO_RFCOMM"):
        print(
            "[ERROR] Esta instalación de Python no soporta AF_BLUETOOTH nativo.\n"
            "        Use '--mode http --port 8080' o ejecute en Linux/Windows 10+ con stack BT."
        )
        sys.exit(1)

    print(f"[*] Conectando Simulador ESP32 por Bluetooth RFCOMM a {bt_mac} (Canal {channel}, UUID {SPP_UUID})...")
    bt_sock = None
    try:
        bt_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        bt_sock.connect((bt_mac, channel))
        bt_sock.settimeout(0.25)
        print(f"[OK] Enlace Bluetooth SPP activo con {bt_mac}")

        while True:
            # Escuchar si la app Android envió algún comando ("ADELANTE\n", "ATRAS\n", "PARAR\n")
            try:
                incoming = bt_sock.recv(1024)
                if incoming:
                    for cmd_line in incoming.decode("utf-8", errors="ignore").splitlines():
                        if cmd_line.strip():
                            VEHICLE_STATE.apply_command(cmd_line.strip())
            except socket.timeout:
                pass

            payload = VEHICLE_STATE.get_telemetry()
            line = (json.dumps(payload) + "\n").encode("utf-8")
            bt_sock.sendall(line)
            print(f"[ESP32-BT TX] {line.decode('utf-8').strip()}")
            time.sleep(interval)

    except KeyboardInterrupt:
        print("\n[!] Cerrando enlace Bluetooth RFCOMM...")
    except Exception as exc:
        print(f"[ERROR BT] {exc}")
    finally:
        if bt_sock is not None:
            try:
                bt_sock.close()
            except Exception:
                pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simulador de Vehículo Autónomo IoT ESP32 (HTTP REST & Bluetooth RFCOMM)"
    )
    parser.add_argument(
        "--mode",
        choices=["http", "bt-rfcomm"],
        default="http",
        help="Modo de simulación: 'http' (Servidor REST del ESP32) o 'bt-rfcomm'",
    )
    parser.add_argument(
        "--bind",
        default="0.0.0.0",
        help="IP de escucha para el servidor HTTP (por defecto: 0.0.0.0)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8080,
        help="Puerto HTTP (por defecto: 8080; use 80 con privilegios de administrador para emular 192.168.4.1:80)",
    )
    parser.add_argument(
        "--bt-mac",
        default="00:11:22:33:FF:EE",
        help="Dirección MAC Bluetooth del teléfono Android para modo bt-rfcomm",
    )
    parser.add_argument(
        "--channel",
        type=int,
        default=1,
        help="Canal RFCOMM Bluetooth (por defecto: 1)",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=0.7,
        help="Intervalo de envío de telemetría en modo Bluetooth (segundos)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    if args.mode == "http":
        run_http_simulator(args.bind, args.port)
    else:
        run_bluetooth_rfcomm_simulator(args.bt_mac, args.channel, args.interval)
