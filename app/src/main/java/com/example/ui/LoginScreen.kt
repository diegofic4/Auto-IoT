package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.security.AuthRepository
import com.example.security.SecurityAuditInfo
import com.example.ui.theme.AppPalette
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.StatusAlertBg
import com.example.ui.theme.StatusAlertCrimson
import com.example.ui.theme.StatusSafeBg
import com.example.ui.theme.StatusSafeGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Pantalla Amigable de Inicio de Sesión y Creación de Cuenta (ISO/IEC 27400).
 *
 * Incluye:
 * - Selector claro por pestañas entre **"Iniciar Sesión"** y **"Crear Cuenta"**.
 * - Selección rápida de usuarios registrados y botón de **Acceso Rápido Demo** con 1 toque.
 * - Formulario completo de **Crear Cuenta** (Nombre completo, Usuario, Rol operativo, Contraseña con medidor visual de fortaleza y Confirmar Contraseña).
 * - Selector de Paleta Visual (6 temas industriales y modo diurno).
 * - Inspector desplegable de Seguridad Criptográfica ISO 27400 (`EncryptedSharedPreferences` + `SHA-256 con Salt`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    auditInfo: SecurityAuditInfo?,
    registeredUsernames: List<String>,
    errorMessage: String?,
    statusMessage: String?,
    onLoginSubmit: (String, String) -> Unit,
    onRegisterSubmit: (String, String, String, String, String) -> Unit,
    onDismissFeedback: () -> Unit,
    selectedPalette: AppPalette = AppPalette.CYBER_CYAN,
    onSelectPalette: (AppPalette) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val CyberBackground = palette.background
    val CyberSurface = palette.surface
    val CyberSurfaceVariant = palette.surfaceVariant
    val CyberCardBorder = palette.cardBorder
    val CyberCyan = palette.primary
    val CyberTeal = palette.secondary
    val TextPrimary = palette.textPrimary
    val TextSecondary = palette.textSecondary
    val TextMuted = palette.textMuted

    var isRegisterTab by rememberSaveable { mutableStateOf(false) }

    // Campos de Iniciar Sesión
    var loginUsername by rememberSaveable { mutableStateOf(AuthRepository.DEFAULT_OPERATOR_ID) }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var showLoginPassword by rememberSaveable { mutableStateOf(false) }

    // Campos de Crear Cuenta
    var regFullName by rememberSaveable { mutableStateOf("") }
    var regUsername by rememberSaveable { mutableStateOf("") }
    var regRole by rememberSaveable { mutableStateOf("Operador de Teleoperación") }
    var regPassword by rememberSaveable { mutableStateOf("") }
    var regConfirmPassword by rememberSaveable { mutableStateOf("") }
    var showRegPassword by rememberSaveable { mutableStateOf(false) }

    var showSecurityDetails by rememberSaveable { mutableStateOf(false) }

    val availableRoles = listOf(
        "Operador de Teleoperación",
        "Supervisor OT",
        "Inspector de Seguridad"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.background, palette.surface, palette.background)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barra Superior con Insignia ISO y Botón Desplegable de Paleta de Colores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = palette.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, palette.secondary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ISO/IEC 27400",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.secondary
                        )
                    }
                }

                // Botón / Icono desplegable de Paleta de Colores
                PalettePopupButton(
                    selectedPalette = selectedPalette,
                    onSelectPalette = onSelectPalette
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Icono Principal Amigable del Vehículo IoT
            Surface(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape),
                color = palette.surfaceVariant,
                border = BorderStroke(2.dp, palette.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Vehículo de Inspección IoT",
                        tint = palette.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Auto IoT Control • ESP32",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Control Remoto para Vehículo ESP32 y Celular a Celular",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta Principal con Pestañas: "Iniciar Sesión" | "Crear Cuenta"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Selector de Pestañas Amigable
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = CyberBackground,
                        border = BorderStroke(1.dp, CyberCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuthTabButton(
                                title = "Iniciar Sesión",
                                icon = Icons.Default.Login,
                                isSelected = !isRegisterTab,
                                onClick = {
                                    isRegisterTab = false
                                    onDismissFeedback()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_login_button")
                            )

                            AuthTabButton(
                                title = "Crear Cuenta",
                                icon = Icons.Default.PersonAdd,
                                isSelected = isRegisterTab,
                                onClick = {
                                    isRegisterTab = true
                                    onDismissFeedback()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_register_button")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isRegisterTab) {
                        // =========================================================
                        // PESTAÑA 1: INICIAR SESIÓN
                        // =========================================================
                        Text(
                            text = "¡Bienvenido! Ingresa con tu cuenta de operador",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tu sesión se guardará de forma segura para que no tengas que volver a escribir tu clave al abrir la app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        if (registeredUsernames.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cuentas guardadas en este dispositivo (toca para elegir):",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberTeal
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                registeredUsernames.forEach { user ->
                                    FilterChip(
                                        selected = loginUsername.equals(user, ignoreCase = true),
                                        onClick = {
                                            loginUsername = user
                                            onDismissFeedback()
                                        },
                                        label = { Text("@$user") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = CyberCyan
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = {
                                loginUsername = it
                                onDismissFeedback()
                            },
                            label = { Text("Usuario o ID de Operador") },
                            placeholder = { Text("Ej. operador_ot") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberCyan,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("operator_id_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = {
                                loginPassword = it
                                onDismissFeedback()
                            },
                            label = { Text("Contraseña") },
                            placeholder = { Text("Ingresa tu contraseña") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showLoginPassword = !showLoginPassword },
                                    modifier = Modifier.testTag("toggle_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showLoginPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (showLoginPassword) {
                                            "Ocultar contraseña"
                                        } else {
                                            "Mostrar contraseña"
                                        },
                                        tint = TextSecondary
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showLoginPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberCyan,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        AuthFeedbackBanners(
                            errorMessage = errorMessage,
                            statusMessage = statusMessage
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onLoginSubmit(loginUsername, loginPassword) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Iniciar Sesión",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Botón amigable de 1 toque para usar la cuenta demo en evaluación
                        OutlinedButton(
                            onClick = {
                                loginUsername = AuthRepository.DEFAULT_OPERATOR_ID
                                loginPassword = AuthRepository.DEFAULT_INITIAL_KEY
                                onLoginSubmit(
                                    AuthRepository.DEFAULT_OPERATOR_ID,
                                    AuthRepository.DEFAULT_INITIAL_KEY
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("quick_demo_login_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CyberTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Entrar con Cuenta Demo (operador_ot)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberTeal
                            )
                        }
                    } else {
                        // =========================================================
                        // PESTAÑA 2: CREAR CUENTA NUEVA
                        // =========================================================
                        Text(
                            text = "Crear Nueva Cuenta de Operador",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tu contraseña nunca se guarda en texto plano: se protege automáticamente con un Salt único y hash SHA-256 en EncryptedSharedPreferences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regFullName,
                            onValueChange = {
                                regFullName = it
                                onDismissFeedback()
                            },
                            label = { Text("Nombre Completo") },
                            placeholder = { Text("Ej. Diego Fernández") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_fullname_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = {
                                regUsername = it.trim()
                                onDismissFeedback()
                            },
                            label = { Text("Nombre de Usuario (sin espacios)") },
                            placeholder = { Text("Ej. diego_ot") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_username_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Selecciona tu Rol en la Estación:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableRoles.forEach { roleOption ->
                                FilterChip(
                                    selected = regRole == roleOption,
                                    onClick = { regRole = roleOption },
                                    label = { Text(roleOption) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.WorkOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                onDismissFeedback()
                            },
                            label = { Text("Contraseña (mínimo 6 caracteres)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                    Icon(
                                        imageVector = if (showRegPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showRegPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input")
                        )

                        // Indicador visual amigable de fortaleza de contraseña
                        if (regPassword.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            PasswordStrengthIndicator(password = regPassword)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = {
                                regConfirmPassword = it
                                onDismissFeedback()
                            },
                            label = { Text("Confirmar Contraseña") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = CyberCyan
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (showRegPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_confirm_password_input")
                        )

                        AuthFeedbackBanners(
                            errorMessage = errorMessage,
                            statusMessage = statusMessage
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                onRegisterSubmit(
                                    regFullName,
                                    regUsername,
                                    regRole,
                                    regPassword,
                                    regConfirmPassword
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusSafeGreen,
                                contentColor = CyberBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("register_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Crear Cuenta e Iniciar Sesión",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tarjeta Desplegable de Auditoría Criptográfica ISO/IEC 27400
            if (auditInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSecurityDetails = !showSecurityDetails }
                        .testTag("crypto_audit_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.65f)),
                    border = BorderStroke(1.dp, CyberCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = CyberTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Verificación de Seguridad ISO/IEC 27400",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyberTeal
                                    )
                                    Text(
                                        text = "${auditInfo.registeredAccountsCount} cuenta(s) cifrada(s) • Cero texto plano",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showSecurityDetails) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = "Ver detalles técnicos",
                                tint = CyberTeal
                            )
                        }

                        AnimatedVisibility(visible = showSecurityDetails) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = CyberCardBorder)
                                Spacer(modifier = Modifier.height(8.dp))
                                AuditRow("Bóveda:", "EncryptedSharedPreferences (AES256_GCM)")
                                AuditRow("Cuenta Activa:", "${auditInfo.fullName} (@${auditInfo.operatorId})")
                                AuditRow("Rol:", auditInfo.role)
                                AuditRow("Salt (Base64):", auditInfo.saltPreviewBase64.ifEmpty { "N/A" })
                                AuditRow(
                                    "Hash SHA-256:",
                                    if (auditInfo.passwordHashSha256.length >= 24) {
                                        "${auditInfo.passwordHashSha256.take(16)}...${auditInfo.passwordHashSha256.takeLast(12)}"
                                    } else {
                                        auditInfo.passwordHashSha256
                                    }
                                )
                                AuditRow("Texto Plano:", "0 bytes (Cumplimiento total ISO 27400)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) palette.primary else palette.background,
        contentColor = if (isSelected) palette.background else palette.textSecondary
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(password: String) {
    val palette = LocalAppPalette.current
    val hasUpper = password.any { it.isUpperCase() }
    val hasDigitOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }
    val score = when {
        password.length < 6 -> 1
        password.length >= 8 && hasUpper && hasDigitOrSymbol -> 3
        else -> 2
    }
    val (label, color, progress) = when (score) {
        1 -> Triple("Débil (mínimo 6 caracteres)", palette.statusAlert, 0.33f)
        2 -> Triple("Buena (recomendado incluir mayúsculas y números)", palette.statusWarning, 0.66f)
        else -> Triple("Fuerte (Óptima para ISO/IEC 27400)", palette.statusSafe, 1.0f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = palette.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Seguridad de clave: $label",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun AuthFeedbackBanners(
    errorMessage: String?,
    statusMessage: String?
) {
    val palette = LocalAppPalette.current
    AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("login_error_banner"),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = palette.statusAlertBg),
            border = BorderStroke(1.dp, palette.statusAlert)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Aviso",
                    tint = palette.statusAlert,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
            }
        }
    }

    AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = palette.statusSafeBg),
            border = BorderStroke(1.dp, palette.statusSafe)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = palette.statusSafe,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.statusSafe
                )
            }
        }
    }
}

@Composable
private fun AuditRow(label: String, value: String) {
    val palette = LocalAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textPrimary,
            modifier = Modifier.weight(0.62f)
        )
    }
}
