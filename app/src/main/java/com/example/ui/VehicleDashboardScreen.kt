package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionState
import com.example.model.PairedBluetoothDevice
import com.example.model.TransportMode
import com.example.model.VehicleCommand
import com.example.model.VehicleTelemetry
import com.example.security.SecurityAuditInfo
import com.example.ui.theme.AppPalette
import com.example.ui.theme.LocalAppPalette

/**
 * Pestañas principales del Dashboard para mantener la pantalla limpia, ordenada y fácil de usar.
 */
enum class DashboardSection(
    val title: String,
    val icon: ImageVector,
    val tag: String
) {
    CONTROL("Control y Sensor", Icons.Default.Speed, "nav_tab_control"),
    CONEXION("Conectar Dispositivos", Icons.Default.SettingsInputAntenna, "nav_tab_connect"),
    REGISTRO("Historial y Cuenta", Icons.Default.History, "nav_tab_logs")
}

/**
 * Pantalla Principal Ordenada por Secciones de Control OT y Teleoperación:
 * - Soportando conexión a **Vehículo ESP32**, **Celular a Celular (Wi-Fi / Hotspot o Bluetooth)** y **PC**.
 * - Botón / Icono superior desplegable para cambiar entre las **6 Paletas de Colores**.
 * - Letras grandes y de alto contraste para lectura inmediata.
 */
