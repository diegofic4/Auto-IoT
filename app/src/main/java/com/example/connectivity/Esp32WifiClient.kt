package com.example.connectivity

import com.example.model.ConnectionState
import com.example.model.TransportMode
import com.example.model.VehicleCommand
import com.example.model.VehicleTelemetry
import com.example.model.VehicleTelemetryParser
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cliente Wi-Fi HTTP/Sockets para comunicación con el Vehículo Autónomo / Móvil de Inspección IoT (ESP32).
 *
 * Características técnicas (ISO/IEC 27400 y Tolerancia a Fallos):
 * - Punto de acceso predeterminado del ESP32: SSID `"Auto_IoT_Control"`, IP `192.168.4.1` (Puerto 80 u 8080).
 * - Endpoints REST soportados:
 *   * `GET /telemetria` -> `{"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}`
 *   * `GET /adelante`   -> Acciona Puente H L298N hacia adelante (bloqueado si distancia <= 15 cm).
 *   * `GET /atras`      -> Acciona Puente H L298N en reversa.
 *   * `GET /parar`      -> Detiene motores inmediatamente.
 * - Ejecuta el 100% de las operaciones de red en [Dispatchers.IO] con timeouts estrictos para detectar
 *   apagado del ESP32 o pérdida de cobertura Wi-Fi sin bloquear el hilo principal ni causar cierres inesperados.
 * - Incluye además un **Simulador HTTP ESP32 Local en `127.0.0.1:8080`** basado en `ServerSocket` para
 *   permitir validar toda la pila HTTP/REST directamente desde el emulador Android.
 */
