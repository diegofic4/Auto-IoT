package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LoginScreen
import com.example.ui.TelemetryViewModel
import com.example.ui.VehicleDashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val telemetryViewModel: TelemetryViewModel = viewModel()
            val uiState by telemetryViewModel.uiState.collectAsStateWithLifecycle()
            MyApplicationTheme(palette = uiState.selectedPalette) {
                OtVehicleStationApp(
                    telemetryViewModel = telemetryViewModel
                )
            }
        }
    }
}

@Composable
fun OtVehicleStationApp(
    telemetryViewModel: TelemetryViewModel = viewModel()
) {
    val uiState by telemetryViewModel.uiState.collectAsStateWithLifecycle()

    Crossfade(
        targetState = uiState.isAuthenticated,
        modifier = Modifier.fillMaxSize(),
        label = "auth_to_vehicle_dashboard"
    ) { authenticated ->
        if (!authenticated) {
            LoginScreen(
                auditInfo = uiState.securityAuditInfo,
                registeredUsernames = uiState.registeredUsernames,
                errorMessage = uiState.authErrorMessage,
                statusMessage = uiState.authStatusMessage,
                onLoginSubmit = telemetryViewModel::loginOperator,
                onRegisterSubmit = telemetryViewModel::registerNewAccount,
                onDismissFeedback = telemetryViewModel::clearAuthFeedback,
                selectedPalette = uiState.selectedPalette,
                onSelectPalette = telemetryViewModel::selectPalette
            )
        } else {
            VehicleDashboardScreen(
                uiState = uiState,
                onSelectTransport = telemetryViewModel::selectTransportMode,
                onIpChanged = telemetryViewModel::updateEsp32Ip,
                onPortChanged = telemetryViewModel::updateEsp32Port,
                onApplyDefaultEsp32Ip = telemetryViewModel::applyPresetEsp32Ap,
                onConnectWifiEsp32 = telemetryViewModel::connectWifiEsp32,
                onToggleEmbeddedSimulator = telemetryViewModel::toggleEmbeddedEsp32Simulator,
                onInjectTestDistance = telemetryViewModel::injectSimulatedDistance,
                onRefreshBluetooth = telemetryViewModel::refreshBluetoothDevices,
                onManualBtMacChanged = telemetryViewModel::updateManualBtMac,
                onConnectBluetoothMac = telemetryViewModel::connectBluetoothDevice,
                onStartBluetoothServer = telemetryViewModel::startBluetoothRfcommServer,
                onSendCommand = telemetryViewModel::sendVehicleCommand,
                onDisconnectSecure = telemetryViewModel::disconnectSecurely,
                onClearLogs = telemetryViewModel::clearLogs,
                onChangePassword = telemetryViewModel::changePassword,
                onClearPasswordFeedback = telemetryViewModel::clearPasswordChangeFeedback,
                onLogout = telemetryViewModel::logoutOperator,
                selectedPalette = uiState.selectedPalette,
                onSelectPalette = telemetryViewModel::selectPalette
            )
        }
    }
}
