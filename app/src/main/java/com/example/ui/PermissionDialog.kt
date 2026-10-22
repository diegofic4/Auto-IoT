package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.TransportMode
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.StatusSafeBg
import com.example.ui.theme.StatusSafeGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Utilidad de permisos en runtime bajo el estándar ISO/IEC 27400 (Consentimiento Informado y Mínimo Privilegio).
 */
object VehiclePermissionHelper {

    /**
     * Determina los permisos en runtime requeridos según el modo inalámbrico y `Build.VERSION.SDK_INT`:
     * - Bluetooth:
     *   * Android 12+ (API 31+): `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
     *   * Android 11 e inferiores (API <= 30): `ACCESS_FINE_LOCATION`.
     * - Wi-Fi:
     *   * Android 13+ (API 33+): `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES`.
     *   * Android 8.0 a 12 (API 26 - 32): `ACCESS_FINE_LOCATION`.
     */
    fun getRequiredPermissions(mode: TransportMode): List<String> {
        return when (mode) {
            TransportMode.BLUETOOTH_RFCOMM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            }

            TransportMode.WIFI_ESP32 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    )
                } else {
                    listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            }
        }
    }

    fun areAllPermissionsGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(context: Context, permissions: List<String>): List<String> {
        return permissions.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun openSystemAppSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

/**
 * Diálogo Previo de Justificación de Permisos (Rationale Dialog en Compose - ISO/IEC 27400).
 *
 * Se despliega ANTES de invocar el diálogo nativo del sistema operativo para explicar claramente
 * por qué la estación OT necesita acceso inalámbrico local (vincular el vehículo de inspección ESP32
 * `"Auto_IoT_Control"` y recibir telemetría ultrasónica en tiempo real).
 * Solo al pulsar `"Entendido / Continuar"` se invoca `rememberLauncherForActivityResult`.
 */
@Composable
fun PermissionRationaleDialog(
    transportMode: TransportMode,
    requiredPermissions: List<String>,
    onConfirmContinue: () -> Unit,
    onDismissCancel: () -> Unit
) {
    val palette = LocalAppPalette.current

    AlertDialog(
        onDismissRequest = onDismissCancel,
        containerColor = palette.surface,
        iconContentColor = palette.primary,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Consentimiento Informado ISO 27400",
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Consentimiento Informado de Permisos Inalámbricos (ISO 27400)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Para operar la Estación de Monitoreo y Teleoperación del Vehículo de Inspección IoT (ESP32), la aplicación solicita autorización para acceder a las interfaces inalámbricas locales (${transportMode.displayName}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = "Finalidad técnica estricta:\n" +
                        "• Vincular el vehículo autónomo 'Auto_IoT_Control' (Wi-Fi 192.168.4.1 o Bluetooth SPP) o enlazar con otro celular.\n" +
                        "• Recibir telemetría ultrasónica (HC-SR04) en tiempo real y activar el freno de emergencia (<= 15 cm).\n" +
                        "• Ningún dato de ubicación o red es transmitido a servidores externos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Text(
                    text = "Permisos requeridos (API ${Build.VERSION.SDK_INT}):\n" +
                        requiredPermissions.joinToString(", ") { it.substringAfterLast('.') },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.background
                ),
                modifier = Modifier.testTag("rationale_confirm_button")
            ) {
                Text(
                    text = "Entendido / Continuar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissCancel,
                modifier = Modifier.testTag("rationale_cancel_button")
            ) {
                Text(
                    text = "Ahora no",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    )
}

/**
 * Gestor reactivo de permisos en runtime que coordina:
 * 1. El `PermissionRationaleDialog` previo (Consentimiento Informado ISO 27400).
 * 2. El lanzador `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`.
 * 3. La tarjeta de restricción tolerante a rechazo con acceso directo a Ajustes del Sistema.
 */
@Composable
fun InformedPermissionSection(
    transportMode: TransportMode,
    onPermissionStateUpdated: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val requiredPermissions = remember(transportMode) {
        VehiclePermissionHelper.getRequiredPermissions(transportMode)
    }

    var isGranted by remember(transportMode) {
        mutableStateOf(VehiclePermissionHelper.areAllPermissionsGranted(context, requiredPermissions))
    }
    var missingPermissions by remember(transportMode) {
        mutableStateOf(VehiclePermissionHelper.getMissingPermissions(context, requiredPermissions))
    }

    // Mostrar automáticamente el pop-up de justificación (Rationale Dialog) la primera vez que faltan permisos
    var showRationaleDialog by rememberSaveable(transportMode.name) {
        mutableStateOf(!isGranted)
    }
    var rejectionCount by rememberSaveable(transportMode.name) { mutableIntStateOf(0) }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val grantedNow = resultMap.values.all { it } ||
            VehiclePermissionHelper.areAllPermissionsGranted(context, requiredPermissions)
        isGranted = grantedNow
        missingPermissions = VehiclePermissionHelper.getMissingPermissions(context, requiredPermissions)
        if (!grantedNow) {
            rejectionCount++
        }
        onPermissionStateUpdated(grantedNow)
    }

    LaunchedEffect(isGranted) {
        onPermissionStateUpdated(isGranted)
    }

    DisposableEffect(lifecycleOwner, transportMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = VehiclePermissionHelper.areAllPermissionsGranted(context, requiredPermissions)
                isGranted = grantedNow
                missingPermissions = VehiclePermissionHelper.getMissingPermissions(context, requiredPermissions)
                if (grantedNow) {
                    showRationaleDialog = false
                }
                onPermissionStateUpdated(grantedNow)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showRationaleDialog && !isGranted) {
        PermissionRationaleDialog(
            transportMode = transportMode,
            requiredPermissions = requiredPermissions,
            onConfirmContinue = {
                showRationaleDialog = false
                multiplePermissionsLauncher.launch(requiredPermissions.toTypedArray())
            },
            onDismissCancel = {
                showRationaleDialog = false
                rejectionCount++
            }
        )
    }

    if (isGranted) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("permission_granted_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = StatusSafeBg.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, StatusSafeGreen.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permisos inalámbricos concedidos",
                    tint = StatusSafeGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permisos Inalámbricos Autorizados (${transportMode.displayName})",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusSafeGreen
                    )
                    Text(
                        text = "Consentimiento ISO 27400 activo • API ${Build.VERSION.SDK_INT}: ${
                            requiredPermissions.joinToString(", ") { it.substringAfterLast('.') }
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("permission_restricted_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = StatusWarningBg),
            border = BorderStroke(1.5.dp, StatusWarningAmber)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Funciones inalámbricas restringidas",
                        tint = StatusWarningAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Funciones Inalámbricas Restringidas (${transportMode.displayName})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusWarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Los permisos para vincular el vehículo de inspección y recibir telemetría no han sido concedidos. La estación permanece en modo seguro sin cierres inesperados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Permisos pendientes: ${
                        missingPermissions.joinToString(", ") { it.substringAfterLast('.') }
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusWarningAmber
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            // Siempre presenta primero el diálogo de consentimiento informado ISO 27400
                            showRationaleDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = palette.background
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_rationale_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Autorizar Permisos",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            VehiclePermissionHelper.openSystemAppSettings(context)
                        },
                        border = BorderStroke(1.dp, StatusWarningAmber),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarningAmber),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_system_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ajustes del Sistema",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