@Composable
fun VehicleDashboardScreen(
    uiState: VehicleUiState,
    onSelectTransport: (TransportMode) -> Unit,
    onIpChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onApplyDefaultEsp32Ip: () -> Unit,
    onConnectWifiEsp32: () -> Unit,
    onToggleEmbeddedSimulator: () -> Unit,
    onInjectTestDistance: (Int) -> Unit,
    onRefreshBluetooth: () -> Unit,
    onManualBtMacChanged: (String) -> Unit,
    onConnectBluetoothMac: (String) -> Unit,
    onStartBluetoothServer: () -> Unit,
    onSendCommand: (VehicleCommand) -> Unit,
    onDisconnectSecure: () -> Unit,
    onClearLogs: () -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onClearPasswordFeedback: () -> Unit,
    onLogout: () -> Unit,
    selectedPalette: AppPalette = AppPalette.CYBER_CYAN,
    onSelectPalette: (AppPalette) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    var currentSectionName by rememberSaveable { mutableStateOf(DashboardSection.CONTROL.name) }
    val currentSection = DashboardSection.entries.firstOrNull { it.name == currentSectionName }
        ?: DashboardSection.CONTROL
    val listState = rememberLazyListState()
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }

    // Si el usuario está en otra pestaña y presiona Atrás, vuelve a la pestaña principal de Control
    BackHandler(enabled = currentSection != DashboardSection.CONTROL) {
        currentSectionName = DashboardSection.CONTROL.name
    }

    // Diálogo de Perfil de Usuario y Cambio de Contraseña
    if (showAccountDialog) {
        UserProfileAndSecurityDialog(
            auditInfo = uiState.securityAuditInfo,
            passwordMessage = uiState.passwordChangeMessage,
            passwordError = uiState.passwordChangeError,
            onChangePassword = onChangePassword,
            selectedPalette = selectedPalette,
            onSelectPalette = onSelectPalette,
            onDismiss = {
                onClearPasswordFeedback()
                showAccountDialog = false
            },
            onRequestLogout = {
                showAccountDialog = false
                showLogoutConfirmDialog = true
            }
        )
    }

    // Diálogo de Confirmación Amigable para Cerrar Sesión
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = palette.statusAlert,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "¿Cerrar Sesión de Operador?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Cualquier conexión activa con el vehículo o celular se desconectará de forma segura y volverás a la pantalla de inicio.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.statusAlert,
                        contentColor = palette.textPrimary
                    ),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Sí, Cerrar Sesión", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancelar", color = palette.textSecondary)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                contentColor = palette.textPrimary,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                DashboardSection.entries.forEach { section ->
                    val selected = currentSection == section
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentSectionName = section.name },
                        icon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.background,
                            selectedTextColor = palette.primary,
                            indicatorColor = palette.primary,
                            unselectedIconColor = palette.textSecondary,
                            unselectedTextColor = palette.textSecondary
                        ),
                        modifier = Modifier.testTag(section.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabecera compacta siempre visible con Botón Desplegable de Tema, Mi Cuenta y Salir
            item {
                Spacer(modifier = Modifier.height(6.dp))
                VehicleTopHeader(
                    auditInfo = uiState.securityAuditInfo,
                    connectionState = uiState.connectionState,
                    selectedPalette = selectedPalette,
                    onSelectPalette = onSelectPalette,
                    onDisconnectSecure = onDisconnectSecure,
                    onOpenAccountDialog = {
                        onClearPasswordFeedback()
                        showAccountDialog = true
                    },
                    onRequestLogout = { showLogoutConfirmDialog = true }
                )
            }

            // Banner de Alerta Crítica "⚠️ FRENO ACTIVO" cuando distancia <= 15 cm o frenoActivo == true
            item {
                AnimatedVisibility(visible = uiState.isCriticalBrakeAlert) {
                    EmergencyBrakeBanner(distanciaCm = uiState.distanciaCm)
                }
            }

            when (currentSection) {
                DashboardSection.CONTROL -> {
                    // Tarjeta rápida de estado si aún no está conectado
                    if (uiState.connectionState is ConnectionState.Disconnected) {
                        item {
                            QuickStartBannerCard(
                                isSimulatorRunning = uiState.isLocalEmbeddedSimRunning,
                                onStartOneClickSimulator = onToggleEmbeddedSimulator,
                                onGoToConnectionTab = { currentSectionName = DashboardSection.CONEXION.name }
                            )
                        }
                    }

                    // Panel Principal de Telemetría Ultrasónica y Control de Motores
                    item {
                        LiveTelemetryAndMotorControlCard(
                            uiState = uiState,
                            onSendCommand = onSendCommand,
                            onDisconnectSecure = onDisconnectSecure,
                            onInjectTestDistance = onInjectTestDistance
                        )
                    }
                }

                DashboardSection.CONEXION -> {
                    // Selector de modo de enlace: Wi-Fi vs Bluetooth
                    item {
                        LinkModeSelectorCard(
                            selectedTransport = uiState.selectedTransport,
                            onSelectTransport = onSelectTransport
                        )
                    }

                    // Gestor de Permisos en Runtime con Pop-up Previo de Justificación (ISO 27400)
                    item {
                        InformedPermissionSection(
                            transportMode = uiState.selectedTransport,
                            onPermissionStateUpdated = { granted ->
                                if (granted && uiState.selectedTransport == TransportMode.BLUETOOTH_RFCOMM) {
                                    onRefreshBluetooth()
                                }
                            }
                        )
                    }

                    // Tarjeta Especial: Conectar de Celular a Celular o a ESP32
                    item {
                        WirelessEndpointConfigCard(
                            uiState = uiState,
                            onIpChanged = onIpChanged,
                            onPortChanged = onPortChanged,
                            onApplyDefaultEsp32Ip = onApplyDefaultEsp32Ip,
                            onConnectWifiEsp32 = {
                                onConnectWifiEsp32()
                                currentSectionName = DashboardSection.CONTROL.name
                            },
                            onToggleEmbeddedSimulator = onToggleEmbeddedSimulator,
                            onRefreshBluetooth = onRefreshBluetooth,
                            onManualBtMacChanged = onManualBtMacChanged,
                            onConnectBluetoothMac = { mac ->
                                onConnectBluetoothMac(mac)
                                currentSectionName = DashboardSection.CONTROL.name
                            },
                            onStartBluetoothServer = onStartBluetoothServer,
                            onDisconnectSecure = onDisconnectSecure
                        )
                    }
                }

                DashboardSection.REGISTRO -> {
                    // Resumen de Cuenta Activa y Seguridad ISO 27400
                    item {
                        AccountSummarySectionCard(
                            auditInfo = uiState.securityAuditInfo,
                            selectedPalette = selectedPalette,
                            onSelectPalette = onSelectPalette,
                            onOpenPasswordDialog = {
                                onClearPasswordFeedback()
                                showAccountDialog = true
                            },
                            onRequestLogout = { showLogoutConfirmDialog = true }
                        )
                    }

                    // Consola de paquetes JSON (/telemetria) y bitácora de eventos
                    item {
                        ProtocolAuditConsoleCard(
                            telemetryHistory = uiState.telemetryHistory,
                            systemLogs = uiState.systemEventLog,
                            onClearLogs = onClearLogs
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

/**
 * Cabecera limpia y ordenada con:
 * - Nombre del Operador activo
 * - Botón / Icono desplegable de Paleta de Colores (`Tema ▼`)
 * - Botón `Mi Cuenta` y `Salir`
 * - Indicador claro del estado de conexión
 */
@Composable
private fun VehicleTopHeader(
    auditInfo: SecurityAuditInfo?,
    connectionState: ConnectionState,
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    onDisconnectSecure: () -> Unit,
    onOpenAccountDialog: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val palette = LocalAppPalette.current
    val fullName = auditInfo?.fullName ?: "Operador Principal OT"
    val username = auditInfo?.operatorId ?: "operador_ot"

    val (statusText, statusColor, statusBg) = when (connectionState) {
        is ConnectionState.Connected -> Triple(
            "CONECTADO • ${connectionState.endpointLabel}",
            palette.statusSafe,
            palette.statusSafeBg
        )
        is ConnectionState.Connecting -> Triple(
            "CONECTANDO • ${connectionState.targetInfo}",
            palette.statusWarning,
            palette.statusWarningBg
        )
        is ConnectionState.Error -> Triple(
            "AVISO • ${connectionState.reason}",
            palette.statusAlert,
            palette.statusAlertBg
        )
        ConnectionState.Disconnected -> Triple(
            "SIN CONEXIÓN ACTIVA",
            palette.textSecondary,
            palette.surfaceVariant
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.cardBorder)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = palette.surfaceVariant,
                        border = BorderStroke(2.dp, palette.primary)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Acciones rápidas: Botón Desplegable de Tema, Mi Cuenta y Salir
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PalettePopupButton(
                        selectedPalette = selectedPalette,
                        onSelectPalette = onSelectPalette,
                        compactLabel = true
                    )

                    IconButton(
                        onClick = onOpenAccountDialog,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceVariant)
                            .testTag("my_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Mi Cuenta",
                            tint = palette.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onRequestLogout,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.statusAlertBg)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar Sesión",
                            tint = palette.statusAlert,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("link_status_indicator")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (connectionState !is ConnectionState.Disconnected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onDisconnectSecure,
                        border = BorderStroke(1.dp, palette.statusAlert),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlert),
                        modifier = Modifier.testTag("header_disconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Desconectar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta limpia de inicio rápido en la pestaña de Control cuando aún no hay enlace activo.
 */
@Composable
private fun QuickStartBannerCard(
    isSimulatorRunning: Boolean,
    onStartOneClickSimulator: () -> Unit,
    onGoToConnectionTab: () -> Unit
) {
    val palette = LocalAppPalette.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.5.dp, palette.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¿Cómo deseas comenzar?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Conéctate a otro celular, al vehículo ESP32 o activa el simulador en 1 toque para probar los controles ahora mismo.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartOneClickSimulator,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.secondary,
                        contentColor = palette.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("quick_start_simulator_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulatorRunning) "Detener Demo" else "Modo Demo 1-Toque",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onGoToConnectionTab,
                    border = BorderStroke(1.5.dp, palette.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("go_to_connect_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Celular / ESP32",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Banner Crítico en color rojo cuando `distancia <= 15 cm` o `frenoActivo == true`.
 */
@Composable
private fun EmergencyBrakeBanner(distanciaCm: Int) {
    val palette = LocalAppPalette.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("critical_brake_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.statusAlertBg),
        border = BorderStroke(2.dp, palette.statusAlert)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Freno de emergencia activo",
                tint = palette.statusAlert,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⚠️ FRENO ACTIVO — OBSTÁCULO A $distanciaCm CM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.statusAlert
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Distancia crítica (<= 15 cm). El botón ADELANTE se ha bloqueado por seguridad. Usa ATRÁS para alejar el vehículo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
            }
        }
    }
}

/**
 * Tarjeta Principal de Telemetría Ultrasónica en Vivo y Control de Motores (Pestaña 1).
 * Diseñada con letras grandes, claras y botones ergonómicos.
 */
@Composable
private fun LiveTelemetryAndMotorControlCard(
    uiState: VehicleUiState,
    onSendCommand: (VehicleCommand) -> Unit,
    onDisconnectSecure: () -> Unit,
    onInjectTestDistance: (Int) -> Unit
) {
    val palette = LocalAppPalette.current
    val isCritical = uiState.isCriticalBrakeAlert
    val isCaution = !isCritical && uiState.distanciaCm in 16..35

    val distanceAccentColor by animateColorAsState(
        targetValue = when {
            isCritical -> palette.statusAlert
            isCaution -> palette.statusWarning
            else -> palette.statusSafe
        },
        animationSpec = tween(durationMillis = 250),
        label = "distance_color_anim"
    )

    val proximityProgress by animateFloatAsState(
        targetValue = ((120f - uiState.distanciaCm.coerceIn(0, 120)) / 120f).coerceIn(0.06f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "proximity_bar_anim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_motor_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(
            width = if (isCritical) 2.dp else 1.dp,
            color = if (isCritical) palette.statusAlert else palette.cardBorder
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            val isLandscapeWide = maxWidth >= 620.dp

            val sensorSection: @Composable () -> Unit = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Encabezado del Sensor y LED de Alerta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sensor de Distancia (HC-SR04)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }

                        // Indicador del LED de Advertencia del Vehículo
                        Surface(
                            color = if (isCritical) palette.statusAlertBg else palette.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isCritical) palette.statusAlert else palette.cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Estado LED Advertencia",
                                    tint = if (isCritical) palette.statusAlert else palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCritical) "LED ALERTA: ON" else "LED: OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCritical) palette.statusAlert else palette.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Indicador Visual Grande de Distancia en Centímetros
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("distance_display_box"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCritical) palette.statusAlertBg.copy(alpha = 0.7f) else palette.background,
                        border = BorderStroke(1.5.dp, distanceAccentColor.copy(alpha = 0.75f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "DISTANCIA AL OBSTÁCULO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.distanciaCm} cm",
                                style = MaterialTheme.typography.displayLarge,
                                color = distanceAccentColor,
                                modifier = Modifier.testTag("distance_cm_value")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = distanceAccentColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = when {
                                        isCritical -> "⚠️ PELIGRO CRÍTICO (<= 15 cm) — FRENO ACTIVO"
                                        isCaution -> "PRECAUCIÓN (16 - 35 cm) — OBSTÁCULO CERCANO"
                                        else -> "CAMINO DESPEJADO (> 35 cm) — OPERACIÓN SEGURA"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = distanceAccentColor,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Barra de Nivel de Proximidad
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Lejos (120 cm)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary
                                    )
                                    Text(
                                        text = "Nivel de Proximidad: ${(proximityProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = distanceAccentColor
                                    )
                                    Text(
                                        text = "Freno (<= 15 cm)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.statusAlert
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { proximityProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .testTag("proximity_progress_bar"),
                                    color = distanceAccentColor,
                                    trackColor = palette.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Estado Actual del Motor y del Freno Automático
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusMetricTile(
                            label = "DIRECCIÓN / MOTOR",
                            value = when (uiState.estadoMotor) {
                                VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA -> "DOBLANDO IZQ. ◄"
                                VehicleTelemetry.MOTOR_GIRANDO_DERECHA -> "DOBLANDO DER. ►"
                                else -> uiState.estadoMotor
                            },
                            valueColor = when (uiState.estadoMotor) {
                                VehicleTelemetry.MOTOR_AVANZANDO -> palette.statusSafe
                                VehicleTelemetry.MOTOR_GIRANDO_IZQUIERDA,
                                VehicleTelemetry.MOTOR_GIRANDO_DERECHA -> palette.secondary
                                VehicleTelemetry.MOTOR_RETROCEDIENDO -> palette.statusWarning
                                else -> palette.primary
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("motor_state_tile")
                        )

                        StatusMetricTile(
                            label = "FRENO AUTOMÁTICO",
                            value = if (isCritical) "⚠️ FRENO ACTIVO" else "LIBERADO",
                            valueColor = if (isCritical) palette.statusAlert else palette.statusSafe,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("brake_state_tile")
                        )
                    }
                }
            }

            val controlsSection: @Composable () -> Unit = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Panel de Control de Movimiento y Giro del Vehículo (Cruceta de 5 Direcciones)
                    Text(
                        text = "Cruceta de Conducción y Giro del Vehículo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (uiState.isForwardButtonEnabled) {
                            "Controla el avance, giro a la izquierda/derecha, parada y retroceso en tiempo real."
                        } else {
                            "El botón ADELANTE está bloqueado por obstáculo a ${uiState.distanciaCm} cm. Puedes doblar o retroceder."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isForwardButtonEnabled) palette.textSecondary else palette.statusAlert
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. FILA SUPERIOR: ADELANTE (/adelante)
                    Button(
                        onClick = { onSendCommand(VehicleCommand.ADELANTE) },
                        enabled = uiState.isForwardButtonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.statusSafe,
                            contentColor = palette.background,
                            disabledContainerColor = palette.surfaceVariant,
                            disabledContentColor = palette.textMuted
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("cmd_adelante_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Avanzar vehículo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isForwardButtonEnabled) {
                                "ADELANTE (Avanzar)"
                            } else {
                                "ADELANTE BLOQUEADO (FRENO <= 15 CM)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. FILA CENTRAL: IZQUIERDA (/izquierda) | PARAR (/parar) | DERECHA (/derecha)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón IZQUIERDA (Doblar a la izquierda)
                        Button(
                            onClick = { onSendCommand(VehicleCommand.IZQUIERDA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primary,
                                contentColor = palette.background
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .testTag("cmd_izquierda_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Doblar a la izquierda",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IZQUIERDA",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Botón Central PARAR
                        Button(
                            onClick = { onSendCommand(VehicleCommand.PARAR) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.statusAlert,
                                contentColor = palette.textPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .height(58.dp)
                                .testTag("cmd_parar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Detener motores",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PARAR",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Botón DERECHA (Doblar a la derecha)
                        Button(
                            onClick = { onSendCommand(VehicleCommand.DERECHA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primary,
                                contentColor = palette.background
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .testTag("cmd_derecha_button")
                        ) {
                            Text(
                                text = "DERECHA",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Doblar a la derecha",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. FILA INFERIOR: ATRÁS (/atras)
                    Button(
                        onClick = { onSendCommand(VehicleCommand.ATRAS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.statusWarning,
                            contentColor = palette.background
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("cmd_atras_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Retroceder vehículo",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ATRÁS (Retroceder)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulador rápido de distancias para probar el freno o transmitir a otro celular
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.surfaceVariant,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Simular Distancia del Sensor (Envía en vivo al otro celular):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onInjectTestDistance(85) },
                                    border = BorderStroke(1.dp, palette.statusSafe),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusSafe),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sim_safe_distance_button")
                                ) {
                                    Text("85 cm Libre", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { onInjectTestDistance(25) },
                                    border = BorderStroke(1.dp, palette.statusWarning),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusWarning),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sim_warn_distance_button")
                                ) {
                                    Text("25 cm Cerca", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { onInjectTestDistance(12) },
                                    border = BorderStroke(1.dp, palette.statusAlert),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlert),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sim_critical_distance_button")
                                ) {
                                    Text("12 cm Freno", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (isLandscapeWide) {
                // En modo Horizontal (Rotación de Pantalla): vista ergonómica de 2 columnas lado a lado
                // para que ningún botón se pierda y se opere como un mando profesional.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        sensorSection()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        controlsSection()
                    }
                }
            } else {
                // En modo Vertical: vista apilada ordenada
                Column(modifier = Modifier.fillMaxWidth()) {
                    sensorSection()
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = palette.cardBorder)
                    Spacer(modifier = Modifier.height(14.dp))
                    controlsSection()
                }
            }
        }
    }
}

@Composable
private fun StatusMetricTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = palette.surfaceVariant,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Selector de Modo de Enlace: Wi-Fi vs Bluetooth (Pestaña 2).
 */
@Composable
private fun LinkModeSelectorCard(
    selectedTransport: TransportMode,
    onSelectTransport: (TransportMode) -> Unit
) {
    val palette = LocalAppPalette.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("link_mode_selector_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "1. Elige el Tipo de Conexión Inalámbrica",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Text(
                text = "Puedes conectar este celular con otro celular, con el vehículo ESP32 o con tu PC.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransportModeButton(
                    title = "Wi-Fi / Hotspot",
                    subtitle = "Celular a Celular o ESP32",
                    icon = Icons.Default.Wifi,
                    selected = selectedTransport == TransportMode.WIFI_ESP32,
                    onClick = { onSelectTransport(TransportMode.WIFI_ESP32) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_wifi_esp32_button")
                )

                TransportModeButton(
                    title = "Bluetooth",
                    subtitle = "Celular a Celular o SPP",
                    icon = Icons.Default.Bluetooth,
                    selected = selectedTransport == TransportMode.BLUETOOTH_RFCOMM,
                    onClick = { onSelectTransport(TransportMode.BLUETOOTH_RFCOMM) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_bt_rfcomm_button")
                )
            }
        }
    }
}

@Composable
private fun TransportModeButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.surfaceVariant else palette.background,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) palette.primary else palette.cardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) palette.primary else palette.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) palette.primary else palette.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

/**
 * Configuración de Conexión Wi-Fi y Bluetooth con soporte dedicado para **Celular a Celular** y **ESP32**.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WirelessEndpointConfigCard(
    uiState: VehicleUiState,
    onIpChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onApplyDefaultEsp32Ip: () -> Unit,
    onConnectWifiEsp32: () -> Unit,
    onToggleEmbeddedSimulator: () -> Unit,
    onRefreshBluetooth: () -> Unit,
    onManualBtMacChanged: (String) -> Unit,
    onConnectBluetoothMac: (String) -> Unit,
    onStartBluetoothServer: () -> Unit,
    onDisconnectSecure: () -> Unit
) {
    val palette = LocalAppPalette.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_endpoint_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (uiState.selectedTransport == TransportMode.WIFI_ESP32) {
                // =============================================================
                // MODO WI-FI: CELULAR A CELULAR O VEHÍCULO ESP32
                // =============================================================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "2. Conexión por Wi-Fi (Celular a Celular o ESP32)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Conecta dos celulares en el mismo Wi-Fi / Hotspot o conéctate al ESP32.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECCIÓN A: CONECTAR DE CELULAR A CELULAR (MODO EMISOR EN ESTE CELULAR)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = palette.surfaceVariant,
                    border = BorderStroke(1.5.dp, palette.secondary.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = palette.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Opción A: Usar ESTE Celular como Emisor / Vehículo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val ipDisplay = if (uiState.localDeviceIps.isNotEmpty()) {
                            uiState.localDeviceIps.joinToString("  ó  ")
                        } else {
                            "Conecta tu Wi-Fi o Zona Portátil (Hotspot)"
                        }
                        Text(
                            text = "1. Activa el botón de abajo en este teléfono.\n" +
                                "2. En el OTRO celular, escribe esta IP: $ipDisplay y el puerto 8080.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )

                        if (uiState.localDeviceIps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = palette.background,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, palette.primary)
                            ) {
                                Text(
                                    text = "IP de este celular: ${uiState.localDeviceIps.first()}  |  Puerto: 8080",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onToggleEmbeddedSimulator,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isLocalEmbeddedSimRunning) {
                                    palette.statusWarning
                                } else {
                                    palette.secondary
                                },
                                contentColor = palette.background
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("embedded_esp32_sim_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isLocalEmbeddedSimRunning) {
                                    Icons.Default.StopCircle
                                } else {
                                    Icons.Default.PhoneAndroid
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isLocalEmbeddedSimRunning) {
                                    "Emisor Activo en Puerto 8080 (Toca para Detener)"
                                } else {
                                    "Activar este Celular como Emisor (Puerto 8080)"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECCIÓN B: CONECTARSE A OTRO CELULAR O AL ESP32
                Text(
                    text = "Opción B: Conectarse a Otro Celular o al Vehículo ESP32",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ajustes rápidos (toca uno para rellenar automáticamente):",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.esp32IpInput == "192.168.4.1" && uiState.esp32PortInput == "80",
                        onClick = onApplyDefaultEsp32Ip,
                        label = { Text("Vehículo ESP32 (192.168.4.1:80)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                            selectedLabelColor = palette.primary
                        ),
                        modifier = Modifier.testTag("preset_esp32_ap_chip")
                    )

                    FilterChip(
                        selected = uiState.esp32IpInput == "192.168.43.1" && uiState.esp32PortInput == "8080",
                        onClick = {
                            onIpChanged("192.168.43.1")
                            onPortChanged("8080")
                        },
                        label = { Text("Otro Celular Hotspot (192.168.43.1:8080)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                            selectedLabelColor = palette.primary
                        ),
                        modifier = Modifier.testTag("preset_phone_hotspot_chip")
                    )

                    FilterChip(
                        selected = uiState.esp32IpInput == "10.0.2.2" && uiState.esp32PortInput == "8080",
                        onClick = {
                            onIpChanged("10.0.2.2")
                            onPortChanged("8080")
                        },
                        label = { Text("PC Simulador (10.0.2.2:8080)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                            selectedLabelColor = palette.primary
                        ),
                        modifier = Modifier.testTag("preset_pc_sim_chip")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.esp32IpInput,
                        onValueChange = onIpChanged,
                        label = { Text("IP del Otro Celular o ESP32") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.cardBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier
                            .weight(0.68f)
                            .testTag("esp32_ip_input")
                    )

                    OutlinedTextField(
                        value = uiState.esp32PortInput,
                        onValueChange = onPortChanged,
                        label = { Text("Puerto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.cardBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier
                            .weight(0.32f)
                            .testTag("esp32_port_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onConnectWifiEsp32,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = palette.background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("connect_wifi_esp32_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Conectar Ahora",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onDisconnectSecure,
                        border = BorderStroke(1.dp, palette.statusAlert),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlert),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("disconnect_wifi_esp32_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                // =============================================================
                // MODO BLUETOOTH: CELULAR A CELULAR O VEHÍCULO ESP32
                // =============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "2. Conexión Bluetooth (Celular a Celular o ESP32)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Sin necesidad de Wi-Fi. Empareja ambos celulares en Ajustes Bluetooth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshBluetooth,
                        modifier = Modifier.testTag("refresh_bt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar lista Bluetooth",
                            tint = palette.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Paso 1 en Bluetooth Celular a Celular: Recibir conexión
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.surfaceVariant,
                    border = BorderStroke(1.5.dp, palette.secondary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Paso 1 (En el Celular 1 - Emisor):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Toca este botón en el primer celular para que quede esperando la conexión del segundo celular:",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStartBluetoothServer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.secondary,
                                contentColor = palette.background
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("listen_bt_rfcomm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recibir Conexión de Otro Celular (Bluetooth)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Paso 2: Seleccionar el otro celular emparejado o ESP32
                Text(
                    text = "Paso 2 (En el Celular 2 - Control): Elige el Celular o ESP32",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )

                if (uiState.pairedBtDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Dispositivos y Celulares Emparejados (Toca uno para conectar):",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.pairedBtDevices.forEach { device ->
                        PairedBtRow(
                            device = device,
                            onSelect = {
                                onManualBtMacChanged(device.macAddress)
                                onConnectBluetoothMac(device.macAddress)
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Si aún no ves el otro celular aquí, vincúlalo primero en los Ajustes Bluetooth de Android y toca el ícono de refrescar arriba.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.manualBtMacInput,
                        onValueChange = onManualBtMacChanged,
                        label = { Text("Dirección MAC Bluetooth") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.cardBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bt_mac_input")
                    )

                    Button(
                        onClick = { onConnectBluetoothMac(uiState.manualBtMacInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = palette.background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("connect_bt_mac_button")
                    ) {
                        Text("Conectar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedBtRow(
    device: PairedBluetoothDevice,
    onSelect: () -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = palette.surfaceVariant,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary
                    )
                }
            }
            Surface(
                color = palette.primary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "CONECTAR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Resumen de Cuenta y Seguridad en la Pestaña 3 ("Historial y Cuenta").
 */
@Composable
private fun AccountSummarySectionCard(
    auditInfo: SecurityAuditInfo?,
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    onOpenPasswordDialog: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val palette = LocalAppPalette.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = auditInfo?.fullName ?: "Operador Principal OT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "@${auditInfo?.operatorId ?: "operador_ot"} • ${auditInfo?.role ?: "Supervisor"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondary
                    )
                }

                PalettePopupButton(
                    selectedPalette = selectedPalette,
                    onSelectPalette = onSelectPalette
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPasswordDialog,
                    border = BorderStroke(1.dp, palette.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cambiar Clave", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onRequestLogout,
                    border = BorderStroke(1.dp, palette.statusAlert),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlert),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerrar Sesión", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Diálogo de Perfil de Cuenta, Cambio de Contraseña y Cierre de Sesión.
 */
@Composable
private fun UserProfileAndSecurityDialog(
    auditInfo: SecurityAuditInfo?,
    passwordMessage: String?,
    passwordError: String?,
    onChangePassword: (String, String, String) -> Unit,
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    onDismiss: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val currentColors = LocalAppPalette.current
    var currentPass by rememberSaveable { mutableStateOf("") }
    var newPass by rememberSaveable { mutableStateOf("") }
    var confirmPass by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = currentColors.surface,
        titleContentColor = currentColors.textPrimary,
        textContentColor = currentColors.textSecondary,
        icon = {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = currentColors.primary,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = "Mi Cuenta y Seguridad (ISO 27400)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = currentColors.surfaceVariant,
                    border = BorderStroke(1.dp, currentColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = auditInfo?.fullName ?: "Operador OT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = currentColors.textPrimary
                        )
                        Text(
                            text = "Usuario: @${auditInfo?.operatorId ?: "operador_ot"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = currentColors.primary
                        )
                        Text(
                            text = "Rol: ${auditInfo?.role ?: "Supervisor"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = currentColors.secondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estilo Visual:",
                        style = MaterialTheme.typography.labelLarge,
                        color = currentColors.textPrimary
                    )
                    PalettePopupButton(
                        selectedPalette = selectedPalette,
                        onSelectPalette = onSelectPalette
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cambiar Contraseña Cifrada (SHA-256 + Salt):",
                    style = MaterialTheme.typography.labelLarge,
                    color = currentColors.primary
                )

                OutlinedTextField(
                    value = currentPass,
                    onValueChange = { currentPass = it },
                    label = { Text("Contraseña Actual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Nueva Contraseña (mín. 6 caracteres)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text("Confirmar Nueva Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!passwordError.isNullOrBlank()) {
                    Text(
                        text = passwordError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = currentColors.statusAlert
                    )
                }

                if (!passwordMessage.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = currentColors.statusSafe,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = passwordMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = currentColors.statusSafe
                        )
                    }
                }

                Button(
                    onClick = { onChangePassword(currentPass, newPass, confirmPass) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentColors.primary,
                        contentColor = currentColors.background
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Actualizar Contraseña", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onRequestLogout,
                border = BorderStroke(1.dp, currentColors.statusAlert),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = currentColors.statusAlert)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cerrar Sesión")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar Ventana", color = currentColors.textSecondary)
            }
        }
    )
}

@Composable
private fun ProtocolAuditConsoleCard(
    telemetryHistory: List<VehicleTelemetry>,
    systemLogs: List<String>,
    onClearLogs: () -> Unit
) {
    val palette = LocalAppPalette.current
    var showJsonTab by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("protocol_audit_console"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historial de Telemetría y Red",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Limpiar consola",
                        tint = palette.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showJsonTab,
                    onClick = { showJsonTab = true },
                    label = { Text("Tramas JSON (${telemetryHistory.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                        selectedLabelColor = palette.primary
                    )
                )
                FilterChip(
                    selected = !showJsonTab,
                    onClick = { showJsonTab = false },
                    label = { Text("Eventos (${systemLogs.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                        selectedLabelColor = palette.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = palette.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            if (showJsonTab) {
                if (telemetryHistory.isEmpty()) {
                    Text(
                        text = "Aún no se han recibido tramas. Conéctate a otro celular, al ESP32 o activa el Modo Demo en la pestaña de Control.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        telemetryHistory.take(8).forEach { pkt ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = palette.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (pkt.isEmergencyBrakeActive) palette.statusAlert.copy(alpha = 0.7f)
                                    else palette.cardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "#${pkt.sequenceNumber} • ${pkt.sourceTransport}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.secondary
                                        )
                                        Text(
                                            text = if (pkt.isEmergencyBrakeActive) "⚠️ FRENO ACTIVO" else "OPERACIÓN SEGURA",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pkt.isEmergencyBrakeActive) palette.statusAlert else palette.statusSafe
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pkt.rawJson,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (systemLogs.isEmpty()) {
                    Text(
                        text = "Sin eventos registrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        systemLogs.take(12).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
