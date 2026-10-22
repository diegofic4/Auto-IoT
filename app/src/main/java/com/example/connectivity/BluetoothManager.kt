package com.example.connectivity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.ConnectionState
import com.example.model.PairedBluetoothDevice
import com.example.model.TransportMode
import com.example.model.VehicleCommand
import com.example.model.VehicleTelemetry
import com.example.model.VehicleTelemetryParser
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gestor de Conectividad Bluetooth Clásico RFCOMM (Serial Port Profile - SPP) para el Vehículo ESP32.
 *
 * Cumple con los estándares ISO/IEC 27400 y tolerancia a fallos:
 * - UUID SPP Estándar: `00001101-0000-1000-8000-00805F9B34FB`.
 * - Toda operación de conexión, envío de comandos (`ADELANTE`, `ATRAS`, `PARAR`) y lectura de telemetría
 *   JSON (`{"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}`) ocurre en [Dispatchers.IO].
 * - Ante apagado del ESP32 o pérdida de enlace RFCOMM, captura `IOException` / `SocketException`, cierra
 *   el `BluetoothSocket` limpiamente y actualiza el estado a `Desconectado` con cero crashes.
 */
class BluetoothManager(
    context: Context,
    private val externalScope: CoroutineScope,
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onTelemetryReceived: (VehicleTelemetry) -> Unit,
    private val onSystemLog: (String) -> Unit
) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "Auto_IoT_Control_SPP"
    }

    private val appContext = context.applicationContext
    private val systemBtManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = systemBtManager?.adapter

    @Volatile
    private var activeBtSocket: BluetoothSocket? = null

    @Volatile
    private var activeBtServerSocket: BluetoothServerSocket? = null

    @Volatile
    private var activeWriter: BufferedWriter? = null

    private var btConnectionJob: Job? = null
    private var btServerEmitterJob: Job? = null
    private val sequenceCounter = AtomicLong(0L)

    // Estado de simulación bidireccional cuando este celular actúa como Nodo Emisor Bluetooth frente a otro celular
    private val btSimDistanceCm = java.util.concurrent.atomic.AtomicInteger(60)
    private val btSimMotorState = java.util.concurrent.atomic.AtomicReference(VehicleTelemetry.MOTOR_DETENIDO)

    fun isHardwareSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Lista los dispositivos Bluetooth emparejados (ej. otro Celular Android, `Auto_IoT_Control` / ESP32 o PC).
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<PairedBluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasConnectPermission() || !adapter.isEnabled) return emptyList()

        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                PairedBluetoothDevice(
                    name = device.name?.takeIf { it.isNotBlank() } ?: "Dispositivo BT (${device.address})",
                    macAddress = device.address
                )
            }.sortedBy { it.name }
        } catch (se: SecurityException) {
            onSystemLog("Permiso BLUETOOTH_CONNECT requerido para listar dispositivos Bluetooth.")
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Conecta como Cliente RFCOMM a otro Celular, ESP32 o PC con la dirección MAC indicada usando `SPP_UUID`.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(macAddress: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onStateChanged(ConnectionState.Error("Este dispositivo no cuenta con adaptador de hardware Bluetooth."))
            return
        }
        if (!hasConnectPermission()) {
            onStateChanged(ConnectionState.Error("Permisos Bluetooth en runtime no otorgados."))
            return
        }
        if (!adapter.isEnabled) {
            onStateChanged(ConnectionState.Error("Active el Bluetooth del sistema para conectar al vehículo o celular."))
            return
        }

        disconnectInternal(notifyDisconnected = false)

        btConnectionJob = externalScope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                onStateChanged(ConnectionState.Connecting("BT $macAddress"))
                onSystemLog("Iniciando conexión Bluetooth RFCOMM con $macAddress (UUID $SPP_UUID)...")

                val remoteDevice = adapter.getRemoteDevice(macAddress)
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }

                socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                activeBtSocket = socket
                socket.connect()

                activeWriter = BufferedWriter(
                    OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8)
                )

                val deviceName = try {
                    remoteDevice.name ?: macAddress
                } catch (_: SecurityException) {
                    macAddress
                }

                onStateChanged(
                    ConnectionState.Connected(
                        endpointLabel = "$deviceName ($macAddress)",
                        transport = TransportMode.BLUETOOTH_RFCOMM
                    )
                )
                onSystemLog("Enlace Bluetooth RFCOMM activo con $deviceName")

                readRfcommStream(socket, "BT RFCOMM ($deviceName)")
            } catch (ce: CancellationException) {
                throw ce
            } catch (se: SecurityException) {
                onStateChanged(ConnectionState.Error("Permiso Bluetooth denegado por el sistema operativo."))
            } catch (ioe: IOException) {
                onSystemLog("Fallo de conexión RFCOMM con $macAddress: ${ioe.localizedMessage}")
                onStateChanged(ConnectionState.Disconnected)
            } catch (e: Exception) {
                onSystemLog("Excepción Bluetooth: ${e.localizedMessage}")
                onStateChanged(ConnectionState.Disconnected)
            } finally {
                closeSocketResources(socket, null)
            }
        }
    }

    /**
     * Inicia modo Servidor Bluetooth RFCOMM SPP para que **otro celular** (o el script en PC)
     * pueda conectarse directamente a este teléfono y recibir telemetría / enviar comandos en vivo.
     */
    @SuppressLint("MissingPermission")
    fun startRfcommServer() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onStateChanged(ConnectionState.Error("Hardware Bluetooth no disponible en este dispositivo."))
            return
        }
        if (!hasConnectPermission()) {
            onStateChanged(ConnectionState.Error("Conceda los permisos Bluetooth antes de escuchar por RFCOMM."))
            return
        }
        if (!adapter.isEnabled) {
            onStateChanged(ConnectionState.Error("El adaptador Bluetooth está apagado."))
            return
        }

        disconnectInternal(notifyDisconnected = false)
        btSimDistanceCm.set(60)
        btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)

        btConnectionJob = externalScope.launch(Dispatchers.IO) {
            var serverSocket: BluetoothServerSocket? = null
            var clientSocket: BluetoothSocket? = null
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                activeBtServerSocket = serverSocket
                onStateChanged(ConnectionState.Connecting("Esperando a otro celular (BT)..."))
                onSystemLog("Modo Servidor Bluetooth activo. Seleccione este teléfono desde el otro celular...")

                clientSocket = serverSocket.accept()
                activeBtSocket = clientSocket
                val writer = BufferedWriter(
                    OutputStreamWriter(clientSocket.outputStream, StandardCharsets.UTF_8)
                )
                activeWriter = writer

                safeCloseServerSocket(serverSocket)
                activeBtServerSocket = null

                val peerName = try {
                    clientSocket.remoteDevice?.name ?: clientSocket.remoteDevice?.address ?: "Celular Remoto"
                } catch (_: SecurityException) {
                    "Celular Remoto"
                }
                onStateChanged(
                    ConnectionState.Connected(
                        endpointLabel = "Celular BT ($peerName)",
                        transport = TransportMode.BLUETOOTH_RFCOMM
                    )
                )
                onSystemLog("📱 Celular / Dispositivo conectado por Bluetooth: $peerName")

                // Inicia emisión periódica de telemetría hacia el otro celular conectado
                btServerEmitterJob = externalScope.launch(Dispatchers.IO) {
                    while (isActive && clientSocket.isConnected) {
                        val currentMotor = btSimMotorState.get()
                        val currentDist = btSimDistanceCm.get()
                        val nextDist = when (currentMotor) {
                            VehicleTelemetry.MOTOR_AVANZANDO -> (currentDist - 5).coerceAtLeast(8)
                            VehicleTelemetry.MOTOR_RETROCEDIENDO -> (currentDist + 6).coerceAtMost(140)
                            VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA,
                            VehicleTelemetry.MOTOR_GIRANDO_DERECHA -> (currentDist + 2).coerceAtMost(140)
                            else -> currentDist
                        }
                        btSimDistanceCm.set(nextDist)
                        if (nextDist <= VehicleTelemetry.CRITICAL_DISTANCE_CM &&
                            currentMotor == VehicleTelemetry.MOTOR_AVANZANDO
                        ) {
                            btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                        }

                        val dist = btSimDistanceCm.get()
                        val motor = btSimMotorState.get()
                        val freno = dist <= VehicleTelemetry.CRITICAL_DISTANCE_CM
                        val jsonLine = String.format(
                            java.util.Locale.US,
                            "{\"distancia\": %d, \"estadoMotor\": \"%s\", \"frenoActivo\": %b}",
                            dist,
                            motor,
                            freno
                        )

                        try {
                            synchronized(writer) {
                                writer.write("$jsonLine\n")
                                writer.flush()
                            }
                            val seq = sequenceCounter.incrementAndGet()
                            VehicleTelemetryParser.parseJson(jsonLine, "BT Emisor ($peerName)", seq)
                                .onSuccess { telemetry ->
                                    onTelemetryReceived(telemetry)
                                }
                        } catch (_: IOException) {
                            break
                        }
                        kotlinx.coroutines.delay(650L)
                    }
                }

                readRfcommStream(clientSocket, "BT P2P ($peerName)")
            } catch (ce: CancellationException) {
                throw ce
            } catch (ioe: IOException) {
                if (isActive) {
                    onSystemLog("Enlace RFCOMM finalizado: ${ioe.localizedMessage}")
                    onStateChanged(ConnectionState.Disconnected)
                }
            } finally {
                btServerEmitterJob?.cancel()
                closeSocketResources(clientSocket, serverSocket)
            }
        }
    }

    fun setSimulatedObstacleDistance(distanceCm: Int) {
        val clamped = distanceCm.coerceIn(5, 150)
        btSimDistanceCm.set(clamped)
        if (clamped <= VehicleTelemetry.CRITICAL_DISTANCE_CM &&
            btSimMotorState.get() == VehicleTelemetry.MOTOR_AVANZANDO
        ) {
            btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
        }
    }

    /**
     * Envía una orden de motor (`ADELANTE`, `IZQUIERDA`, `DERECHA`, `ATRAS`, `PARAR`) a través del socket Bluetooth RFCOMM en `Dispatchers.IO`.
     */
    fun sendVehicleCommand(command: VehicleCommand, currentDistanceCm: Int) {
        if (command == VehicleCommand.ADELANTE && currentDistanceCm <= VehicleTelemetry.CRITICAL_DISTANCE_CM) {
            onSystemLog(
                "BLOQUEO DE SEGURIDAD OT (BT): Comando ADELANTE cancelado por distancia crítica ($currentDistanceCm cm <= 15 cm)."
            )
            return
        }

        // Si este celular está actuando como Servidor Bluetooth P2P, también actualiza su estado local
        when (command) {
            VehicleCommand.ADELANTE -> if (btSimDistanceCm.get() > VehicleTelemetry.CRITICAL_DISTANCE_CM) {
                btSimMotorState.set(VehicleTelemetry.MOTOR_AVANZANDO)
            }
            VehicleCommand.IZQUIERDA -> btSimMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA)
            VehicleCommand.DERECHA -> btSimMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_DERECHA)
            VehicleCommand.ATRAS -> btSimMotorState.set(VehicleTelemetry.MOTOR_RETROCEDIENDO)
            VehicleCommand.PARAR -> btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
        }

        externalScope.launch(Dispatchers.IO) {
            val writer = activeWriter
            val socket = activeBtSocket
            if (writer == null || socket == null || !socket.isConnected) {
                onSystemLog("No hay socket Bluetooth RFCOMM conectado para enviar ${command.btCommandToken}.")
                onStateChanged(ConnectionState.Disconnected)
                return@launch
            }
            try {
                synchronized(writer) {
                    writer.write("${command.btCommandToken}\n")
                    writer.flush()
                }
                onSystemLog("Comando BT RFCOMM enviado -> ${command.btCommandToken}")
            } catch (ioe: IOException) {
                onSystemLog("Pérdida de enlace Bluetooth al enviar comando: ${ioe.localizedMessage}")
                disconnect()
            }
        }
    }

    private fun readRfcommStream(socket: BluetoothSocket, transportLabel: String) {
        try {
            BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (btConnectionJob?.isActive == true && socket.isConnected) {
                    val rawLine = reader.readLine() ?: break
                    val line = rawLine.trim()
                    if (line.isEmpty()) continue

                    // Soportar recepción de comandos directos desde otro celular ("ADELANTE", "IZQUIERDA", "DERECHA", "ATRAS", "PARAR")
                    val upper = line.uppercase(java.util.Locale.ROOT)
                    if (upper == "ADELANTE" || upper == "/ADELANTE") {
                        if (btSimDistanceCm.get() > VehicleTelemetry.CRITICAL_DISTANCE_CM) {
                            btSimMotorState.set(VehicleTelemetry.MOTOR_AVANZANDO)
                        } else {
                            btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                        }
                        onSystemLog("📱 Celular remoto (BT) ordenó: ADELANTE")
                        continue
                    } else if (upper == "IZQUIERDA" || upper == "/IZQUIERDA") {
                        btSimMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA)
                        onSystemLog("📱 Celular remoto (BT) ordenó: DOBLAR IZQUIERDA")
                        continue
                    } else if (upper == "DERECHA" || upper == "/DERECHA") {
                        btSimMotorState.set(VehicleTelemetry.MOTOR_GIRANDO_DERECHA)
                        onSystemLog("📱 Celular remoto (BT) ordenó: DOBLAR DERECHA")
                        continue
                    } else if (upper == "ATRAS" || upper == "/ATRAS") {
                        btSimMotorState.set(VehicleTelemetry.MOTOR_RETROCEDIENDO)
                        onSystemLog("📱 Celular remoto (BT) ordenó: ATRÁS")
                        continue
                    } else if (upper == "PARAR" || upper == "/PARAR") {
                        btSimMotorState.set(VehicleTelemetry.MOTOR_DETENIDO)
                        onSystemLog("📱 Celular remoto (BT) ordenó: PARAR")
                        continue
                    }

                    val seq = sequenceCounter.incrementAndGet()
                    VehicleTelemetryParser.parseJson(line, transportLabel, seq)
                        .onSuccess { telemetry ->
                            onTelemetryReceived(telemetry)
                        }
                        .onFailure { err ->
                            onSystemLog("Trama BT ignorada (#$seq): ${err.message}")
                        }
                }
            }
            if (btConnectionJob?.isActive == true) {
                onSystemLog("El dispositivo Bluetooth cerró la conexión. Pasando a Desconectado.")
                onStateChanged(ConnectionState.Disconnected)
            }
        } catch (se: SocketException) {
            if (btConnectionJob?.isActive == true) {
                onSystemLog("Desconexión detectada en Bluetooth: ${se.localizedMessage}")
                onStateChanged(ConnectionState.Disconnected)
            }
        } catch (ioe: IOException) {
            if (btConnectionJob?.isActive == true) {
                onSystemLog("Interrupción de enlace Bluetooth: ${ioe.localizedMessage}")
                onStateChanged(ConnectionState.Disconnected)
            }
        }
    }

    /**
     * Cierra de forma limpia y segura cualquier `BluetoothSocket` o `BluetoothServerSocket`.
     */
    fun disconnect() {
        disconnectInternal(notifyDisconnected = true)
    }

    private fun disconnectInternal(notifyDisconnected: Boolean) {
        btServerEmitterJob?.cancel()
        btServerEmitterJob = null
        btConnectionJob?.cancel()
        btConnectionJob = null
        closeSocketResources(activeBtSocket, activeBtServerSocket)
        if (notifyDisconnected) {
            onSystemLog("Desconexión segura de Bluetooth RFCOMM completada.")
            onStateChanged(ConnectionState.Disconnected)
        }
    }

    private fun closeSocketResources(socket: BluetoothSocket?, serverSocket: BluetoothServerSocket?) {
        try {
            activeWriter?.close()
        } catch (_: Exception) {
        }
        activeWriter = null

        if (socket != null) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        if (activeBtSocket === socket) {
            activeBtSocket = null
        }

        safeCloseServerSocket(serverSocket)
        if (activeBtServerSocket === serverSocket) {
            activeBtServerSocket = null
        }
    }

    private fun safeCloseServerSocket(serverSocket: BluetoothServerSocket?) {
        if (serverSocket == null) return
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }
}
