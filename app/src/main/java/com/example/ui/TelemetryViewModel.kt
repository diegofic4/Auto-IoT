package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.connectivity.BluetoothManager
import com.example.connectivity.Esp32WifiClient
import com.example.model.ConnectionState
import com.example.model.PairedBluetoothDevice
import com.example.model.TransportMode
import com.example.model.VehicleCommand
import com.example.model.VehicleTelemetry
import com.example.security.AuthRepository
import com.example.security.AuthResult
import com.example.security.CryptoManager
import com.example.security.SecurityAuditInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado inmutable de la UI de Teleoperación y Monitoreo del Vehículo ESP32.
 */
data class VehicleUiState(
    // 1. Seguridad, Cuentas de Usuario y Sesión Cifrada (ISO/IEC 27400)
    val isAuthenticated: Boolean = false,
    val authErrorMessage: String? = null,
    val authStatusMessage: String? = null,
    val securityAuditInfo: SecurityAuditInfo? = null,
    val registeredUsernames: List<String> = emptyList(),
    val passwordChangeMessage: String? = null,
    val passwordChangeError: String? = null,

    // 2. Configuración de Enlace Inalámbrico Dual (Wi-Fi ESP32 / Celular a Celular vs Bluetooth RFCOMM)
    val selectedTransport: TransportMode = TransportMode.WIFI_ESP32,
    val esp32IpInput: String = Esp32WifiClient.DEFAULT_ESP32_IP,
    val esp32PortInput: String = Esp32WifiClient.DEFAULT_ESP32_PORT.toString(),
    val isLocalEmbeddedSimRunning: Boolean = false,
    val localDeviceIps: List<String> = emptyList(),

    // 3. Estado Bluetooth Clásico RFCOMM
    val isBtHardwareSupported: Boolean = false,
    val isBtEnabled: Boolean = false,
    val pairedBtDevices: List<PairedBluetoothDevice> = emptyList(),
    val manualBtMacInput: String = "00:11:22:33:FF:EE",

    // 4. Telemetría en Vivo del Vehículo (HC-SR04 + Puente H L298N + LED Advertencia)
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val distanciaCm: Int = 45,
    val estadoMotor: String = VehicleTelemetry.MOTOR_DETENIDO,
    val frenoActivo: Boolean = false,
    val hasReceivedFirstPacket: Boolean = false,
    val telemetryHistory: List<VehicleTelemetry> = emptyList(),
    val systemEventLog: List<String> = emptyList(),

    // 5. Estadísticas de Misión OT
    val totalPacketsReceived: Int = 0,
    val totalEmergencyBrakesTriggered: Int = 0,
    val lastCommandDispatched: String = "NINGUNO",

    // 6. Paleta de Colores de la Estación OT
    val selectedPalette: com.example.ui.theme.AppPalette = com.example.ui.theme.AppPalette.CYBER_CYAN
) {
    /**
     * Regla crítica de seguridad OT: si `distanciaCm <= 15` o `frenoActivo == true`,
     * se activa la alerta roja `"⚠️ FRENO ACTIVO"` y se bloquea automáticamente el botón `"ADELANTE"`.
     */
    val isCriticalBrakeAlert: Boolean
        get() = distanciaCm <= VehicleTelemetry.CRITICAL_DISTANCE_CM || frenoActivo

    val isForwardButtonEnabled: Boolean
        get() = !isCriticalBrakeAlert
}

/**
 * ViewModel principal resiliente a rotación de pantalla (`AndroidViewModel` + `viewModelScope`).
 */
