# Auto-IoT · Evaluación ES3 TI3042

## 1. Datos del proyecto

| Dato | Descripción |
| --- | --- |
| Integrantes | Diego Astudillo · Anibal San Martin · Benjamin Inostroz |
| Sección | Por confirmar |
| Problema | El operador necesita ver la distancia a un obstáculo y evitar que el prototipo siga avanzando cuando se acerca demasiado. |
| Sistema | La ESP32 mide distancia, controla un motor DC y corta el avance a 15 cm o menos o si se pierde el eco. Auto IoT envía órdenes y muestra distancia, estado, freno e historial. |
| Placa | Keyestudio ESP32 Plus con ESP32-WROOM-32. |
| Sensor | HC-SR04. |
| Actuadores | Motor DC de dos terminales en el canal A de un L298N. El firmware incluye una salida de luz opcional. |
| Enlace | Wi-Fi local WPA2 en punto de acceso de la ESP32. |
| Responsabilidades | Distribución individual por confirmar con el equipo. |

## 2. Esquema de conexiones

[Esquema del prototipo](docs/esquema-esp32-es3.png)

| Desde | Hacia | Detalle |
| --- | --- | --- |
| ESP32 `IO5` | HC-SR04 `TRIG` | Señal directa. |
| HC-SR04 `ECHO` | ESP32 `IO18` | 1 kΩ en serie hasta el nodo de `IO18` y 2 kΩ desde ese nodo a GND. Convierte un pulso de 5 V en aproximadamente 3,3 V. |
| ESP32 `5V` y `GND` | HC-SR04 `VCC` y `GND` | La ESP32 se alimenta por USB durante la demostración. |
| ESP32 `IO25`, `IO26`, `IO27` | L298N `IN1`, `IN2`, `ENA` | Retirar el jumper `ENA` y poner 10 kΩ entre `ENA` y GND. |
| L298N `OUT1` y `OUT2` | Dos terminales del motor DC | Si el sentido queda invertido, intercambiar estos cables con la alimentación desconectada. |
| Portapilas +, cuatro AA de 1,5 V en serie | L298N `VS`/`+12V` | 6 V nominales para la tracción. Comprobar el voltaje admisible del motor. |
| Portapilas −, GND de ESP32 y sensor | L298N `GND` | Masa común. |
| ESP32 `5V` | L298N `+5V` | Alimentación de lógica desde el USB. Retirar primero el jumper del regulador `5V-EN`. |
| ESP32 `IO4` | LED opcional | Usar resistencia en serie y conectar a GND. |

