# Estación de Monitoreo y Teleoperación OT — Vehículo de Inspección IoT (ESP32 y Celular ↔ Celular)

Aplicación móvil nativa Android desarrollada en **Kotlin** con **Jetpack Compose (Material 3)** y arquitectura **MVVM limpia**, diseñada como **Estación Comercial de Monitoreo y Teleoperación** de un Vehículo Autónomo / Móvil de Inspección IoT basado en **ESP32** (sensor ultrasónico **HC-SR04**, puente H **L298N** y **LED de advertencia**) o en modo **Celular a Celular (2 Teléfonos)**, cumpliendo con los estándares de ciberseguridad **ISO/IEC 27400**, tipografía de alta legibilidad y tolerancia a fallos.

---

## 1. Pantallazos Descriptivos de la Aplicación (Presentación Comercial)

Los siguientes pantallazos descriptivos están incluidos en la carpeta [`docs/screenshots/`](docs/screenshots/) del repositorio y destacan las ventajas clave del producto:

| 1. Acceso Cifrado, Multicuenta y Botón `[Tema ▼]` | 2. Conectar Celular ↔ Celular, ESP32 y Consentimiento |
| :---: | :---: |
| ![Acceso Cifrado y Botón de Tema](docs/screenshots/01_login_crear_cuenta.png) | ![Conectar Celular a Celular y Consentimiento ISO 27400](docs/screenshots/02_consentimiento_permisos.png) |
| **Seguridad ISO 27400 y Personalización:** Pestañas para **Iniciar Sesión** y **Crear Cuenta** con hashing `SHA-256 + Salt` en `EncryptedSharedPreferences`, e ícono/botón superior compacto **`[Tema ▼]`** que despliega 6 paletas de alto contraste. | **Pestaña 2 (`Conectar Dispositivos`):** Permite enlazar **un celular con otro celular** (por Wi-Fi, Hotspot o Bluetooth RFCOMM) o conectarse al vehículo **ESP32**, mostrando primero el diálogo de **Consentimiento Informado ISO 27400**. |

| 3. Pestaña `Control y Sensor` (Cruceta 5 Direcciones) | 4. Protección Anticolisión `⚠️ FRENO ACTIVO` (`<= 15 cm`) |
| :---: | :---: |
| ![Pestaña Control y Sensor en Vivo](docs/screenshots/03_dashboard_teleoperacion.png) | ![Freno de Emergencia Activo](docs/screenshots/04_freno_emergencia_activo.png) |
| **Pestaña 1 (`Control y Sensor`):** Interfaz despejada con lectura en vivo del sensor **HC-SR04** (`45 cm`) y **Cruceta / D-Pad de 5 Direcciones** (**`▲ ADELANTE`**, **`◄ IZQUIERDA`**, **`■ PARAR`**, **`DERECHA ►`**, **`▼ ATRÁS`**), adaptable a pantalla vertical y horizontal. | **Seguridad Física Automática:** Cuando `distancia <= 15 cm`, se despliega **`⚠️ FRENO ACTIVO`**, se bloquea **`ADELANTE`** para evitar choque frontal y se mantienen habilitados **`◄ IZQUIERDA`**, **`DERECHA ►`**, **`■ PARAR`** y **`▼ ATRÁS`** para esquivar o retroceder. |

---

## 2. Organización Limpia en 3 Pestañas, Rotación de Pantalla y Alta Legibilidad

Para ofrecer una experiencia comercial intuitiva, ordenada y sin saturar una sola pantalla, la aplicación está estructurada en **3 secciones independientes** accesibles desde la barra inferior (`NavigationBar`) y soporta **rotación completa del teléfono (Vertical ↔ Horizontal)** sin perder estado, conexión ni botones:

1. **Pestaña 1 — `Control y Sensor` (Cruceta de 5 Direcciones y Telemetría)**:
   - Dedicada exclusivamente a la conducción: tarjeta de distancia ultrasónica **HC-SR04** con números gigantes (`54 sp`), barra de proximidad con código de colores, alerta de emergencia (`<= 15 cm`) y **Cruceta / D-Pad de 5 Direcciones** (**`▲ ADELANTE`**, **`◄ IZQUIERDA`**, **`■ PARAR`**, **`DERECHA ►`**, **`▼ ATRÁS`**).
   - **Diseño Adaptativo al Rotar el Teléfono (Vertical y Horizontal)**: En orientación horizontal (`maxWidth >= 620.dp`), la tarjeta ubica el **Sensor HC-SR04 a la izquierda** y la **Cruceta D-Pad de 5 botones a la derecha** en 2 columnas paralelas con desplazamiento fluido, conservando todos los botones, diálogos y funciones accesibles.
