package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Catálogo de Paletas de Colores de la Estación OT / SCADA.
 * Ofrece múltiples opciones visuales para adaptarse a distintos entornos
 * de iluminación (cuartos de control oscuros, operaciones tácticas nocturnas y luz solar diurna).
 */
enum class AppPalette(
    val id: String,
    val displayName: String,
    val previewPrimary: Color,
    val previewAccent: Color,
    val isDarkTheme: Boolean,
    val description: String
) {
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "Cyber Cyan",
        previewPrimary = Color(0xFF00E5FF),
        previewAccent = Color(0xFF1DE9B6),
        isDarkTheme = true,
        description = "Estilo clásico OT/Cyberpunk con azul cian eléctrico y fondo nocturno profundo"
    ),
    TACTICAL_AMBER(
        id = "tactical_amber",
        displayName = "Ámbar Táctico",
        previewPrimary = Color(0xFFFFB300),
        previewAccent = Color(0xFFFF9100),
        isDarkTheme = true,
        description = "Paleta industrial SCADA / Maquinaria pesada con acentos dorados y ámbar"
    ),
    MATRIX_EMERALD(
        id = "matrix_emerald",
        displayName = "Verde Matrix",
        previewPrimary = Color(0xFF00E676),
        previewAccent = Color(0xFF69F0AE),
        isDarkTheme = true,
        description = "Terminal verde de fósforo para inspección y ciberdefensa perimetral"
    ),
    SYNTH_VIOLET(
        id = "synth_violet",
        displayName = "Neón Violeta",
        previewPrimary = Color(0xFFC77DFF),
        previewAccent = Color(0xFFFF4081),
        isDarkTheme = true,
        description = "Modo furtivo de alta tecnología con tonos violeta eléctrico y magenta"
    ),
    CRIMSON_OPS(
        id = "crimson_ops",
        displayName = "Rojo Táctico",
        previewPrimary = Color(0xFFFF3D00),
        previewAccent = Color(0xFFFF1744),
        isDarkTheme = true,
        description = "Operaciones de máxima alerta y visión nocturna de alto contraste"
    ),
    DAYLIGHT_CLEAN(
        id = "daylight_clean",
        displayName = "Luz de Día",
        previewPrimary = Color(0xFF0284C7),
        previewAccent = Color(0xFF0D9488),
        isDarkTheme = false,
        description = "Modo claro de alto contraste para inspecciones de campo con luz solar directa"
    );

    companion object {
        fun fromId(id: String?): AppPalette {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CYBER_CYAN
        }
    }
}

/**
 * Conjunto completo de tokens de color por paleta de interfaz.
 */
data class AppPaletteColors(
    val palette: AppPalette,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBorder: Color,
    val primary: Color,
    val primaryDim: Color,
    val secondary: Color,
    val statusSafe: Color,
    val statusSafeBg: Color,
    val statusWarning: Color,
    val statusWarningBg: Color,
    val statusAlert: Color,
    val statusAlertBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean
)