El cableado de alimentación de la tabla usa la lógica del L298N alimentada por USB. Con cuatro AA, el regulador integrado del módulo puede quedar por debajo de su tensión de trabajo. [Ficha del regulador 78M05](https://www.st.com/resource/en/datasheet/cd00000447.pdf)

## 3. Protocolo de comunicación

La ESP32 sirve HTTP local en `192.168.4.1:80`. Las órdenes de tracción son rutas sin cuerpo ni parámetros. Las respuestas son JSON y el flujo de lecturas usa JSON UTF-8 con un objeto por línea terminada en LF (NDJSON). La app lee líneas de hasta 335 bytes en `/eventos`. Una línea incompleta o inválida se descarta y provoca reconexión. Las tramas incompletas en el canal de órdenes se rechazan antes de accionar.

| Dirección | Método y ruta | Valores | Respuesta |
| --- | --- | --- | --- |
| App → ESP32 | `GET /adelante`, `/atras`, `/parar` | Sin parámetros | `200 ACK` y estado del motor. Avance inseguro → `409 ERR FRENO_ACTIVO`. |
| App → ESP32 | `GET /izquierda`, `/derecha` | Sin parámetros | `422 ERR COMANDO_NO_DISPONIBLE` en el perfil de un motor. |
| App → ESP32 | `GET /flash` | Sin parámetros | Alterna la luz opcional. |
| App → ESP32 | `POST /luz?valor=0` o `valor=1` | Un valor entero literal, sin cuerpo | `200 ACK`, `400 ERR TRAMA_INVALIDA` o `422 ERR VALOR_FUERA_DE_RANGO`. |
| Navegador → ESP32 | `GET /telemetria` | Sin parámetros | Lectura instantánea con distancia en cm, validez, motor, freno y motivo. |
| ESP32 → app | `GET /eventos` | Una suscripción HTTP, un cliente a la vez | Cola recuperada y después una lectura cada 650 ms, sin una petición por dato. |
| App → ESP32 | `POST /latido` con `ack` opcional | Sin cuerpo. `ack` admite 16 dígitos hexadecimales, un guion y otros 16. | Confirma el enlace y, cuando hay ID, libera las lecturas ya guardadas en SQLite. |
| Cliente → ESP32 | Ruta inexistente | Por ejemplo, `GET /inexistente` | `404 ERR COMANDO_DESCONOCIDO`. |

`/eventos` incluye `idLectura`, `capturaMs` desde el arranque, `edadMs`, `historial`, `distancia`, `distanciaValida`, `estadoMotor`, `frenoActivo` y `motivoFreno`. La distancia física válida del HC-SR04 va de 2 a 400 cm. Ante falta de eco, el firmware indica `distanciaValida:false` y la app muestra **Sin eco**. La app fecha las lecturas recibidas y las respuestas ACK/ERR. Para archivos del arranque actual también estima la hora de captura. Para arranques anteriores el reloj del ESP32 no permite conocer la hora real de captura y se muestra la hora de recepción.

## 4. Seguridad

| Control | Aplicación | Evidencia y prueba |
| --- | --- | --- |
| Acceso Wi-Fi por WPA2-PSK con CCMP y clave propia | [network.c](firmware/esp32/src/network.c) exige entre 8 y 63 caracteres ASCII imprimibles y desactiva el punto de acceso si falta una clave válida. | En el banco se observó que un firmware sin clave desactiva el Wi-Fi. La prueba con una clave incorrecta se hace desde Ajustes de Android, antes de unir el teléfono con la clave del equipo. |
| Validación previa a la actuación y parada ante pérdida de enlace | [network.c](firmware/esp32/src/network.c), [es3.c](firmware/esp32/src/es3.c) y [vehicle.c](firmware/esp32/src/vehicle.c). El firmware rechaza rutas y parámetros inválidos. El avance exige un eco válido y distancia segura. | El banco C verifica rechazo de parámetros, corte del avance al acercar un obstáculo y pérdida de enlace. El guion de demostración incluye la ruta desconocida y el valor fuera de rango en la placa. |
| Cuentas locales sin usuario de demostración preconfigurado | [AuthRepository.kt](app/src/main/java/com/example/security/AuthRepository.kt) retira la cuenta demo de instalaciones anteriores. Verificadores SHA-256 con sal aleatoria individual y preferencias cifradas con Android Keystore donde está disponible. | Al abrir el APK en una instalación nueva, crear una cuenta y probar una contraseña errónea. No se muestran verificadores ni sales en la pantalla de auditoría. |

La contraseña Wi-Fi va en `firmware/esp32/wifi_secrets.h`, excluido de Git. Se entrega [una plantilla vacía](firmware/esp32/wifi_secrets.example.h). El APK no necesita esa contraseña porque Android gestiona la conexión Wi-Fi. No se distribuye una imagen de firmware grabada con la contraseña del equipo.

Estas medidas se alinean con control de acceso, reducción de superficie y protección de credenciales de la familia ISO/IEC 27000. El enlace de aplicación es HTTP dentro del punto de acceso. Un participante que ya tenga acceso a esa red podría consultar las lecturas y accionar las rutas. La cuenta de Android controla la interfaz local y el firmware confía en la red Wi-Fi.

## 5. Datos personales

| Dato | Viaja al ESP32 | Retención y protección |
| --- | --- | --- |
| Nombre, usuario y rol del operador | No | Perfil local en preferencias de la app. Evitar datos personales reales en pruebas de clase. |
| Contraseña del operador | No | Se guarda un verificador con sal, no la contraseña original. Android Keystore respalda las preferencias cifradas en dispositivos compatibles. |
| Distancia, hora, estado del motor y freno | Sí | Flash de la ESP32 para lecturas sin enlace y SQLite privado de Android. Los datos no incluyen un identificador de operador y no se suben a la nube. |
| Imagen, GPS o audio | No | El perfil ES3 no recoge ni almacena estos datos. Android puede solicitar permisos de descubrimiento de Wi-Fi o Bluetooth según la versión. |

## 6. Reconexión y plan de contingencia

Android reintenta después de 1, 2, 4 y hasta 8 s. Al recibir una lectura válida vuelve a 1 s. Se indica error y reintento en la pantalla de conexión y se conserva la hora de la última lectura. Android debe permitir que el teléfono permanezca en el Wi-Fi local sin Internet. El vehículo vuelve a moverse solo después de una nueva orden explícita.

| Falla | Acción y recuperación |
| --- | --- |
| Teléfono pierde el Wi-Fi o se cierra la app | El firmware pone `ENA` en bajo cuando se va la última estación o al pasar aproximadamente 3 s sin actividad de control. Al volver, Android reabre el flujo y sincroniza la flash. |
| ESP32 se reinicia o apaga | El puente se deshabilita durante el arranque con la resistencia de 10 kΩ. La tracción queda detenida al iniciar. Se reabre el AP y se recupera la cola de la NVS. |
| Sensor sin eco o a 15 cm o menos | Se bloquea el avance y se detiene el motor si venía avanzando. Tras una lectura segura, el usuario puede volver a dar la orden. |
| Pilas o cableado de potencia fallan | Mostrar monitoreo sin mover el motor. Usar `Parar`, retirar el portapilas y verificar tensión, terminales y masa común antes de rearmar. |
| Incompatibilidad con firmware anterior | Si `/eventos` devuelve 404, la app usa `/telemetria`. Para probar sincronización, cargar el firmware ES3. |

## 7. Almacenamiento temporal sin conexión

Android guarda lecturas del canal nuevo en **SQLite**. Durante la conexión conserva una muestra de cada 5 s y persiste todas las lecturas recuperadas. Cada una tiene una clave única de endpoint e ID de lectura. La app confirma el último ID de una serie recuperada solo después de insertar todas las filas anteriores. Si el ACK se pierde, el ESP32 reenvía la serie y SQLite ignora los IDs ya guardados. La app conserva el historial tras un reinicio y permite mostrar lecturas recuperadas aunque se desconecte. Los comandos de motor caducados quedan fuera de la sincronización para proteger el arranque.

| Dato | ESP32 |
| --- | --- |
| Mecanismo | NVS, clave `lecturas_v1` en el espacio `auto_iot_es3`. |
| Tamaño | 24 bytes por lectura en el objetivo ESP32. El bloque para 32 lecturas ocupa aproximadamente 0,8 KiB, más metadatos de NVS. |
| Frecuencia | Una captura cada 15 s cuando no llega un latido de la app. El ciclo de control del sensor sigue a 70 ms. |
| Capacidad | 32 lecturas, aproximadamente 8 min sin enlace y con la placa encendida. |
| Cola llena | Conserva las 32 lecturas pendientes hasta confirmarlas. Las nuevas muestras offline se omiten mientras está llena. |
| Desgaste | Una actualización NVS por muestra offline, ninguna por evento en vivo y una por lote confirmado. NVS administra las escrituras en flash. |
| Vaciado | En el reencuentro `/eventos` entrega primero la cola. `POST /latido?ack=<id>` retira el prefijo confirmado por la app. |

## 8. Consumo de energía

El prototipo usa USB para la ESP32, el sensor y la lógica del puente. El portapilas de **cuatro AA alcalinas en serie** da 6 V nominales a la etapa del motor. En serie se suma voltaje y se mantiene la capacidad en mAh de una celda. Los porcentajes son supuestos de cálculo para una demostración, no mediciones.

| Componente y estado | Corriente de referencia | Uso estimado | Promedio |
| --- | ---: | ---: | ---: |
| ESP32 radio Wi-Fi TX, 3,3 V | 240 mA | 10 % | 24 mA |
| ESP32 radio Wi-Fi RX, 3,3 V | 100 mA | 90 % | 90 mA |
| HC-SR04, 5 V | 15 mA | 100 % | 15 mA |
| L298N lógica, 5 V | 36 mA | 100 % | 36 mA |
| LED de módulo, 5 V | 2 mA (supuesto de cálculo) | 100 % | 2 mA |
| L298N etapa de potencia habilitada, 6 V, consumo propio | 70 mA | 25 % | 17,5 mA |
| L298N etapa de potencia deshabilitada, 6 V | 4 mA | 75 % | 3 mA |
| Motor pequeño de referencia en marcha | 560 mA | 25 % | 140 mA |
| Motor detenido | 0 mA | 75 % | 0 mA |

Fuentes de la tabla. [ESP32, consumo RF](https://documentation.espressif.com/esp32_datasheet_en.html), [HC-SR04, ElecFreaks](https://www.digikey.co.uk/htmldatasheets/production/3822706/0/0/1/hc-sr04.pdf), [L298, características eléctricas](https://www.st.com/resource/en/datasheet/l298.pdf), [L298N, corriente de lógica](https://docs.sunfounder.com/projects/sf-components/en/latest/component_l298n_module.html), [motor DC de referencia, Mabuchi FA-130RA-18100](https://product.mabuchi-motor.com/detail.html?id=10), [curvas Duracell MN1500](https://media.digikey.com/pdf/Data%20Sheets/Duracell/MN1500.pdf).

La etapa de 6 V suma **160,5 mA**. En USB se estiman **0,64 W** de carga de componentes antes de pérdidas y consumo de la placa base, calculados como 3,3 V × 114 mA + 5 V × 53 mA. El conjunto suma aproximadamente **1,60 W** con los supuestos de la tabla. Con **1.200 mAh útiles de referencia** por celda, un valor supuesto dependiente de carga y tensión de corte, la autonomía de la rama AA es **1.200 × 0,8 ÷ 160,5 ≈ 6,0 h**. El motor instalado está sin referencia confirmada, por lo que la cifra sirve como estimación de orden de magnitud y no incluye picos de arranque o atasco.

El puente se deshabilita entre órdenes y el motor se detiene ante un fallo. El teléfono recibe tramas en un solo flujo, guarda una parte de las lecturas en vivo y reintenta con espera creciente. La pantalla y el Wi-Fi continuo siguen consumiendo energía. La ESP32 permanece despierta para responder al control y al sensor. La autonomía de la ESP32 no aplica al llevar alimentación USB.

## 9. Compatibilidad

| Dato | Valor |
| --- | --- |
| `minSdk` | 26, Android 8.0. |
| `targetSdk` | 36, Android 16. |
| `compileSdk` | 36.1. |
| ABIs | APK universal verificado con `aapt2`: `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`. |

| Dispositivo | Android | Resultado |
| --- | --- | --- |
| Teléfono del equipo | Modelo y versión por completar | El equipo confirmó funcionamiento físico de sensor, app y motor antes de la actualización ES3. |
| Android Emulator x86_64 | API 37 | APK 1.1 instalado. Arrancó sin cuentas preconfiguradas y permitió crear una cuenta local. Con el banco virtual, mostró lecturas con hora y recuperó dos lecturas en SQLite con IDs distintos después de simular una caída. |

## 10. Cómo compilar y ejecutar

- **Firmware.** Abrir [esp32.ino](firmware/esp32/esp32.ino) dentro de su carpeta en Arduino IDE 2.3.11 con **esp32 de Espressif Systems 3.3.12** y la placa **ESP32 Dev Module**. Copiar la plantilla a `wifi_secrets.h`, escribir la clave propia y conservar `AUTO_IOT_USE_SERVOS 0` en `opciones.h`. Cargar a 115200 por USB. [Instrucciones de firmware](firmware/esp32/README.md)
- **Android.** Abrir la raíz en Android Studio con JDK integrado, Gradle 9.3.1 y SDK de compilación 36.1. Ejecutar `gradle :app:assembleDebug` o **Build → Build APK(s)**. El resultado se genera en `app/build/outputs/apk/debug/app-debug.apk`. El APK de la evaluación usa firma de depuración. [Descarga de Auto-IoT 1.1](https://github.com/diegofic4/Auto-IoT/releases/tag/es3-v1.1)
- **Arranque.** Asegurar el auto con las ruedas libres, alimentar la ESP32 por USB, conectar las pilas del motor y unir el teléfono a `Auto_IoT_Control`. Crear una cuenta local, elegir **Wi-Fi Local (ESP32)** y conectar a `192.168.4.1:80`.

Para compilar por primera vez, generar `debug.keystore` en la raíz con el `keytool` del JDK. Los parámetros siguientes son los del perfil local de depuración de Android. El almacén queda excluido de Git.

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

### Demostración de 9 minutos

| Tiempo | Prueba |
| --- | --- |
| 0–2 min | Mostrar ESP32, sensor, puente, pilas y dos fuentes de alimentación. Mostrar la distancia y la hora de la lectura. |
| 2–5 min | Avanzar con un objeto a 30 cm. Acercarlo hasta 15 cm, mostrar corte del motor, retirar objeto, volver a dar orden y pulsar Parar. Mostrar ACK/ERR con hora en Eventos. |
| 5–7 min | Intentar asociarse con una clave Wi-Fi errónea y luego conectar con la propia. En un computador conectado al AP probar `curl.exe -i http://192.168.4.1/inexistente` y `curl.exe -i -X POST "http://192.168.4.1/luz?valor=9"`. Deben devolver 404 y 422 sin movimiento. |
| 7–9 min | Apagar la ESP32 y mostrar aviso y reintentos. Encenderla y comprobar recuperación sin reiniciar la app. Para mostrar lecturas guardadas, desconectar solo el Wi-Fi del teléfono durante 20–30 s con la ESP32 encendida. Volver a unirlo y abrir **Historial → Recuperadas**. |

## 11. Qué no funcionó y qué mejoraríamos

- El primer HC-SR04 no entregaba eco. Se reemplazó y el equipo confirmó que la lectura volvió a funcionar. Esta versión diferencia el fallo de eco de un obstáculo cerca.
- El LED del L298N se apagó después de un cambio de pilas durante el armado. El equipo confirmó después que el montaje volvió a funcionar con cuatro AA.
- El prototipo utiliza un motor y un L298N. El giro independiente, el servo y la cámara quedan fuera del montaje evaluado.
- Las lecturas se recuperan dentro de la capacidad de NVS y mientras la ESP32 recibe alimentación para capturarlas. Los datos ya confirmados quedan en Android.
- El protocolo podría incorporar autenticación por dispositivo y cifrado de aplicación. El perfil de prueba conserva el modo de preferencias privadas de Android si Android Keystore no está disponible.
- El firmware ES3 compiló con Arduino ESP32 3.3.12. El banco C verificó el corte de ENA a 15 cm y la parada por pérdida de enlace. La recuperación del APK se probó con el historial simulado del banco. Queda por repetir la sincronización con NVS real y comprobar la versión 1.1 en el teléfono del equipo.
