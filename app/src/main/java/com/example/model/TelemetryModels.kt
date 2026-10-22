package com.example.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Modelo formal de Telemetría del Vehículo de Inspección IoT (ESP32 + HC-SR04 + L298N + LED).
 *
 * Estructura JSON entregada por el endpoint `/telemetria` y por el canal Bluetooth RFCOMM:
 * `{"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}`
 */
data class VehicleTelemetry(
    @SerializedName("distancia")
    val distancia: Int = 100,

    @SerializedName("estadoMotor")
    val estadoMotor: String = MOTOR_DETENIDO,

    @SerializedName("frenoActivo")
    val frenoActivo: Boolean = false,

    // Metadatos locales de recepción en la estación Android
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val rawJson: String = "",
    val sourceTransport: String = "Wi-Fi HTTP",
    val sequenceNumber: Long = 0L
) {
    companion object {
        const val MOTOR_DETENIDO = "DETENIDO"
        const val MOTOR_AVANZANDO = "AVANZANDO"
        const val MOTOR_RETROCEDIENDO = "RETROCEDIENDO"
        const val MOTOR_GIRANDO_IZQUIERDA = "GIRANDO_IZQUIERDA"
        const val MOTOR_GIRANDO_DERECHA = "GIRANDO_DERECHA"
        const val CRITICAL_DISTANCE_CM = 15
    }

    /**
     * Condición de seguridad física OT: Freno de emergencia activo si `distancia <= 15` cm
     * o si el microcontrolador ESP32 reporta `frenoActivo == true`.
     */
    val isEmergencyBrakeActive: Boolean
        get() = distancia <= CRITICAL_DISTANCE_CM || frenoActivo

    /**
     * Nivel normalizado [0.0f .. 1.0f] para la barra de proximidad (rango de referencia 0 a 150 cm).
     */
    val normalizedProximityLevel: Float
        get() = (distancia.toFloat() / 150.0f).coerceIn(0.03f, 1.0f)

    val proximityZone: ProximityZone
        get() = when {
            isEmergencyBrakeActive -> ProximityZone.CRITICAL_BRAKE
            distancia <= 40 -> ProximityZone.CAUTION
            else -> ProximityZone.CLEAR
        }
}

enum class ProximityZone(val label: String) {
    CRITICAL_BRAKE("⚠️ FRENO ACTIVO (<= 15 cm)"),
    CAUTION("PRECAUCIÓN (16 - 40 cm)"),
    CLEAR("VÍA DESPEJADA (> 40 cm)")
}

/**
 * Comandos de teleoperación del Puente H L298N del vehículo ESP32 (Avance, Giro Izquierda/Derecha, Parada y Reversa).
 */
enum class VehicleCommand(
    val endpointPath: String,
    val btCommandToken: String,
    val expectedMotorState: String,
    val displayLabel: String
) {
    ADELANTE("/adelante", "ADELANTE", VehicleTelemetry.MOTOR_AVANZANDO, "ADELANTE"),
    IZQUIERDA("/izquierda", "IZQUIERDA", VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA, "IZQUIERDA"),
    PARAR("/parar", "PARAR", VehicleTelemetry.MOTOR_DETENIDO, "PARAR / DETENER"),
    DERECHA("/derecha", "DERECHA", VehicleTelemetry.MOTOR_GIRANDO_DERECHA, "DERECHA"),
    ATRAS("/atras", "ATRAS", VehicleTelemetry.MOTOR_RETROCEDIENDO, "ATRÁS")
}

/**
 * Modos de enlace inalámbrico local exigidos por la rúbrica.
 */
enum class TransportMode(val displayName: String, val protocolSubtitle: String) {
    WIFI_ESP32("Wi-Fi Local (ESP32)", "AP Auto_IoT_Control • 192.168.4.1"),
    BLUETOOTH_RFCOMM("Bluetooth RFCOMM", "SPP Clásico • UUID 00001101")
}

/**
 * Estados del enlace inalámbrico en la interfaz OT.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val targetInfo: String) : ConnectionState()
    data class Connected(val endpointLabel: String, val transport: TransportMode) : ConnectionState()
    data class Error(
        val reason: String,
        val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ConnectionState()
}

/**
 * Dispositivo Bluetooth emparejado (Bonded Device) para selección RFCOMM SPP.
 */
data class PairedBluetoothDevice(
    val name: String,
    val macAddress: String
)

/**
 * Parser resiliente para el JSON del ESP32:
 * `{"distancia": 45, "estadoMotor": "DETENIDO", "frenoActivo": false}`
 */
object VehicleTelemetryParser {
    private val gson = Gson()

    fun parseJson(rawText: String, transportTag: String, seq: Long): Result<VehicleTelemetry> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Respuesta de telemetría vacía"))
        }
        return try {
            val jsonObj = JsonParser.parseString(trimmed).asJsonObject
            val distancia = when {
                jsonObj.has("distancia") -> jsonObj.get("distancia").asFloat.toInt()
                jsonObj.has("sensor_distancia_cm") -> jsonObj.get("sensor_distancia_cm").asFloat.toInt()
                else -> 100
            }.coerceIn(0, 500)

            val rawEstado = when {
                jsonObj.has("estadoMotor") -> jsonObj.get("estadoMotor").asString
                else -> VehicleTelemetry.MOTOR_DETENIDO
            }.uppercase().trim()

            val normalizedMotor = when {
                rawEstado.contains("AVANZ") || rawEstado.contains("ADELANTE") ->
                    VehicleTelemetry.MOTOR_AVANZANDO
                rawEstado.contains("IZQ") ->
                    VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA
                rawEstado.contains("DER") ->
                    VehicleTelemetry.MOTOR_GIRANDO_DERECHA
                rawEstado.contains("RETRO") || rawEstado.contains("ATRAS") || rawEstado.contains("ATRÁS") ->
                    VehicleTelemetry.MOTOR_RETROCEDIENDO
                else -> VehicleTelemetry.MOTOR_DETENIDO
            }

            val frenoReportado = when {
                jsonObj.has("frenoActivo") -> jsonObj.get("frenoActivo").asBoolean
                jsonObj.has("alerta") -> jsonObj.get("alerta").asBoolean
                else -> false
            }

            val frenoEfectivo = frenoReportado || (distancia <= VehicleTelemetry.CRITICAL_DISTANCE_CM)
            // Si el freno crítico se activa mientras avanzaba, el puente H L298N detiene el avance
            val estadoEfectivo = if (frenoEfectivo && normalizedMotor == VehicleTelemetry.MOTOR_AVANZANDO) {
                VehicleTelemetry.MOTOR_DETENIDO
            } else {
                normalizedMotor
            }

            Result.success(
                VehicleTelemetry(
                    distancia = distancia,
                    estadoMotor = estadoEfectivo,
                    frenoActivo = frenoEfectivo,
                    timestampEpochMs = System.currentTimeMillis(),
                    rawJson = gson.toJson(
                        mapOf(
                            "distancia" to distancia,
                            "estadoMotor" to estadoEfectivo,
                            "frenoActivo" to frenoEfectivo
                        )
                    ),
                    sourceTransport = transportTag,
                    sequenceNumber = seq
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("JSON inválido desde nodo ESP32: ${e.message}"))
        }
    }
}