fun getPaletteColors(palette: AppPalette): AppPaletteColors {
    return when (palette) {
        AppPalette.CYBER_CYAN -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF070D14),
            surface = Color(0xFF0E1824),
            surfaceVariant = Color(0xFF162436),
            cardBorder = Color(0xFF284261),
            primary = Color(0xFF00E5FF),
            primaryDim = Color(0xFF007C91),
            secondary = Color(0xFF1DE9B6),
            statusSafe = Color(0xFF00E676),
            statusSafeBg = Color(0xFF07291B),
            statusWarning = Color(0xFFFFB300),
            statusWarningBg = Color(0xFF2B1F06),
            statusAlert = Color(0xFFFF1744),
            statusAlertBg = Color(0xFF2E0913),
            textPrimary = Color(0xFFF4F9FF),
            textSecondary = Color(0xFFBDD1E8),
            textMuted = Color(0xFF8EA7C4),
            isDark = true
        )

        AppPalette.TACTICAL_AMBER -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF100C06),
            surface = Color(0xFF1A140B),
            surfaceVariant = Color(0xFF271F13),
            cardBorder = Color(0xFF544329),
            primary = Color(0xFFFFB300),
            primaryDim = Color(0xFFB27B00),
            secondary = Color(0xFFFF9100),
            statusSafe = Color(0xFF00E676),
            statusSafeBg = Color(0xFF1A2610),
            statusWarning = Color(0xFFFFB300),
            statusWarningBg = Color(0xFF332306),
            statusAlert = Color(0xFFFF1744),
            statusAlertBg = Color(0xFF330B12),
            textPrimary = Color(0xFFFFFAF2),
            textSecondary = Color(0xFFEDD8BA),
            textMuted = Color(0xFFBFA684),
            isDark = true
        )

        AppPalette.MATRIX_EMERALD -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF040E08),
            surface = Color(0xFF0A1C11),
            surfaceVariant = Color(0xFF112C1B),
            cardBorder = Color(0xFF255737),
            primary = Color(0xFF00E676),
            primaryDim = Color(0xFF008C45),
            secondary = Color(0xFF69F0AE),
            statusSafe = Color(0xFF00E676),
            statusSafeBg = Color(0xFF062B17),
            statusWarning = Color(0xFFFFB300),
            statusWarningBg = Color(0xFF262106),
            statusAlert = Color(0xFFFF1744),
            statusAlertBg = Color(0xFF2B0A11),
            textPrimary = Color(0xFFF0FFF5),
            textSecondary = Color(0xFFB4E6C5),
            textMuted = Color(0xFF7FB893),
            isDark = true
        )

        AppPalette.SYNTH_VIOLET -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF0C0614),
            surface = Color(0xFF170D26),
            surfaceVariant = Color(0xFF25163D),
            cardBorder = Color(0xFF523282),
            primary = Color(0xFFC77DFF),
            primaryDim = Color(0xFF7B2CBF),
            secondary = Color(0xFFFF4081),
            statusSafe = Color(0xFF00E676),
            statusSafeBg = Color(0xFF10261A),
            statusWarning = Color(0xFFFFB300),
            statusWarningBg = Color(0xFF2B1F0A),
            statusAlert = Color(0xFFFF1744),
            statusAlertBg = Color(0xFF2B0816),
            textPrimary = Color(0xFFFAF2FF),
            textSecondary = Color(0xFFD8C2F0),
            textMuted = Color(0xFFA68CC4),
            isDark = true
        )

        AppPalette.CRIMSON_OPS -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF120608),
            surface = Color(0xFF1F0B0E),
            surfaceVariant = Color(0xFF301217),
            cardBorder = Color(0xFF6B2733),
            primary = Color(0xFFFF3D00),
            primaryDim = Color(0xFFB22A00),
            secondary = Color(0xFFFF1744),
            statusSafe = Color(0xFF00E676),
            statusSafeBg = Color(0xFF1A2612),
            statusWarning = Color(0xFFFFB300),
            statusWarningBg = Color(0xFF332007),
            statusAlert = Color(0xFFFF1744),
            statusAlertBg = Color(0xFF3B0C15),
            textPrimary = Color(0xFFFFF2F4),
            textSecondary = Color(0xFFEBC2C8),
            textMuted = Color(0xFFB88890),
            isDark = true
        )

        AppPalette.DAYLIGHT_CLEAN -> AppPaletteColors(
            palette = palette,
            background = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E8F0),
            cardBorder = Color(0xFF94A3B8),
            primary = Color(0xFF0284C7),
            primaryDim = Color(0xFF0369A1),
            secondary = Color(0xFF0D9488),
            statusSafe = Color(0xFF15803D),
            statusSafeBg = Color(0xFFDCFCE7),
            statusWarning = Color(0xFFB45309),
            statusWarningBg = Color(0xFFFEF3C7),
            statusAlert = Color(0xFFB91C1C),
            statusAlertBg = Color(0xFFFEE2E2),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF1E293B),
            textMuted = Color(0xFF334155),
            isDark = false
        )
    }
}

// Valores globales por defecto para compatibilidad estática
val CyberBackground = Color(0xFF070D14)
val CyberSurface = Color(0xFF0E1824)
val CyberSurfaceVariant = Color(0xFF162436)
val CyberCardBorder = Color(0xFF284261)

val CyberCyan = Color(0xFF00E5FF)
val CyberCyanDim = Color(0xFF007C91)
val CyberTeal = Color(0xFF1DE9B6)

val StatusSafeGreen = Color(0xFF00E676)
val StatusSafeBg = Color(0xFF07291B)

val StatusWarningAmber = Color(0xFFFFB300)
val StatusWarningBg = Color(0xFF2B1F06)

val StatusAlertCrimson = Color(0xFFFF1744)
val StatusAlertBg = Color(0xFF2E0913)

val TextPrimary = Color(0xFFF4F9FF)
val TextSecondary = Color(0xFFBDD1E8)
val TextMuted = Color(0xFF8EA7C4)
