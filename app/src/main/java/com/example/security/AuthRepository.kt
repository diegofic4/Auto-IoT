package com.example.security

import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Perfil de cuenta de operador almacenado de forma cifrada en [EncryptedSharedPreferences].
 * Cumple con ISO/IEC 27400: cero contraseñas en texto plano; solo se guarda `saltBase64` y `passwordHashSha256`.
 */
data class OperatorAccount(
    val username: String,
    val fullName: String,
    val role: String,
    val saltBase64: String,
    val passwordHashSha256: String,
    val createdAtEpochSec: Long
)

/**
 * Estado de auditoría criptográfica y perfil activo expuesto a la UI.
 */
data class SecurityAuditInfo(
    val operatorId: String,
    val fullName: String,
    val role: String,
    val saltPreviewBase64: String,
    val passwordHashSha256: String,
    val isSessionActive: Boolean,
    val failedAttempts: Int,
    val lastAuthEpochSeconds: Long,
    val registeredAccountsCount: Int
)

sealed class AuthResult {
    data class Success(val account: OperatorAccount, val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Repositorio de Cuentas de Usuario, Registro ("Crear Cuenta"), Login y Sesión Cifrada (ISO/IEC 27400).
 *
 * Almacena todas las cuentas registradas en un arreglo JSON dentro de [EncryptedSharedPreferences]
 * (cifrado con AES-256 GCM), donde cada cuenta contiene únicamente su `saltBase64` de 16 bytes
 * y su hash `SHA-256`.
 */
class AuthRepository(private val cryptoManager: CryptoManager) {

    companion object {
        private const val KEY_ACCOUNTS_JSON = "ot_registered_accounts_json"
        private const val KEY_ACTIVE_USERNAME = "ot_active_username"
        private const val KEY_SESSION_ACTIVE = "ot_session_authenticated_flag"
        private const val KEY_FAILED_ATTEMPTS = "ot_failed_login_attempts"
        private const val KEY_LAST_AUTH_TIMESTAMP = "ot_last_auth_epoch_sec"

        // Cuenta predeterminada de demostración (se hashea en el primer arranque)
        const val DEFAULT_OPERATOR_ID = "operador_ot"
        const val DEFAULT_INITIAL_KEY = "Iso27400#OT"
        const val DEFAULT_FULL_NAME = "Operador Principal OT"
        const val DEFAULT_ROLE = "Supervisor de Teleoperación"
    }

    init {
        ensureDefaultAccountExists()
    }

    private fun ensureDefaultAccountExists() {
        val accounts = loadAllAccounts()
        if (accounts.isEmpty()) {
            val salt = cryptoManager.generateSaltBase64()
            val hash = cryptoManager.hashPasswordSha256(DEFAULT_INITIAL_KEY, salt)
            val defaultAccount = OperatorAccount(
                username = DEFAULT_OPERATOR_ID,
                fullName = DEFAULT_FULL_NAME,
                role = DEFAULT_ROLE,
                saltBase64 = salt,
                passwordHashSha256 = hash,
                createdAtEpochSec = System.currentTimeMillis() / 1000L
            )
            saveAllAccounts(listOf(defaultAccount))
            cryptoManager.encryptedPrefs.edit {
                putString(KEY_ACTIVE_USERNAME, DEFAULT_OPERATOR_ID)
                putBoolean(KEY_SESSION_ACTIVE, false)
                putInt(KEY_FAILED_ATTEMPTS, 0)
            }
        }
    }

    /**
     * Crea una nueva cuenta de usuario validando los campos, generando un Salt aleatorio de 16 bytes
     * y almacenando únicamente el hash SHA-256 en `EncryptedSharedPreferences`.
     */
    fun registerAccount(
        fullName: String,
        username: String,
        role: String,
        rawPassword: String,
        confirmPassword: String
    ): AuthResult {
        val cleanName = fullName.trim()
        val cleanUser = username.trim().lowercase()
        val cleanRole = role.trim().ifEmpty { "Operador de Inspección IoT" }

        if (cleanName.length < 3) {
            return AuthResult.Error("Por favor ingresa tu nombre completo (mínimo 3 caracteres).")
        }
        if (cleanUser.length < 3 || cleanUser.contains(" ")) {
            return AuthResult.Error("El nombre de usuario debe tener al menos 3 caracteres y no contener espacios.")
        }
        if (rawPassword.length < 6) {
            return AuthResult.Error("La contraseña debe tener al menos 6 caracteres por seguridad ISO 27400.")
        }
        if (rawPassword != confirmPassword) {
            return AuthResult.Error("Las contraseñas no coinciden. Verifícalas e intenta nuevamente.")
        }

        val currentAccounts = loadAllAccounts().toMutableList()
        if (currentAccounts.any { it.username.equals(cleanUser, ignoreCase = true) }) {
            return AuthResult.Error("El usuario '$cleanUser' ya está registrado. Elige otro o inicia sesión.")
        }

        val saltBase64 = cryptoManager.generateSaltBase64()
        val hashHex = cryptoManager.hashPasswordSha256(rawPassword, saltBase64)
        val nowSec = System.currentTimeMillis() / 1000L

        val newAccount = OperatorAccount(
            username = cleanUser,
            fullName = cleanName,
            role = cleanRole,
            saltBase64 = saltBase64,
            passwordHashSha256 = hashHex,
            createdAtEpochSec = nowSec
        )

        currentAccounts.add(newAccount)
        saveAllAccounts(currentAccounts)

        cryptoManager.encryptedPrefs.edit {
            putString(KEY_ACTIVE_USERNAME, newAccount.username)
            putBoolean(KEY_SESSION_ACTIVE, true)
            putInt(KEY_FAILED_ATTEMPTS, 0)
            putLong(KEY_LAST_AUTH_TIMESTAMP, nowSec)
        }

        return AuthResult.Success(
            account = newAccount,
            message = "¡Cuenta creada exitosamente para $cleanName! Sesión iniciada con cifrado AES-256."
        )
    }

    /**
     * Inicia sesión verificando la contraseña ingresada con SHA-256 + el salt de la cuenta.
     */
    fun login(username: String, rawPassword: String): AuthResult {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || rawPassword.isEmpty()) {
            return AuthResult.Error("Por favor ingresa tu usuario y contraseña para continuar.")
        }

        val prefs = cryptoManager.encryptedPrefs
        val accounts = loadAllAccounts()
        val targetAccount = accounts.find { it.username.equals(cleanUser, ignoreCase = true) }

        if (targetAccount == null) {
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            prefs.edit { putInt(KEY_FAILED_ATTEMPTS, attempts) }
            return AuthResult.Error(
                "No encontramos la cuenta '$cleanUser'. Puedes crear una nueva en la pestaña 'Crear Cuenta'."
            )
        }

        val isPasswordValid = cryptoManager.verifyPassword(
            inputPassword = rawPassword,
            storedSaltBase64 = targetAccount.saltBase64,
            storedHashHex = targetAccount.passwordHashSha256
        )

        return if (isPasswordValid) {
            val nowEpoch = System.currentTimeMillis() / 1000L
            prefs.edit {
                putString(KEY_ACTIVE_USERNAME, targetAccount.username)
                putBoolean(KEY_SESSION_ACTIVE, true)
                putInt(KEY_FAILED_ATTEMPTS, 0)
                putLong(KEY_LAST_AUTH_TIMESTAMP, nowEpoch)
            }
            AuthResult.Success(
                account = targetAccount,
                message = "Bienvenido(a), ${targetAccount.fullName}."
            )
        } else {
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            prefs.edit { putInt(KEY_FAILED_ATTEMPTS, attempts) }
            AuthResult.Error(
                "Contraseña incorrecta para '${targetAccount.username}'. (Intento fallido #$attempts)"
            )
        }
    }

    /**
     * Permite al usuario con sesión activa cambiar su contraseña generando un nuevo Salt y Hash SHA-256.
     */
    fun changePasswordForActiveUser(currentPassword: String, newPassword: String, confirmNewPassword: String): AuthResult {
        val activeUser = getActiveAccount()
            ?: return AuthResult.Error("No hay una cuenta activa seleccionada.")

        if (!cryptoManager.verifyPassword(currentPassword, activeUser.saltBase64, activeUser.passwordHashSha256)) {
            return AuthResult.Error("La contraseña actual ingresada no es correcta.")
        }
        if (newPassword.length < 6) {
            return AuthResult.Error("La nueva contraseña debe tener al menos 6 caracteres.")
        }
        if (newPassword != confirmNewPassword) {
            return AuthResult.Error("La confirmación de la nueva contraseña no coincide.")
        }

        val newSalt = cryptoManager.generateSaltBase64()
        val newHash = cryptoManager.hashPasswordSha256(newPassword, newSalt)
        val updatedAccount = activeUser.copy(
            saltBase64 = newSalt,
            passwordHashSha256 = newHash
        )

        val updatedList = loadAllAccounts().map {
            if (it.username.equals(activeUser.username, ignoreCase = true)) updatedAccount else it
        }
        saveAllAccounts(updatedList)

        return AuthResult.Success(
            account = updatedAccount,
            message = "Contraseña actualizada y cifrada con un nuevo Salt SHA-256."
        )
    }

    fun isSessionAuthenticated(): Boolean {
        return cryptoManager.encryptedPrefs.getBoolean(KEY_SESSION_ACTIVE, false)
    }

    fun logout() {
        cryptoManager.encryptedPrefs.edit {
            putBoolean(KEY_SESSION_ACTIVE, false)
        }
    }

    fun getRegisteredUsernames(): List<String> {
        return loadAllAccounts().map { it.username }
    }

    fun getActiveAccount(): OperatorAccount? {
        val accounts = loadAllAccounts()
        val activeUsername = cryptoManager.encryptedPrefs.getString(KEY_ACTIVE_USERNAME, DEFAULT_OPERATOR_ID)
        return accounts.find { it.username.equals(activeUsername, ignoreCase = true) }
            ?: accounts.firstOrNull()
    }

    fun getSecurityAuditInfo(): SecurityAuditInfo {
        val prefs = cryptoManager.encryptedPrefs
        val accounts = loadAllAccounts()
        val active = getActiveAccount()
        return SecurityAuditInfo(
            operatorId = active?.username ?: DEFAULT_OPERATOR_ID,
            fullName = active?.fullName ?: DEFAULT_FULL_NAME,
            role = active?.role ?: DEFAULT_ROLE,
            saltPreviewBase64 = active?.saltBase64 ?: "",
            passwordHashSha256 = active?.passwordHashSha256 ?: "",
            isSessionActive = prefs.getBoolean(KEY_SESSION_ACTIVE, false),
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            lastAuthEpochSeconds = prefs.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L),
            registeredAccountsCount = accounts.size
        )
    }

    private fun loadAllAccounts(): List<OperatorAccount> {
        val rawJson = cryptoManager.encryptedPrefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<OperatorAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    OperatorAccount(
                        username = obj.optString("username", DEFAULT_OPERATOR_ID),
                        fullName = obj.optString("fullName", DEFAULT_FULL_NAME),
                        role = obj.optString("role", DEFAULT_ROLE),
                        saltBase64 = obj.optString("saltBase64", ""),
                        passwordHashSha256 = obj.optString("passwordHashSha256", ""),
                        createdAtEpochSec = obj.optLong("createdAtEpochSec", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllAccounts(accounts: List<OperatorAccount>) {
        val arr = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("username", acc.username)
                put("fullName", acc.fullName)
                put("role", acc.role)
                put("saltBase64", acc.saltBase64)
                put("passwordHashSha256", acc.passwordHashSha256)
                put("createdAtEpochSec", acc.createdAtEpochSec)
            }
            arr.put(obj)
        }
        cryptoManager.encryptedPrefs.edit {
            putString(KEY_ACCOUNTS_JSON, arr.toString())
        }
    }
}