2. **Pestaña 2 — `Conectar Dispositivos` (Celular ↔ Celular, ESP32 y PC)**:
   - Guía paso a paso para conectar **dos celulares entre sí** (Wi-Fi / Hotspot o Bluetooth), conectarse al **ESP32 real (`192.168.4.1`)** o probar en 1 toque con el simulador integrado, además del panel de **Consentimiento Informado ISO/IEC 27400**.
3. **Pestaña 3 — `Historial y Cuenta` (Perfil, Temas y Auditoría JSON)**:
   - Gestión de la cuenta activa, botón desplegable de paleta de colores, cambio de contraseña cifrada, cierre de sesión seguro y consola de tramas JSON recibidas de `/telemetria`.

### Botón / Ícono Desplegable de Paleta de Colores (`[Tema ▼]`)
En lugar de ocupar espacio en la pantalla con listas largas de colores, la cabecera superior incluye el botón compacto **`[Tema: <Nombre> ▼]`** con ícono de paleta y doble punto cromático. Al pulsarlo, se despliega una ventana modal (`PalettePickerDialog`) con **10 temas organizados en 2 categorías** (persistidos en `EncryptedSharedPreferences`):

1. **🌿 Simples y Descanso Visual (Suaves para la vista, cero fatiga ocular)**:
   - **Pizarra Suave** (Oscuro Mate — Estilo *Nord/Slate* en gris azulado mate sin brillo neón).
   - **Papel Cálido** (Claro Crema/Sepia — Fondo cálido tipo lectura para evitar deslumbramiento).
   - **Salvia Natural** (Oscuro Relajante — Tonos verde bosque mate y salvia suave).
   - **Brisa Marina** (Claro Minimalista — Blanco azulado limpio y azul corporativo).
   - **Luz de Día** (Claro Nítido — Para uso bajo luz solar directa).
2. **⚡ Alto Contraste Táctico / SCADA**:
   - **Cyber Cyan**, **Ámbar Táctico**, **Verde Matrix**, **Neón Violeta** y **Rojo Táctico**.

---

## 3. Conexión de Celular a Celular (2 Teléfonos por Wi-Fi, Hotspot o Bluetooth)

La aplicación permite conectar **un celular directamente con otro celular** sin necesidad de un ESP32 físico ni un computador externo. Un teléfono actúa como **Vehículo / Sensor Emisor (Celular A)** y el otro como **Mando de Control Remoto (Celular B)**:

### Opción A: Celular a Celular por Wi-Fi o Zona Wi-Fi (Hotspot)
1. Conecte ambos celulares a la misma red Wi-Fi, o active **Compartir Internet (Zona Wi-Fi / Hotspot)** en uno de los celulares y conecte el otro a esa red.
2. En el **Celular A (Vehículo / Emisor)**:
   - Abra la pestaña **`2. Conectar Dispositivos`** y seleccione el modo **Wi-Fi**.
   - La app detecta y muestra automáticamente la **IP de este celular** en pantalla (por ejemplo `192.168.1.45` o `192.168.43.1`).
   - Pulse el botón verde **"Convertir este Celular en Servidor (Puerto 8080)"**. El servidor HTTP se abrirá en todas las interfaces de red (`0.0.0.0:8080`).
3. En el **Celular B (Mando de Control)**:
   - En la pestaña **`2. Conectar Dispositivos`**, escriba la IP mostrada en el Celular A con el puerto `8080` (o toque el chip rápido **`Hotspot Celular (192.168.43.1:8080)`**) y pulse **"Conectar Ahora"**.
   - Al presionar **`ADELANTE`**, **`IZQUIERDA`**, **`PARAR`**, **`DERECHA`** o **`ATRÁS`** en el Celular B, el Celular A actualizará su dirección, motor y distancia en tiempo real.

### Opción B: Celular a Celular por Bluetooth Clásico (RFCOMM SPP)
1. Vincule ambos celulares desde los **Ajustes Bluetooth** de Android.
2. En el **Celular A (Vehículo / Emisor)**:
   - En la pestaña **`2. Conectar Dispositivos`**, elija **Bluetooth** y pulse **"Poner este Celular en Modo Espera (Servidor Bluetooth)"**.
   - El Celular A comenzará a emitir telemetría JSON cada `650 ms` y escuchará comandos `ADELANTE`, `IZQUIERDA`, `DERECHA`, `ATRAS` y `PARAR` por el canal RFCOMM (`UUID 00001101-0000-1000-8000-00805F9B34FB`).