class Esp32WifiClient(
    private val externalScope: CoroutineScope,
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onTelemetryReceived: (VehicleTelemetry) -> Unit,
    private val onSystemLog: (String) -> Unit
) {

    companion object {
        const val DEFAULT_ESP32_AP_SSID = "Auto_IoT_Control"
        const val DEFAULT_ESP32_IP = "192.168.4.1"
        const val DEFAULT_ESP32_PORT = 80
        const val SIMULATOR_LOCAL_IP = "127.0.0.1"
        const val SIMULATOR_LOCAL_PORT = 8080
        private const val POLLING_INTERVAL_MS = 650L
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2200, TimeUnit.MILLISECONDS)
        .readTimeout(2200, TimeUnit.MILLISECONDS)
        .writeTimeout(2200, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private var telemetryJob: Job? = null
    private var localEmbeddedEsp32Job: Job? = null

    @Volatile
    private var localEmbeddedServerSocket: ServerSocket? = null

    @Volatile
    private var currentHost: String = DEFAULT_ESP32_IP

    @Volatile
    private var currentPort: Int = DEFAULT_ESP32_PORT

    @Volatile
    private var isConnectedFlag: Boolean = false

    private val sequenceCounter = AtomicLong(0L)

    // Estado interno del Simulador HTTP ESP32 embebido (127.0.0.1:8080) para pruebas de laboratorio
    private val simDistanceCm = AtomicInteger(68)
    private val simMotorState = AtomicReference(VehicleTelemetry.MOTOR_DETENIDO)

    /**
     * Inicia el enlace Wi-Fi HTTP hacia el ESP32 (`192.168.4.1` o IP del `simulador_esp32.py`)
     * y mantiene el sondeo continuo de `/telemetria` en `Dispatchers.IO`.
     */
    fun connectAndPollTelemetry(host: String, port: Int) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) {
            onStateChanged(ConnectionState.Error("Ingrese una dirección IP válida para el nodo ESP32."))
            return
        }

        telemetryJob?.cancel()
        currentHost = cleanHost
        currentPort = port

        telemetryJob = externalScope.launch(Dispatchers.IO) {
            val baseUrl = buildBaseUrl(cleanHost, port)
            onStateChanged(ConnectionState.Connecting("$cleanHost:$port"))
            onSystemLog("Conectando vía Wi-Fi HTTP al nodo ESP32 en $baseUrl/telemetria...")

            var consecutiveFailures = 0

            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/telemetria")
                        .get()
                        .header("Accept", "application/json")
                        .header("Connection", "close")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code} desde $baseUrl/telemetria")
                        }
                        val bodyString = response.body?.string().orEmpty()
                        val seq = sequenceCounter.incrementAndGet()

                        VehicleTelemetryParser.parseJson(bodyString, "Wi-Fi HTTP ($cleanHost)", seq)
                            .onSuccess { telemetry ->
                                if (!isConnectedFlag || consecutiveFailures > 0) {
                                    isConnectedFlag = true
                                    consecutiveFailures = 0
                                    onStateChanged(
                                        ConnectionState.Connected(
                                            endpointLabel = "$cleanHost:$port",
                                            transport = TransportMode.WIFI_ESP32
                                        )
                                    )
                                    onSystemLog("Enlace Wi-Fi HTTP establecido con ESP32 ($cleanHost:$port)")
                                }
                                onTelemetryReceived(telemetry)
                            }
                            .onFailure { parseErr ->
                                onSystemLog("Trama JSON descartada (#$seq): ${parseErr.message}")
                            }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (ste: SocketTimeoutException) {
                    consecutiveFailures++
                    handleNetworkFailure(
                        cleanHost,
                        port,
                        consecutiveFailures,
                        "Timeout HTTP (${ste.localizedMessage ?: "El ESP32 no respondió a tiempo"})"
                    )
                } catch (se: SocketException) {
                    consecutiveFailures++
                    handleNetworkFailure(
                        cleanHost,
                        port,
                        consecutiveFailures,
                        "Socket interrumpido (${se.localizedMessage ?: "Conexión rechazada o pérdida de AP Wi-Fi"})"
                    )
                } catch (ioe: IOException) {
                    consecutiveFailures++
                    handleNetworkFailure(
                        cleanHost,
                        port,
                        consecutiveFailures,
                        "Error I/O Wi-Fi (${ioe.localizedMessage ?: "Host inalcanzable"})"
                    )
                } catch (e: Exception) {
                    consecutiveFailures++
                    handleNetworkFailure(
                        cleanHost,
                        port,
                        consecutiveFailures,
                        "Excepción de red (${e.localizedMessage ?: e.javaClass.simpleName})"
                    )
                }

                // Si acumulamos 2 fallos consecutivos (pérdida de cobertura o apagado del ESP32),
                // cerramos limpiamente el ciclo y pasamos a estado Desconectado sin crashear.
                if (consecutiveFailures >= 2) {
                    isConnectedFlag = false
                    onSystemLog(
                        "Desconexión forzada detectada ($cleanHost:$port sin respuesta). Cerrando enlace de forma segura."
                    )
                    onStateChanged(ConnectionState.Disconnected)
                    break
                }

                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /**
     * Envía un comando de teleoperación (`/adelante`, `/atras`, `/parar`) al servidor REST del ESP32
     * ejecutándose estrictamente en `Dispatchers.IO`.
     */
    fun sendVehicleCommand(command: VehicleCommand, currentDistanceCm: Int) {
        if (command == VehicleCommand.ADELANTE && currentDistanceCm <= VehicleTelemetry.CRITICAL_DISTANCE_CM) {
            onSystemLog(
                "BLOQUEO DE SEGURIDAD OT: Comando /adelante rechazado porque distancia ($currentDistanceCm cm) <= 15 cm (Freno Activo)."
            )
            return
        }

        externalScope.launch(Dispatchers.IO) {
            val baseUrl = buildBaseUrl(currentHost, currentPort)
            val endpointUrl = "$baseUrl${command.endpointPath}"
            try {
                val request = Request.Builder()
                    .url(endpointUrl)
                    .get()
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Connection", "close")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty().trim()
                    if (response.isSuccessful) {
                        onSystemLog("Comando enviado -> GET ${command.endpointPath} (HTTP ${response.code})")
                        // Si el endpoint devuelve directamente el JSON actualizado de telemetría, lo procesamos de inmediato
                        if (bodyText.startsWith("{") && bodyText.endsWith("}")) {
                            val seq = sequenceCounter.incrementAndGet()
                            VehicleTelemetryParser.parseJson(
                                bodyText,
                                "Wi-Fi CMD (${command.endpointPath})",
                                seq
                            ).onSuccess { telemetry ->
                                onTelemetryReceived(telemetry)
                            }
                        }
                    } else {
                        onSystemLog("Advertencia HTTP ${response.code} en comando ${command.endpointPath}: $bodyText")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                isConnectedFlag = false
                onSystemLog("Fallo al enviar ${command.endpointPath}: ${e.localizedMessage ?: "ESP32 desconectado"}")
                onStateChanged(ConnectionState.Disconnected)
            }
        }
    }

    /**
     * Obtiene las direcciones IPv4 locales activas del teléfono (Wi-Fi LAN o Zona Wi-Fi / Hotspot)
     * para que otro celular pueda conectarse directamente a este teléfono en el puerto 8080.
     */
    fun getDeviceLocalIpAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("169.254.")) {
                            result.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result.distinct()
    }

    /**
     * Levanta un Servidor HTTP en `0.0.0.0:8080` (accesible tanto localmente en `127.0.0.1:8080`
     * como desde **otro celular** conectado a la misma red Wi-Fi o Zona Portátil / Hotspot).
     */
    fun startEmbeddedEsp32SimulatorAndConnect() {
        stopEmbeddedEsp32Simulator()
        simDistanceCm.set(55)
        simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)

        localEmbeddedEsp32Job = externalScope.launch(Dispatchers.IO) {
            var server: ServerSocket? = null
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    // Escuchar en 0.0.0.0:8080 permite conexiones tanto del propio teléfono como de OTRO CELULAR en la red
                    bind(InetSocketAddress("0.0.0.0", SIMULATOR_LOCAL_PORT))
                }
                localEmbeddedServerSocket = server
                val localIps = getDeviceLocalIpAddresses()
                val ipSummary = if (localIps.isNotEmpty()) localIps.joinToString(", ") else SIMULATOR_LOCAL_IP
                onSystemLog(
                    "Modo Emisor / Servidor activo en puerto $SIMULATOR_LOCAL_PORT (IPs para otro celular: $ipSummary)"
                )

                // Iniciamos el cliente HTTP local para visualizar también en pantalla la telemetría en vivo
                connectAndPollTelemetry(SIMULATOR_LOCAL_IP, SIMULATOR_LOCAL_PORT)

                while (isActive && !server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (se: SocketException) {
                        break
                    }
                    handleEmbeddedHttpClient(client)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                onSystemLog("Servidor local finalizado: ${e.localizedMessage}")
            } finally {
                safeCloseServerSocket(server)
            }
        }
    }

    private fun handleEmbeddedHttpClient(socket: Socket) {
        try {
            socket.soTimeout = 1500
            val remoteIp = socket.inetAddress?.hostAddress ?: "remoto"
            val isExternalPhone = remoteIp != "127.0.0.1" && remoteIp != "localhost"
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            // Consumir cabeceras HTTP hasta línea vacía
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break
            }

            val path = requestLine.split(" ").getOrNull(1)?.lowercase(Locale.ROOT) ?: "/telemetria"

            when {
                path.startsWith("/adelante") -> {
                    if (simDistanceCm.get() > VehicleTelemetry.CRITICAL_DISTANCE_CM) {
                        simMotorState.set(VehicleTelemetry.MOTOR_AVANZANDO)
                    } else {
                        simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                    }
                    if (isExternalPhone) {
                        onSystemLog("📱 Celular remoto ($remoteIp) ordenó: ADELANTE")
                    }
                }
                path.startsWith("/izquierda") -> {
                    simMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA)
                    if (isExternalPhone) {
                        onSystemLog("📱 Celular remoto ($remoteIp) ordenó: DOBLAR IZQUIERDA")
                    }
                }
                path.startsWith("/derecha") -> {
                    simMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_DERECHA)
                    if (isExternalPhone) {
                        onSystemLog("📱 Celular remoto ($remoteIp) ordenó: DOBLAR DERECHA")
                    }
                }
                path.startsWith("/atras") -> {
                    simMotorState.set(VehicleTelemetry.MOTOR_RETROCEDIENDO)
                    if (isExternalPhone) {
                        onSystemLog("📱 Celular remoto ($remoteIp) ordenó: ATRÁS")
                    }
                }
                path.startsWith("/parar") -> {
                    simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                    if (isExternalPhone) {
                        onSystemLog("📱 Celular remoto ($remoteIp) ordenó: PARAR")
                    }
                }
                path.startsWith("/obstaculo_critico") -> {
                    simDistanceCm.set(11)
                    simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                }
                else -> {
                    // En cada lectura de /telemetria actualizamos la cinemática del vehículo
                    val currentMotor = simMotorState.get()
                    val currentDist = simDistanceCm.get()
                    val nextDist = when (currentMotor) {
                        VehicleTelemetry.MOTOR_AVANZANDO -> (currentDist - 5).coerceAtLeast(8)
                        VehicleTelemetry.MOTOR_RETROCEDIENDO -> (currentDist + 6).coerceAtMost(140)
                        VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA,
                        VehicleTelemetry.MOTOR_GIRANDO_DERECHA -> (currentDist + 2).coerceAtMost(140)
                        else -> currentDist
                    }
                    simDistanceCm.set(nextDist)
                    if (nextDist <= VehicleTelemetry.CRITICAL_DISTANCE_CM &&
                        currentMotor == VehicleTelemetry.MOTOR_AVANZANDO
                    ) {
                        // Freno automático de emergencia del puente H L298N al llegar a <= 15 cm
                        simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                    }
                }
            }

            val dist = simDistanceCm.get()
            val motor = simMotorState.get()
            val freno = dist <= VehicleTelemetry.CRITICAL_DISTANCE_CM
            val jsonPayload = String.format(
                Locale.US,
                "{\"distancia\": %d, \"estadoMotor\": \"%s\", \"frenoActivo\": %b}",
                dist,
                motor,
                freno
            )

            val responseBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Content-Length: ${responseBytes.size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.write(jsonPayload)
            writer.flush()
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Permite inyectar instantáneamente una distancia específica en el simulador / modo emisor
     * (ej. <= 15 cm para demostrar el bloqueo automático del botón ADELANTE y el banner ⚠️ FRENO ACTIVO).
     */
    fun setSimulatedObstacleDistance(distanceCm: Int) {
        val clamped = distanceCm.coerceIn(5, 150)
        simDistanceCm.set(clamped)
        if (clamped <= VehicleTelemetry.CRITICAL_DISTANCE_CM &&
            simMotorState.get() == VehicleTelemetry.MOTOR_AVANZANDO
        ) {
            simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
        }
    }

    fun isEmbeddedSimulatorRunning(): Boolean {
        return localEmbeddedEsp32Job?.isActive == true && localEmbeddedServerSocket?.isClosed == false
    }

    fun stopEmbeddedEsp32Simulator() {
        localEmbeddedEsp32Job?.cancel()
        localEmbeddedEsp32Job = null
        safeCloseServerSocket(localEmbeddedServerSocket)
        localEmbeddedServerSocket = null
    }

    /**
     * Cierra de forma limpia y segura las conexiones activas y envía orden preventiva de parada si estaba enlazado.
     */
    fun disconnect() {
        telemetryJob?.cancel()
        telemetryJob = null
        stopEmbeddedEsp32Simulator()
        isConnectedFlag = false
        onSystemLog("Enlace Wi-Fi ESP32 desconectado de forma segura.")
        onStateChanged(ConnectionState.Disconnected)
    }

    private suspend fun handleNetworkFailure(
        host: String,
        port: Int,
        failureCount: Int,
        detail: String
    ) = withContext(Dispatchers.IO) {
        onSystemLog("Alerta de enlace Wi-Fi ($host:$port) [Intento $failureCount/2]: $detail")
        if (failureCount == 1) {
            onStateChanged(ConnectionState.Error("Inestabilidad con ESP32 ($host:$port): $detail"))
        }
    }

    private fun buildBaseUrl(host: String, port: Int): String {
        val clean = host.removePrefix("http://").removePrefix("https://").trimEnd('/')
        return if (port == 80) {
            "http://$clean"
        } else {
            "http://$clean:$port"
        }
    }

    private fun safeCloseServerSocket(serverSocket: ServerSocket?) {
        if (serverSocket == null) return
        try {
            if (!serverSocket.isClosed) {
                serverSocket.close()
            }
        } catch (_: Exception) {
        }
    }
}
