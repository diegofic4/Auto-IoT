# Circuito virtual de Auto-IoT

El [diagrama](diagram.json) usa un ESP32 clásico, un HC-SR04 con distancia ajustable y el divisor de 1 kΩ y 2 kΩ que protege GPIO18. Los LED y el analizador permiten observar las señales que van al L298N. El visor representa el mando eléctrico del puente. La velocidad, la corriente, la batería y el movimiento de un motor quedan fuera del modelo. [Componentes soportados por Wokwi](https://docs.wokwi.com/getting-started/supported-hardware)

| Componente | Señales |
| --- | --- |
| HC-SR04 | `TRIG` en GPIO5, `ECHO` en GPIO18 mediante el divisor, VIN virtual y GND |
| L298N representado por señales | `IN1` en GPIO25 (avance), `IN2` en GPIO26 (retroceso), `ENA` en GPIO27 (habilitación) con resistencia de 10 kΩ a masa |
| Analizador lógico | `D0` TRIG, `D1` ECHO, `D2` ENA, `D3` IN1 y `D4` IN2 |

## Preparar y abrir el circuito

Con Arduino CLI y el núcleo ESP32 instalados, ejecuta desde `firmware/esp32/sim`.

```powershell
.\build-wokwi.ps1
```

Si `arduino-cli` no está en el PATH de Windows y usas la distribución local de Arduino IDE, indica su ruta.

```powershell
$cli = Join-Path $env:LOCALAPPDATA 'Programs/Arduino IDE/resources/app/lib/backend/resources/arduino-cli.exe'
.\build-wokwi.ps1 -ArduinoCli $cli
```

El script exporta `esp32.ino.merged.bin` y `esp32.ino.elf` a `firmware/esp32/build/wokwi`. Compila el perfil L298N con la clave vacía para que el circuito virtual pruebe el mismo control de tracción y sensor por UART. No copia la configuración Wi-Fi física.

Abre `firmware/esp32` como carpeta en VS Code. Si tienes la extensión oficial activa, ejecuta **Wokwi: Select Config File**, elige `sim/wokwi.toml` y ejecuta **Wokwi: Start Simulator**. Con Wokwi CLI y `WOKWI_CLI_TOKEN` configurado, ejecuta el escenario de prueba desde `firmware/esp32/sim`.

```powershell
wokwi-cli . --scenario frenado.scenario.yaml --timeout 10000
```

El [escenario](frenado.scenario.yaml) parte a 30 cm, manda avanzar, acerca el obstáculo a 10 cm, comprueba que `ENA` cae por sí solo y que el avance vuelve a rechazarse. Después prueba retroceso y parada manual. [Configuración de Wokwi](https://docs.wokwi.com/vscode/project-config) · [Escenarios y requisitos de Wokwi CLI](https://docs.wokwi.com/wokwi-ci/automation-scenarios)

## Pruebas con el emulador Android

El banco de pruebas ejecuta el código C de tracción y las rutas HTTP básicas del firmware con un HC-SR04, reloj, GPIO y enlace Wi-Fi simulados. La distancia y el freno se actualizan aunque nadie consulte `/telemetria`. El servidor de pruebas, disponible solo en localhost, ofrece `/eventos` y `/latido` para probar Android. La cola del servidor vive en RAM y sirve para ejercitar SQLite y la reconexión. El firmware de la placa usa NVS.

Con Python y Zig instalados, ejecuta estos comandos desde esta carpeta para correr la prueba automática y luego iniciar el servidor.

```powershell
python host/bench.py --cc zig
python host/bench.py --cc zig --serve --port 8090
```

En Auto-IoT dentro de Android Emulator, ingresa `10.0.2.2` como dirección y `8090` como puerto. Desde el computador, cambia la distancia del sensor y consulta las salidas del puente con estas peticiones al banco.

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8090/__bench/distance -ContentType application/json -Body '{"cm":15}'
Invoke-RestMethod -Uri http://127.0.0.1:8090/__bench/pins
```

La prueba automática verifica avance a 30 cm, corte de ENA a 15 cm sin una consulta nueva, rechazo de avance bloqueado y de parámetros incorrectos, reversa, parada, pérdida de eco, pérdida de enlace y recuperación sin arrancar solo.

Para repetir la desconexión, envía `{"connected":false}` por POST a `http://127.0.0.1:8090/__bench/station`. El banco corta la salida ENA y devuelve HTTP 503 a Auto-IoT. Envía `{"connected":true}` a la misma ruta para restaurar el enlace.

La versión de prueba de Auto-IoT se ejecutó en Android Emulator API 37. La pantalla recibió 45 cm, activó el motor virtual al pulsar **Adelante**, mostró **Freno activo** y **Detenido** al bajar a 15 cm, y dejó ENA en cero. **Parar** también cortó ENA. Al simular la pérdida del enlace mostró HTTP 503 y reintentó. Al restaurar el enlace recibió 57 cm y conservó el motor detenido sin reiniciar la app.

## Alcance de Wokwi y del banco

El circuito de Wokwi está preparado para probar las señales por UART. El modo Wi-Fi documentado de Wokwi conecta el ESP simulado como cliente a una red virtual. Para alcanzar un servidor en el ESP desde fuera de Wokwi se necesita el gateway privado y un puerto reenviado. La prueba con Android Emulator usa el banco local descrito arriba. La asociación, DHCP y el tráfico HTTP del punto de acceso físico se comprueban aparte con la placa y un teléfono. [Redes Wi-Fi de Wokwi](https://docs.wokwi.com/guides/esp32-wifi)
