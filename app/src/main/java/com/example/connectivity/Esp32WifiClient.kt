package com.example.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.example.model.ConnectionState
import com.example.model.TelemetryStore
import com.example.model.TransportMode
import com.example.model.VehicleCommand
import com.example.model.VehicleTelemetry
import com.example.model.VehicleTelemetryParser
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    context: Context,
    private val externalScope: CoroutineScope,
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onTelemetryReceived: (VehicleTelemetry) -> Unit,
    private val onArchiveRestored: (List<VehicleTelemetry>) -> Unit,
    private val onSystemLog: (String) -> Unit
) {

    companion object {
        const val DEFAULT_ESP32_AP_SSID = "Auto_IoT_Control"
        const val DEFAULT_ESP32_IP = "192.168.4.1"
        const val DEFAULT_ESP32_PORT = 80
        const val SIMULATOR_LOCAL_IP = "127.0.0.1"
        const val SIMULATOR_LOCAL_PORT = 8080
        private const val POLLING_INTERVAL_MS = 650L
        private const val FIRST_RETRY_MS = 1000L
        private const val MAX_RETRY_MS = 8000L
        private const val HEARTBEAT_INTERVAL_MS = 1200L
        private const val LOCAL_SAMPLE_INTERVAL_MS = 5000L
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .writeTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(4500, TimeUnit.MILLISECONDS)
        .dns(localAwareDns(null))
        .proxy(Proxy.NO_PROXY)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    private class Connection(val url: HttpUrl) {
        val label = "${url.host}:${url.port}"
        var pollJob: Job? = null
        var pollCall: Call? = null
        val commandCalls = mutableSetOf<Call>()
        var ready = false
        var commandGeneration = 0L
        var lastFailure: String? = null
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val telemetryStore = TelemetryStore(context)
    private val connectionLock = Any()
    private val networkClientLock = Any()
    private var wifiClient: Pair<Network, OkHttpClient>? = null
    private var activeConnection: Connection? = null
    private var localEmbeddedEsp32Job: Job? = null
    private var localEmbeddedServerSocket: ServerSocket? = null
    private var embeddedGeneration = 0L

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
        val baseUrl = buildBaseUrl(cleanHost, port).toHttpUrlOrNull()
        synchronized(connectionLock) {
            cancelConnectionLocked()
            if (baseUrl == null) {
                onStateChanged(ConnectionState.Error("Ingrese una dirección y un puerto válidos para el ESP32."))
                return
            }

            val connection = Connection(baseUrl)
            activeConnection = connection
            onStateChanged(ConnectionState.Connecting(connection.label))
            onSystemLog("Conectando con el ESP32 en ${connection.label}.")
            connection.pollJob = externalScope.launch(Dispatchers.IO) { pollTelemetry(connection) }
        }
    }

    private suspend fun pollTelemetry(connection: Connection) {
        var retryMs = FIRST_RETRY_MS
        var legacy = false

        while (currentCoroutineContext().isActive) {
            var call: Call? = null
            var waitMs = POLLING_INTERVAL_MS
            try {
                val client = clientForHost(connection.url.host)
                val nextCall = if (legacy) {
                    client.newCall(localRequest(connection, "telemetria"))
                } else {
                    client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
                        .newCall(localRequest(connection, "eventos"))
                }
                synchronized(connectionLock) {
                    if (activeConnection !== connection) return
                    connection.pollCall = nextCall
                }
                call = nextCall
                nextCall.execute().use { response ->
                    if (!legacy && response.code == 404) {
                        legacy = true
                        continue
                    }
                    if (!response.isSuccessful) throw IOException("El ESP32 devolvió HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Telemetría vacía")
                    if (legacy) {
                        val telemetry = parseReading(connection, body.string()).getOrThrow()
                        if (!publishReading(connection, telemetry)) return
                        retryMs = FIRST_RETRY_MS
                    } else {
                        val source = body.source()
                        var pendingAck: String? = null
                        var lastHeartbeat = 0L
                        var lastSaved = 0L
                        var restored = 0
                        while (currentCoroutineContext().isActive) {
                            val line = source.readUtf8LineStrict(335)
                            val telemetry = parseReading(connection, line).getOrThrow()
                            val id = telemetry.readingId ?: throw IOException("La lectura no tiene identificador")
                            if (telemetry.archived) {
                                if (telemetryStore.save(connection.label, telemetry)) restored++
                                pendingAck = id
                                continue
                            }

                            val now = android.os.SystemClock.elapsedRealtime()
                            if (pendingAck != null || lastHeartbeat == 0L || now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                                sendHeartbeat(connection, client, pendingAck)
                                pendingAck = null
                                lastHeartbeat = now
                                if (restored > 0) {
                                    onArchiveRestored(telemetryStore.recent(35, archivedOnly = true))
                                    onSystemLog("$restored lecturas recuperadas del ESP32 y guardadas en el historial.")
                                    restored = 0
                                }
                            }
                            if (lastSaved == 0L || now - lastSaved >= LOCAL_SAMPLE_INTERVAL_MS) {
                                telemetryStore.save(connection.label, telemetry)
                                lastSaved = now
                            }
                            if (!publishReading(connection, telemetry)) return
                            retryMs = FIRST_RETRY_MS
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val detail = networkErrorDetail(error)
                synchronized(connectionLock) {
                    if (activeConnection !== connection) return
                    invalidateCommandsLocked(connection)
                    if (connection.lastFailure != detail) {
                        onSystemLog("Error de enlace con el ESP32 (${connection.label}). $detail")
                        onStateChanged(
                            ConnectionState.Error("ESP32 ${connection.label}. $detail Reintentando automáticamente.")
                        )
                        connection.lastFailure = detail
                    }
                }
                waitMs = retryMs
                retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
            } finally {
                synchronized(connectionLock) {
                    if (connection.pollCall === call) connection.pollCall = null
                }
            }
            delay(waitMs)
        }
    }

    private fun parseReading(connection: Connection, json: String): Result<VehicleTelemetry> =
        VehicleTelemetryParser.parseJson(
            json, "Wi-Fi HTTP (${connection.url.host})", sequenceCounter.incrementAndGet()
        )

    private fun publishReading(connection: Connection, telemetry: VehicleTelemetry): Boolean =
        synchronized(connectionLock) {
            if (activeConnection !== connection) return false
            if (!connection.ready) {
                onStateChanged(ConnectionState.Connected(connection.label, TransportMode.WIFI_ESP32))
                onSystemLog("Enlace HTTP establecido con el ESP32 (${connection.label}).")
            }
            connection.ready = true
            connection.lastFailure = null
            onTelemetryReceived(telemetry)
            true
        }

    private fun sendHeartbeat(connection: Connection, client: OkHttpClient, ack: String?) {
        val url = connection.url.newBuilder().addPathSegment("latido").apply {
            if (ack != null) addQueryParameter("ack", ack)
        }.build()
        val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
        val call = client.newCall(request)
        synchronized(connectionLock) {
            if (activeConnection !== connection) return
            connection.commandCalls.add(call)
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Confirmación del ESP32: HTTP ${response.code}")
            }
        } finally {
            synchronized(connectionLock) { connection.commandCalls.remove(call) }
        }
    }

    fun recentStoredReadings(limit: Int, archivedOnly: Boolean = false): List<VehicleTelemetry> =
        telemetryStore.recent(limit, archivedOnly)

    /**
     * Envía la orden solo en la conexión en que se solicitó. La reconexión
     * recupera la telemetría y requiere una nueva orden para mover el vehículo.
     */
    fun sendVehicleCommand(command: VehicleCommand, currentDistanceCm: Int) {
        if (command == VehicleCommand.ADELANTE && currentDistanceCm <= VehicleTelemetry.CRITICAL_DISTANCE_CM) {
            onSystemLog("Orden ADELANTE bloqueada a $currentDistanceCm cm.")
            return
        }

        val (connection, generation) = synchronized(connectionLock) {
            val current = activeConnection
            if (current == null || (!current.ready && command != VehicleCommand.PARAR)) {
                onSystemLog("Orden ${command.displayLabel} omitida mientras se recupera la telemetría.")
                return
            }
            current to current.commandGeneration
        }

        externalScope.launch(Dispatchers.IO) {
            val path = command.endpointPath.removePrefix("/")
            val call = clientForHost(connection.url.host).newCall(localRequest(connection, path))
            synchronized(connectionLock) {
                if (activeConnection !== connection || connection.commandGeneration != generation) return@launch
                connection.commandCalls.add(call)
            }
            try {
                call.execute().use { response ->
                    val raw = response.body?.string().orEmpty().trim()
                    currentCoroutineContext().ensureActive()
                    synchronized(connectionLock) {
                        if (activeConnection !== connection || connection.commandGeneration != generation) {
                            return@launch
                        }
                        if (response.isSuccessful) {
                            onSystemLog("ACK ${command.displayLabel} (HTTP ${response.code}).")
                        } else {
                            onSystemLog("ERR ${command.displayLabel} (HTTP ${response.code}). $raw")
                        }
                        if ((response.isSuccessful || response.code == 409 || response.code == 422) &&
                            raw.startsWith("{") && raw.endsWith("}")
                        ) {
                            VehicleTelemetryParser.parseJson(
                                raw,
                                "Wi-Fi CMD (${command.endpointPath})",
                                sequenceCounter.incrementAndGet()
                            ).onSuccess(onTelemetryReceived)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Exception) {
                synchronized(connectionLock) {
                    if (activeConnection === connection && connection.commandGeneration == generation) {
                        onSystemLog("No se confirmó ${command.displayLabel}. ${networkErrorDetail(error)}")
                    }
                }
            } finally {
                synchronized(connectionLock) { connection.commandCalls.remove(call) }
            }
        }
    }

    private fun localRequest(connection: Connection, path: String): Request =
        Request.Builder()
            .url(connection.url.newBuilder().addPathSegment(path).build())
            .get()
            .header("Accept", "application/json")
            .header("Connection", "close")
            .build()

    private fun networkErrorDetail(error: Exception): String = when (error) {
        is SocketTimeoutException -> "La conexión HTTP no respondió a tiempo."
        else -> error.localizedMessage?.trimEnd('.')?.let { "$it." } ?: "No se pudo completar la conexión HTTP."
    }

    private fun invalidateCommandsLocked(connection: Connection) {
        connection.ready = false
        connection.commandGeneration++
        connection.commandCalls.forEach(Call::cancel)
        connection.commandCalls.clear()
    }

    private fun cancelConnectionLocked() {
        activeConnection?.let { previous ->
            previous.pollJob?.cancel()
            previous.pollCall?.cancel()
            invalidateCommandsLocked(previous)
        }
        activeConnection = null
    }

    private fun clientForHost(host: String): OkHttpClient {
        val network = wifiNetworkForHost(host) ?: return okHttpClient
        return synchronized(networkClientLock) {
            wifiClient?.takeIf { it.first == network }?.second
                ?: okHttpClient.newBuilder()
                    .socketFactory(network.socketFactory)
                    .dns(localAwareDns(network))
                    .proxy(Proxy.NO_PROXY)
                    .build()
                    .also { wifiClient = network to it }
        }
    }

    private fun localAwareDns(network: Network?): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val ipv4 = parseIpv4Bytes(hostname)
            return if (ipv4 != null) {
                listOf(InetAddress.getByAddress(hostname, ipv4))
            } else if (network != null) {
                network.getAllByName(hostname).toList()
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private fun parseIpv4Bytes(host: String): ByteArray? {
        val fields = host.split('.')
        if (fields.size != 4) return null
        val bytes = ByteArray(4)
        for ((index, value) in fields.withIndex()) {
            val octet = value.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            bytes[index] = octet.toByte()
        }
        return bytes
    }

    private fun ipv4ToInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

    private fun isInSameIpv4Subnet(hostBytes: ByteArray, linkProperties: LinkProperties): Boolean {
        val targetInt = ipv4ToInt(hostBytes)
        return linkProperties.linkAddresses.any { linkAddr ->
            val addr = linkAddr.address
            if (addr is Inet4Address) {
                val prefix = linkAddr.prefixLength.coerceIn(0, 32)
                val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                (ipv4ToInt(addr.address) and mask) == (targetInt and mask)
            } else {
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiNetworkForHost(host: String): Network? {
        // El gateway del emulador y la interfaz local conservan el enrutamiento normal.
        if (host == "10.0.2.2" || host == SIMULATOR_LOCAL_IP) return null
        val bytes = parseIpv4Bytes(host) ?: return null
        val address = InetAddress.getByAddress(bytes)
        val manager = connectivityManager ?: return null
        return try {
            val wifiNetworks = manager.allNetworks.filter { network ->
                manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            // 1. Preferir la red Wi-Fi cuya subred IPv4 o tabla de rutas incluya la IP del ESP32
            //    (sin depender de route.gateway?.isAnyLocalAddress, que falla en Samsung/Xiaomi/Motorola).
            wifiNetworks.firstOrNull { network ->
                val props = manager.getLinkProperties(network) ?: return@firstOrNull false
                isInSameIpv4Subnet(bytes, props) ||
                    props.routes.any { route -> !route.isDefaultRoute && route.matches(address) }
            } ?: if (address.isSiteLocalAddress) {
                // 2. Respaldo para IPs privadas LAN (192.168.x.x / 10.x.x.x / 172.16-31.x.x):
                //    forzar salida por Wi-Fi aunque los datos móviles estén encendidos.
                wifiNetworks.firstOrNull()
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
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
        synchronized(connectionLock) {
            stopEmbeddedSimulatorLocked()
            simDistanceCm.set(55)
            simMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
            val generation = embeddedGeneration
            val previousConnection = activeConnection

            localEmbeddedEsp32Job = externalScope.launch(Dispatchers.IO) {
                var server: ServerSocket? = null
                try {
                    val boundServer = ServerSocket()
                    server = boundServer
                    boundServer.apply {
                        reuseAddress = true
                        bind(InetSocketAddress("0.0.0.0", SIMULATOR_LOCAL_PORT))
                    }
                    val localIps = getDeviceLocalIpAddresses()
                    val ipSummary = if (localIps.isNotEmpty()) localIps.joinToString(", ") else SIMULATOR_LOCAL_IP
                    synchronized(connectionLock) {
                        if (generation != embeddedGeneration) return@launch
                        localEmbeddedServerSocket = boundServer
                        onSystemLog("Servidor local activo en $SIMULATOR_LOCAL_PORT (IPs: $ipSummary).")
                        if (activeConnection === previousConnection) {
                            connectAndPollTelemetry(SIMULATOR_LOCAL_IP, SIMULATOR_LOCAL_PORT)
                        }
                    }

                    while (isActive && !boundServer.isClosed) {
                        val client = try {
                            boundServer.accept()
                        } catch (_: SocketException) {
                            break
                        }
                        handleEmbeddedHttpClient(client)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (error: Exception) {
                    synchronized(connectionLock) {
                        if (generation == embeddedGeneration) {
                            onSystemLog("Servidor local finalizado: ${error.localizedMessage}")
                        }
                    }
                } finally {
                    safeCloseServerSocket(server)
                    synchronized(connectionLock) {
                        if (localEmbeddedServerSocket === server) localEmbeddedServerSocket = null
                    }
                }
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

    fun isEmbeddedSimulatorRunning(): Boolean = synchronized(connectionLock) {
        localEmbeddedEsp32Job?.isActive == true && localEmbeddedServerSocket?.isClosed == false
    }

    private fun stopEmbeddedSimulatorLocked() {
        embeddedGeneration++
        localEmbeddedEsp32Job?.cancel()
        localEmbeddedEsp32Job = null
        safeCloseServerSocket(localEmbeddedServerSocket)
        localEmbeddedServerSocket = null
    }

    fun disconnect() {
        synchronized(connectionLock) {
            cancelConnectionLocked()
            stopEmbeddedSimulatorLocked()
            onSystemLog("Enlace Wi-Fi del ESP32 cerrado.")
            onStateChanged(ConnectionState.Disconnected)
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
