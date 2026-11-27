# Firmware Wi-Fi para Auto-IoT

Este firmware para la Keyestudio ESP32 Plus se puede cargar con **Arduino IDE** y el paquete **esp32 de Espressif Systems 3.3.x**, o con ESP-IDF 5.5. El ESP32 crea la red `Auto_IoT_Control` y expone las rutas HTTP que usa la versión actual de la aplicación Android. El perfil predeterminado controla un motor DC con un L298N.

## Cargar con Arduino IDE

1. En **Archivo → Abrir**, selecciona `esp32.ino` dentro de `firmware/esp32`. Abre el proyecto desde esa carpeta para que Arduino compile también los archivos de `src`.
2. Copia [`wifi_secrets.example.h`](wifi_secrets.example.h) como **`wifi_secrets.h`** junto al `.ino`. Escribe una clave propia de 8 a 63 caracteres ASCII imprimibles entre las comillas. Git excluye ese archivo.

   ```c
   #define AUTO_IOT_WIFI_PASSWORD ""
   ```

3. Selecciona **Herramientas → Placa → ESP32 Dev Module** y el puerto `COM` que aparece al enchufar la placa con un cable USB de datos. Si falta el paquete de placas, instálalo desde el gestor de placas de Arduino IDE. [Instalar el paquete ESP32](https://docs.espressif.com/projects/arduino-esp32/en/latest/installing.html)
4. En **Herramientas → Upload Speed**, selecciona **115200** para la primera carga. Pulsa **Verificar** y luego **Subir**. Abre el **Monitor serie a 115200**. Ahí aparecerá `Wi-Fi: Auto_IoT_Control` y la dirección de telemetría. Si abriste el monitor después del arranque, reinicia la placa con el botón `EN` o `RST`.

El perfil Arduino se configura en `opciones.h`. Para L298N, conserva `AUTO_IOT_USE_SERVOS` en `0`. Cambia esa opción a `1` para el montaje de dos servos, y `AUTO_IOT_USE_OLED` a `1` si conectaste la OLED.

## Conexiones

| Componente | Conexión al ESP32 |
| --- | --- |
| HC-SR04 | `TRIG` a `IO5` y `ECHO` a `IO18` por divisor de 1 kΩ y 2 kΩ. El sensor recibe 5 V y comparte GND. |
| L298N | `IN1` a `IO25`, `IN2` a `IO26` y `ENA` a `IO27`. El motor va a `OUT1` y `OUT2`. |
| Dos servos de tracción | Selecciona este perfil solo si el montaje usa esos actuadores. Las señales van a `IO12` e `IO14`. Calibra el pulso neutro antes de probarlos con las ruedas en el suelo. |
| OLED SSD1306 opcional | Habilítala en el menú Auto-IoT. `SDA` a `IO21` y `SCL` a `IO22`. |
| Luz opcional | LED con resistencia en `IO4`. Se alterna con `F` por serial. |

Con el montaje L298N, **retira el jumper `ENA` antes de conectar `IO27`** y pon la resistencia de 10 kΩ entre `ENA` y `GND`. Usa masa común y alimenta el motor con el portapilas de cuatro AA. Con la ESP32 por USB, quita `5V-EN` antes de llevar 5 V al borne de lógica. [Esquema del montaje](../../docs/esquema-esp32-es3.png)

## Cargar con ESP-IDF

1. Abre una terminal de ESP-IDF 5.5 y entra en la carpeta `firmware/esp32`.
2. Ejecuta `idf.py set-target esp32` para seleccionar el chip y luego `idf.py menuconfig`.
3. En el menú **Auto-IoT**, escribe una contraseña WPA2 propia de 8 a 63 caracteres ASCII imprimibles. Conserva **Un motor DC y L298N** para el circuito del video. Guarda y sal. Si omites la clave, puedes usar la consola serial y el punto de acceso queda deshabilitado. El archivo de configuración local queda excluido de Git.
4. Ejecuta `idf.py build`. Conecta la placa por USB y carga el resultado con `idf.py -p COM5 flash monitor`. Sustituye `COM5` por el puerto de la placa. El monitor muestra el nombre de la red y la dirección para consultar telemetría.

[Instalación de ESP-IDF 5.5 en Windows](https://docs.espressif.com/projects/esp-idf/en/v5.5/esp32/get-started/windows-setup.html)

## Conectar el teléfono

1. Conecta Android a `Auto_IoT_Control` con la clave configurada (soporta hasta 4 estaciones para evitar bloqueos por MAC aleatoria en Android). Mantén la conexión si Android indica que la red no tiene Internet y desactiva **DNS Privado** en Ajustes si el navegador bloquea direcciones locales.
2. Abre `http://192.168.4.1/telemetria` (o `http://192.168.4.1/`, asegurándote de usar `http://` sin `s`) en el navegador del teléfono. Comprueba que devuelve JSON y que la distancia cambia al recargar con un objeto frente al sensor.
3. Abre Auto-IoT, que aparece como **OT Shield Monitor**, y selecciona **Conectar Dispositivos → Wi-Fi Local (ESP32) → Vehículo ESP32 (192.168.4.1:80) → Conectar Ahora**.
4. Prueba **Adelante**, **Atrás** y **Parar** con la rueda levantada. Con un solo motor, los giros responden con error sin accionar el motor.

Para diagnosticar la conexión, busca estos mensajes en orden en el Monitor serie.

| Mensaje | Qué confirma |
| --- | --- |
| `Wi-Fi: telefono asociado` | El teléfono se unió al punto de acceso. |
| `Wi-Fi: DHCP asigno 192.168.4.x al telefono` | La placa entregó una dirección en su red local. |
| `HTTP GET /telemetria recibido` | La petición del navegador o de Auto-IoT llegó al ESP32. |

Si el teléfono se asocia y la petición no aparece, comprueba que Android mantiene seleccionado ese Wi-Fi. Puedes desactivar los datos móviles mientras haces la primera prueba desde el navegador.

El firmware mide cada 70 ms. Si está avanzando, corta la tracción a 15 cm o menos o al perder el eco. Al perder la conexión Wi-Fi o dejar de recibir peticiones válidas durante unos 3 segundos, se detiene el movimiento iniciado desde el teléfono. Volver a conectar recupera la lectura. Para moverlo se requiere una nueva orden.

Las rutas `GET /telemetria`, `/adelante`, `/atras`, `/parar`, `/izquierda` y `/derecha` devuelven una lectura y `resultado`. Sin eco válido, el JSON señala `distanciaValida:false` y el APK ES3 muestra **Sin eco**. Por serial, a 115200 baudios, siguen disponibles `W`, `S`, `A`, `D`, `X` y `F` según el montaje.

La app ES3 abre `GET /eventos` y recibe telemetría periódica por ese flujo. El latido `POST /latido` mantiene el control habilitado y confirma el historial tras persistirlo en SQLite. Si se conecta una versión anterior, `GET /telemetria` sigue disponible. [Protocolo, capacidad de flash y guion ES3](../../README.md)

[Circuito virtual y pruebas con el emulador Android](sim/README.md)