class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_TELEMETRY_HISTORY = 35
        private const val MAX_SYSTEM_LOGS = 50
        private const val KEY_SELECTED_PALETTE = "ot_selected_palette_id"
    }

    private val cryptoManager = CryptoManager(application)
    private val authRepository = AuthRepository(cryptoManager)

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    private val esp32WifiClient = Esp32WifiClient(
        externalScope = viewModelScope,
        onStateChanged = ::handleConnectionStateChange,
        onTelemetryReceived = ::handleIncomingTelemetry,
        onSystemLog = ::appendSystemLog
    )

    private val bluetoothManager = BluetoothManager(
        context = application,
        externalScope = viewModelScope,
        onStateChanged = ::handleConnectionStateChange,
        onTelemetryReceived = ::handleIncomingTelemetry,
        onSystemLog = ::appendSystemLog
    )

    init {
        val restoredSession = authRepository.isSessionAuthenticated()
        val audit = authRepository.getSecurityAuditInfo()
        val savedPaletteId = cryptoManager.encryptedPrefs.getString(
            KEY_SELECTED_PALETTE,
            com.example.ui.theme.AppPalette.CYBER_CYAN.id
        )
        val initialPalette = com.example.ui.theme.AppPalette.fromId(savedPaletteId)

        _uiState.update { state ->
            state.copy(
                isAuthenticated = restoredSession,
                securityAuditInfo = audit,
                registeredUsernames = authRepository.getRegisteredUsernames(),
                selectedPalette = initialPalette,
                localDeviceIps = esp32WifiClient.getDeviceLocalIpAddresses(),
                isBtHardwareSupported = bluetoothManager.isHardwareSupported(),
                isBtEnabled = bluetoothManager.isBluetoothEnabled(),
                pairedBtDevices = bluetoothManager.getPairedDevices()
            )
        }
        if (restoredSession) {
            appendSystemLog("Sesión cifrada ISO 27400 restaurada para '${audit.fullName}' (@${audit.operatorId}).")
        }
    }

    fun selectPalette(palette: com.example.ui.theme.AppPalette) {
        cryptoManager.encryptedPrefs.edit().putString(KEY_SELECTED_PALETTE, palette.id).apply()
        _uiState.update { it.copy(selectedPalette = palette) }
        appendSystemLog("Paleta de interfaz actualizada a: ${palette.displayName}")
    }

    // =========================================================================
    // 1. GESTIÓN DE CUENTAS, CREAR CUENTA, LOGIN Y CERRAR SESIÓN (ISO/IEC 27400)
    // =========================================================================

    fun loginOperator(username: String, password: String) {
        when (val result = authRepository.login(username, password)) {
            is AuthResult.Success -> {
                val audit = authRepository.getSecurityAuditInfo()
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        authErrorMessage = null,
                        authStatusMessage = result.message,
                        securityAuditInfo = audit,
                        registeredUsernames = authRepository.getRegisteredUsernames()
                    )
                }
                appendSystemLog("Sesión iniciada: ${result.account.fullName} (@${result.account.username}).")
                refreshBluetoothDevices()
            }

            is AuthResult.Error -> {
                val audit = authRepository.getSecurityAuditInfo()
                _uiState.update {
                    it.copy(
                        authErrorMessage = result.message,
                        authStatusMessage = null,
                        securityAuditInfo = audit
                    )
                }
            }
        }
    }

    fun registerNewAccount(
        fullName: String,
        username: String,
        role: String,
        password: String,
        confirmPassword: String
    ) {
        when (val result = authRepository.registerAccount(fullName, username, role, password, confirmPassword)) {
            is AuthResult.Success -> {
                val audit = authRepository.getSecurityAuditInfo()
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        authErrorMessage = null,
                        authStatusMessage = result.message,
                        securityAuditInfo = audit,
                        registeredUsernames = authRepository.getRegisteredUsernames()
                    )
                }
                appendSystemLog(
                    "Nueva cuenta creada y cifrada en EncryptedSharedPreferences: ${result.account.fullName} (@${result.account.username})."
                )
                refreshBluetoothDevices()
            }

            is AuthResult.Error -> {
                _uiState.update {
                    it.copy(
                        authErrorMessage = result.message,
                        authStatusMessage = null
                    )
                }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmNewPassword: String) {
        when (val result = authRepository.changePasswordForActiveUser(currentPassword, newPassword, confirmNewPassword)) {
            is AuthResult.Success -> {
                val audit = authRepository.getSecurityAuditInfo()
                _uiState.update {
                    it.copy(
                        securityAuditInfo = audit,
                        passwordChangeMessage = result.message,
                        passwordChangeError = null
                    )
                }
                appendSystemLog("Contraseña actualizada con nuevo Salt SHA-256 para @${result.account.username}.")
            }

            is AuthResult.Error -> {
                _uiState.update {
                    it.copy(
                        passwordChangeError = result.message,
                        passwordChangeMessage = null
                    )
                }
            }
        }
    }

    fun clearPasswordChangeFeedback() {
        _uiState.update {
            it.copy(passwordChangeMessage = null, passwordChangeError = null)
        }
    }

    fun clearAuthFeedback() {
        _uiState.update { it.copy(authErrorMessage = null, authStatusMessage = null) }
    }

    fun logoutOperator() {
        disconnectSecurely()
        authRepository.logout()
        val audit = authRepository.getSecurityAuditInfo()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                authErrorMessage = null,
                authStatusMessage = "Has cerrado sesión de forma segura. Tus datos siguen protegidos con AES-256.",
                securityAuditInfo = audit,
                registeredUsernames = authRepository.getRegisteredUsernames(),
                passwordChangeMessage = null,
                passwordChangeError = null
            )
        }
        appendSystemLog("Sesión cerrada por el usuario y enlaces inalámbricos desconectados.")
    }

    // =========================================================================
    // 2. GESTIÓN DE ENLACE WI-FI (ESP32) Y BLUETOOTH RFCOMM
    // =========================================================================

    fun selectTransportMode(mode: TransportMode) {
        if (_uiState.value.selectedTransport == mode) return
        disconnectSecurely()
        _uiState.update { it.copy(selectedTransport = mode) }
        if (mode == TransportMode.BLUETOOTH_RFCOMM) {
            refreshBluetoothDevices()
        }
        appendSystemLog("Modo de enlace cambiado a: ${mode.displayName}")
    }

    fun updateEsp32Ip(ip: String) {
        _uiState.update { it.copy(esp32IpInput = ip) }
    }

    fun updateEsp32Port(portText: String) {
        val clean = portText.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(esp32PortInput = clean) }
    }

    fun applyPresetEsp32Ap() {
        _uiState.update {
            it.copy(
                esp32IpInput = Esp32WifiClient.DEFAULT_ESP32_IP,
                esp32PortInput = Esp32WifiClient.DEFAULT_ESP32_PORT.toString()
            )
        }
    }

    fun connectWifiEsp32() {
        val host = _uiState.value.esp32IpInput.trim()
        val port = _uiState.value.esp32PortInput.toIntOrNull()?.coerceIn(1, 65535)
            ?: Esp32WifiClient.DEFAULT_ESP32_PORT
        esp32WifiClient.connectAndPollTelemetry(host, port)
    }

    fun toggleEmbeddedEsp32Simulator() {
        if (esp32WifiClient.isEmbeddedSimulatorRunning()) {
            esp32WifiClient.disconnect()
            _uiState.update {
                it.copy(
                    isLocalEmbeddedSimRunning = false,
                    localDeviceIps = esp32WifiClient.getDeviceLocalIpAddresses()
                )
            }
        } else {
            val localIps = esp32WifiClient.getDeviceLocalIpAddresses()
            _uiState.update {
                it.copy(
                    selectedTransport = TransportMode.WIFI_ESP32,
                    esp32IpInput = Esp32WifiClient.SIMULATOR_LOCAL_IP,
                    esp32PortInput = Esp32WifiClient.SIMULATOR_LOCAL_PORT.toString(),
                    isLocalEmbeddedSimRunning = true,
                    localDeviceIps = localIps
                )
            }
            esp32WifiClient.startEmbeddedEsp32SimulatorAndConnect()
        }
    }

    fun injectSimulatedDistance(distanceCm: Int) {
        esp32WifiClient.setSimulatedObstacleDistance(distanceCm)
        bluetoothManager.setSimulatedObstacleDistance(distanceCm)
        if (esp32WifiClient.isEmbeddedSimulatorRunning()) {
            appendSystemLog("Distancia ajustada a $distanceCm cm en el nodo emisor.")
        } else {
            val freno = distanceCm <= VehicleTelemetry.CRITICAL_DISTANCE_CM
            val nuevoMotor = if (freno && _uiState.value.estadoMotor == VehicleTelemetry.MOTOR_AVANZANDO) {
                VehicleTelemetry.MOTOR_DETENIDO
            } else {
                _uiState.value.estadoMotor
            }
            handleIncomingTelemetry(
                VehicleTelemetry(
                    distancia = distanceCm,
                    estadoMotor = nuevoMotor,
                    frenoActivo = freno,
                    rawJson = "{\"distancia\": $distanceCm, \"estadoMotor\": \"$nuevoMotor\", \"frenoActivo\": $freno}",
                    sourceTransport = "Prueba Manual",
                    sequenceNumber = (_uiState.value.totalPacketsReceived + 1).toLong()
                )
            )
        }
    }

    fun refreshBluetoothDevices() {
        _uiState.update {
            it.copy(
                localDeviceIps = esp32WifiClient.getDeviceLocalIpAddresses(),
                isBtHardwareSupported = bluetoothManager.isHardwareSupported(),
                isBtEnabled = bluetoothManager.isBluetoothEnabled(),
                pairedBtDevices = bluetoothManager.getPairedDevices()
            )
        }
    }

    fun updateManualBtMac(mac: String) {
        _uiState.update { it.copy(manualBtMacInput = mac.uppercase(Locale.ROOT)) }
    }

    fun connectBluetoothDevice(macAddress: String) {
        bluetoothManager.connectToDevice(macAddress.trim())
    }

    fun startBluetoothRfcommServer() {
        bluetoothManager.startRfcommServer()
    }

    // =========================================================================
    // 3. TELEOPERACIÓN DEL VEHÍCULO (/adelante, /parar, /atras)
    // =========================================================================

    fun sendVehicleCommand(command: VehicleCommand) {
        val currentState = _uiState.value
        if (command == VehicleCommand.ADELANTE && currentState.isCriticalBrakeAlert) {
            appendSystemLog(
                "⚠️ SEGURIDAD OT: Orden ADELANTE bloqueada (distancia = ${currentState.distanciaCm} cm <= 15 cm)."
            )
            return
        }

        _uiState.update {
            it.copy(
                lastCommandDispatched = "${command.displayLabel} (${command.endpointPath})",
                estadoMotor = command.expectedMotorState
            )
        }

        when (currentState.selectedTransport) {
            TransportMode.WIFI_ESP32 -> {
                esp32WifiClient.sendVehicleCommand(command, currentState.distanciaCm)
            }
            TransportMode.BLUETOOTH_RFCOMM -> {
                bluetoothManager.sendVehicleCommand(command, currentState.distanciaCm)
            }
        }
    }

    fun disconnectSecurely() {
        esp32WifiClient.disconnect()
        bluetoothManager.disconnect()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                estadoMotor = VehicleTelemetry.MOTOR_DETENIDO,
                isLocalEmbeddedSimRunning = false
            )
        }
    }

    fun clearLogs() {
        _uiState.update {
            it.copy(
                telemetryHistory = emptyList(),
                systemEventLog = emptyList(),
                totalPacketsReceived = 0,
                totalEmergencyBrakesTriggered = 0
            )
        }
    }

    // =========================================================================
    // 4. RECEPCIÓN THREAD-SAFE DE TELEMETRÍA Y ESTADOS
    // =========================================================================

    private fun handleConnectionStateChange(newState: ConnectionState) {
        _uiState.update { state ->
            state.copy(
                connectionState = newState,
                isLocalEmbeddedSimRunning = esp32WifiClient.isEmbeddedSimulatorRunning()
            )
        }
    }

    private fun handleIncomingTelemetry(telemetry: VehicleTelemetry) {
        _uiState.update { state ->
            val updatedHistory = (listOf(telemetry) + state.telemetryHistory).take(MAX_TELEMETRY_HISTORY)
            val newTotal = state.totalPacketsReceived + 1
            val brakeJustActivated = telemetry.isEmergencyBrakeActive && !state.isCriticalBrakeAlert
            val newBrakesCount = state.totalEmergencyBrakesTriggered + if (brakeJustActivated) 1 else 0

            state.copy(
                distanciaCm = telemetry.distancia,
                estadoMotor = telemetry.estadoMotor,
                frenoActivo = telemetry.isEmergencyBrakeActive,
                hasReceivedFirstPacket = true,
                telemetryHistory = updatedHistory,
                totalPacketsReceived = newTotal,
                totalEmergencyBrakesTriggered = newBrakesCount,
                isLocalEmbeddedSimRunning = esp32WifiClient.isEmbeddedSimulatorRunning()
            )
        }
    }

    private fun appendSystemLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$timeStr] $message"
        _uiState.update { state ->
            state.copy(
                systemEventLog = (listOf(line) + state.systemEventLog).take(MAX_SYSTEM_LOGS)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        esp32WifiClient.disconnect()
        bluetoothManager.disconnect()
    }
}