3. En el **Celular B (Mando de Control)**:
   - Pulse **"Buscar Dispositivos Vinculados"** y toque el nombre del **Celular A** en la lista para iniciar la teleoperación Bluetooth entre ambos teléfonos.

---

## 4. Gestión de Cuentas y Seguridad Criptográfica (ISO/IEC 27400)

1. **Crear Cuenta Nueva (`registerAccount`)**:
   - Permite registrar **Nombre Completo**, **Nombre de Usuario**, **Rol Operativo**, **Contraseña** y **Confirmación**, con medidor de fortaleza en tiempo real.
   - Genera un **Salt aleatorio de 16 bytes (`SecureRandom`)** y guarda únicamente el hash `SHA-256(salt + password)` en `EncryptedSharedPreferences` (`AES256_GCM` respaldado por Android Keystore). **Cero credenciales en texto plano.**
2. **Iniciar Sesión y Cambio Rápido de Cuenta (`login`)**:
   - Chips interactivos para elegir cuentas registradas con un toque y acceso rápido con cuenta demo (`operador_ot` / `Iso27400#OT`).
3. **Cambio de Contraseña y Cierre de Sesión Seguro (`logoutOperator`)**:
   - Desde el botón **"Mi Cuenta"** o la pestaña **`3. Historial y Cuenta`**, el operador puede actualizar su contraseña o cerrar sesión (lo cual detiene el enlace activo y revoca la bandera cifrada de sesión).

---

## 5. Arquitectura MVVM y Protocolo de Comunicación

### 5.1 Estructura Modular por Paquetes
- `com.example.security`: `CryptoManager.kt` (`EncryptedSharedPreferences` + `MasterKey AES256_GCM` + `SHA-256` en tiempo constante) y `AuthRepository.kt` (multicuenta cifrada y persistencia de paleta).
- `com.example.model`: `TelemetryModels.kt` (`VehicleTelemetry`, `VehicleCommand` con las 5 direcciones `ADELANTE`, `IZQUIERDA`, `PARAR`, `DERECHA`, `ATRAS`, `ConnectionState` y `VehicleTelemetryParser` tolerante a JSON malformado).
- `com.example.connectivity`:
  - `Esp32WifiClient.kt`: Cliente HTTP REST (`OkHttpClient`) en `Dispatchers.IO` hacia `/adelante`, `/izquierda`, `/derecha`, `/atras`, `/parar` y `/telemetria`, más servidor HTTP integrado en `0.0.0.0:8080` con detección de IP LAN/Hotspot (`getDeviceLocalIpAddresses()`) para conexión Celular a Celular.
  - `BluetoothManager.kt`: Cliente y Servidor Bluetooth RFCOMM (`00001101-0000-1000-8000-00805F9B34FB`) en `Dispatchers.IO` con emisor bidireccional de telemetría JSON para conexión entre 2 celulares o contra ESP32.
- `com.example.ui`: `TelemetryViewModel.kt`, `VehicleDashboardScreen.kt` (organizado en 3 pestañas y adaptable a rotación vertical/horizontal), `PaletteSelector.kt` (botón e ícono desplegable), `LoginScreen.kt` y `PermissionDialog.kt`.

### 5.2 Protocolo REST y Bluetooth (`/telemetria`)
```json
{"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}
```
- **Comandos de Dirección Soportados**:
  - **HTTP REST**: `/adelante` (`AVANZANDO`), `/izquierda` (`GIRANDO_IZQUIERDA`), `/derecha` (`GIRANDO_DERECHA`), `/atras` (`RETROCEDIENDO`), `/parar` (`DETENIDO`).
  - **Bluetooth RFCOMM**: `ADELANTE\n`, `IZQUIERDA\n`, `DERECHA\n`, `ATRAS\n`, `PARAR\n`.
- **Regla de Seguridad Física (`<= 15 cm`)**: Cuando `distancia <= 15`, se activa el freno automático, se muestra el banner rojo **`⚠️ FRENO ACTIVO`** y el botón **`ADELANTE`** queda bloqueado en la interfaz y en el ViewModel, permitiendo girar (`IZQUIERDA` / `DERECHA`), detener (`PARAR`) o retroceder (`ATRÁS`) para despejar el obstáculo.

