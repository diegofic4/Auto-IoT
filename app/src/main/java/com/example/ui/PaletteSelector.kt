package com.example.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppPalette
import com.example.ui.theme.LocalAppPalette

/**
 * Botón / Icono compacto que despliega un cuadro de diálogo limpio y ordenado
 * con las 6 opciones de paleta de colores cuando el usuario lo presiona.
 */
@Composable
fun PalettePopupButton(
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    compactLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentColors = LocalAppPalette.current
    var showPaletteDialog by rememberSaveable { mutableStateOf(false) }

    if (showPaletteDialog) {
        PalettePickerDialog(
            selectedPalette = selectedPalette,
            onSelectPalette = { chosen ->
                onSelectPalette(chosen)
            },
            onDismiss = { showPaletteDialog = false }
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPaletteDialog = true }
            .testTag("palette_dropdown_button"),
        shape = RoundedCornerShape(12.dp),
        color = currentColors.surfaceVariant,
        border = BorderStroke(1.5.dp, currentColors.primary.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Cambiar paleta de colores",
                tint = currentColors.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Muestras de color activas
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(selectedPalette.previewPrimary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(selectedPalette.previewAccent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (compactLabel) "Tema" else "Tema: ${selectedPalette.displayName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = currentColors.textPrimary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = currentColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Diálogo desplegable de selección de paleta de colores con letras claras y gran legibilidad.
 */
@Composable
fun PalettePickerDialog(
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    onDismiss: () -> Unit
) {
    val currentColors = LocalAppPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = currentColors.surface,
        titleContentColor = currentColors.textPrimary,
        textContentColor = currentColors.textSecondary,
        icon = {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = currentColors.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Paleta de Colores",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = currentColors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Elige el estilo visual más cómodo para tu vista",
                    style = MaterialTheme.typography.bodySmall,
                    color = currentColors.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPalette.entries.forEach { palette ->
                    PaletteSelectionItem(
                        palette = palette,
                        isSelected = palette == selectedPalette,
                        onClick = {
                            onSelectPalette(palette)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_palette_dialog_button")
            ) {
                Text(
                    text = "Listo",
                    style = MaterialTheme.typography.labelLarge,
                    color = currentColors.primary
                )
            }
        }
    )
}

/**
 * Fila compacta de selección rápida de paleta de colores (para el diálogo de Mi Cuenta).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteQuickSelectorRow(
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentColors = LocalAppPalette.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = currentColors.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Paleta de Colores de la Estación:",
                style = MaterialTheme.typography.labelMedium,
                color = currentColors.textSecondary
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppPalette.entries.forEach { palette ->
                val isSelected = palette == selectedPalette
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) currentColors.primary else currentColors.cardBorder,
                    label = "border_anim"
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) currentColors.surfaceVariant else currentColors.surface,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelectPalette(palette) }
                        .testTag("palette_chip_${palette.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(palette.previewPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(palette.previewAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = palette.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) currentColors.primary else currentColors.textPrimary
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = currentColors.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaletteSelectorCard(
    selectedPalette: AppPalette,
    onSelectPalette: (AppPalette) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentColors = LocalAppPalette.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("palette_selector_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = currentColors.surface),
        border = BorderStroke(1.dp, currentColors.cardBorder)
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
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = currentColors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PALETA VISUAL DE LA ESTACIÓN",
                            style = MaterialTheme.typography.labelLarge,
                            color = currentColors.primary
                        )
                        Text(
                            text = "Elige entre 6 temas de alto contraste",
                            style = MaterialTheme.typography.labelSmall,
                            color = currentColors.textSecondary
                        )
                    }
                }

                PalettePopupButton(
                    selectedPalette = selectedPalette,
                    onSelectPalette = onSelectPalette,
                    compactLabel = true
                )
            }
        }
    }
}

@Composable
private fun PaletteSelectionItem(
    palette: AppPalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val currentColors = LocalAppPalette.current
    val borderColor = if (isSelected) currentColors.primary else currentColors.cardBorder

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("palette_item_${palette.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) currentColors.surfaceVariant else currentColors.background,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(palette.previewPrimary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(palette.previewAccent)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = palette.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) currentColors.primary else currentColors.textPrimary
                    )
                    Text(
                        text = palette.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = currentColors.textSecondary
                    )
                }
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = currentColors.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Activo",
                            tint = currentColors.background,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
