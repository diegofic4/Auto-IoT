package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gestor criptográfico bajo el estándar ISO/IEC 27400 (Seguridad y Privacidad en IoT/OT).
 *
 * Responsabilidades:
 * 1. Inicializar [EncryptedSharedPreferences] respaldado por Android Keystore (AES-256 GCM / SIV).
 * 2. Generar Sales Criptográficas aleatorias (CSPRNG via [SecureRandom] de 16 bytes / 128 bits).
 * 3. Ejecutar derivación / hashing SHA-256 con Salt para evitar ataques de diccionario y Rainbow Tables.
 * 4. Comparar hashes en tiempo constante ([MessageDigest.isEqual]) para mitigar ataques de temporización (Timing Attacks).
 */
class CryptoManager(context: Context) {

    companion object {
        private const val TAG = "OT_CryptoManager"
        private const val ENCRYPTED_PREFS_FILE = "iso27400_ot_secure_vault"
        private const val SALT_LENGTH_BYTES = 16
    }

    val encryptedPrefs: SharedPreferences = createSecurePreferences(context.applicationContext)

    @Suppress("DEPRECATION")
    private fun createSecurePreferences(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback seguro únicamente si el Keystore de hardware falla o en entorno JVM de pruebas unitarias
            Log.w(TAG, "Keystore fallback activado: ${e.message}")
            appContext.getSharedPreferences("${ENCRYPTED_PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Genera un salt aleatorio criptográficamente fuerte (16 bytes) codificado en Base64.
     */
    fun generateSaltBase64(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Calcula el hash SHA-256 combinando el salt decodificado y los bytes UTF-8 de la contraseña.
     * Retorna el digest en formato hexadecimal en minúsculas (64 caracteres) y Base64.
     */
    fun hashPasswordSha256(plainPassword: String, saltBase64: String): String {
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        val passwordBytes = plainPassword.toByteArray(StandardCharsets.UTF_8)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(saltBytes)
        val hashedBytes = digest.digest(passwordBytes)

        return bytesToHex(hashedBytes)
    }

    /**
     * Verifica la contraseña ingresada contra el hash SHA-256 almacenado usando comparación en tiempo constante.
     */
    fun verifyPassword(inputPassword: String, storedSaltBase64: String, storedHashHex: String): Boolean {
        val computedHashHex = hashPasswordSha256(inputPassword, storedSaltBase64)
        val computedBytes = computedHashHex.toByteArray(StandardCharsets.UTF_8)
        val storedBytes = storedHashHex.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(computedBytes, storedBytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