---

## 6. Compilación, Generación de APK y Ejecución del Simulador Python

1. **Generar APK en Android Studio**:
   - Abra el proyecto en Android Studio y seleccione **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   - El APK se generará en `app/build/outputs/apk/debug/app-debug.apk`.
2. **Simulador Externo en Python (`simulador_esp32.py`)**:
   ```bash
   python3 simulador_esp32.py --mode http --port 8080
   ```
   - Para Bluetooth RFCOMM desde PC: `python3 simulador_esp32.py --mode bt-server`.

---

## 7. Guía de Pruebas y Validación Técnica en Sala (7 a 10 minutos)

Esta guía detalla el paso a paso exacto para verificar los 5 puntos exigidos en la dinámica de revisión docente:

### 7.1 Inicio de Sesión y Persistencia Cifrada (Criterio A2 / ISO 27400)
1. **Verificación de Cero Texto Plano:**
   - En la pantalla de login, verificar en la tarjeta de auditoría que las credenciales no existen en texto plano, mostrando el `Salt (Base64)` de 16 bytes y el `Hash SHA-256`.
   - Los datos residen en `EncryptedSharedPreferences` respaldados por Android Keystore (`AES256_GCM`).
2. **Persistencia de Sesión:**
   - Iniciar sesión con la cuenta demo `@operador_ot`.
   - Cerrar forzosamente la aplicación desde las apps recientes de Android y volver a abrirla.
   - **Resultado:** La sesión se restaura automáticamente ingresando directo al panel de teleoperación.

### 7.2 Permisos en Runtime y Consentimiento Informado (Criterio A3 / ISO 27400)
1. **Pop-up de Justificación Previa:**
   - Al pulsar conectar en la Pestaña 2, se despliega el diálogo informativo en Compose explicando el uso técnico de Bluetooth/Wi-Fi antes de llamar al sistema operativo.
2. **Prueba de Denegación (Tolerancia a Fallos):**
   - Presionar "Continuar" y luego pulsar **Denegar** en el permiso de Android.
   - **Resultado:** Cero crashes (sin cierres inesperados). La app permanece operativa, informa la restricción y habilita el botón hacia Ajustes.
3. **Prueba de Concesión:**
   - Otorgar los permisos para desbloquear los selectores de red.

### 7.3 Demostración de Comunicación en Vivo (Criterio A4)
1. **Modo Celular ↔ Celular o ESP32:**
   - Iniciar el servidor en el Celular A (o encender el ESP32 / script Python).
   - Conectar el Celular B ingresando la IP y puerto (o enlace Bluetooth RFCOMM).
   - **Resultado:** El estado cambia a **"Conectado"** y la distancia ultrasónica fluctúa en tiempo real en la pantalla.
2. **Teleoperación de 5 Direcciones:**
   - Presionar `▲ ADELANTE`, `◄ IZQUIERDA`, `■ PARAR`, `DERECHA ►` y `▼ ATRÁS`, verificando la recepción inmediata de comandos.

### 7.4 Prueba de Regla de Seguridad OT (Frenado Autónomo)
1. Colocar un obstáculo frente al sensor a una distancia **menor o igual a 15 cm**.
2. **Resultado:** Se activa el banner rojo **`⚠️ FRENO ACTIVO`** y el botón **`ADELANTE`** se bloquea automáticamente en la UI para impedir choques frontales, manteniendo activos el giro, retroceso y parada.

### 7.5 Pruebas de Robustez (Criterio A5)
1. **Rotación de Pantalla:**
   - Con telemetría continua en vivo, rotar el teléfono de vertical a horizontal.
   - **Resultado:** La interfaz se reorganiza en 2 columnas paralelas sin desconectarse ni reiniciar valores a cero (gestión en `ViewModelScope`).
2. **Desconexión Forzada:**
   - Con la telemetría activa, apagar bruscamente el Hotspot/Wi-Fi del emisor o detener el servidor.
   - **Resultado:** La aplicación no se congela ni crashea; captura las excepciones de red en `Dispatchers.IO`, cierra los sockets de forma segura y actualiza el estado visual a **"Desconectado / Error de enlace"**.

---

## 8. Descarga Directa del Instalador (.apk)

- **Versión Evaluada:** [Descargar APK v1.0.0 (ot-shield-monitor.apk)](https://github.com/diegofic4/Auto-IoT/releases/download/v1.0.0/ot-shield-monitor.apk)

