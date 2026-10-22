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
    val isEyeComfort: Boolean,
    val description: String
) {
    NORDIC_SLATE(
        id = "nordic_slate",
        displayName = "Pizarra Suave",
        previewPrimary = Color(0xFF7AA2F7),
        previewAccent = Color(0xFF73DACA),
        isDarkTheme = true,
        isEyeComfort = true,
        description = "Oscuro mate de descanso visual (estilo Nord), sin brillo neón para no cansar la vista"
    ),
    WARM_PAPER(
        id = "warm_paper",
        displayName = "Papel Cálido",
        previewPrimary = Color(0xFF2B6CB0),
        previewAccent = Color(0xFF319795),
        isDarkTheme = false,
        isEyeComfort = true,
        description = "Claro crema/sepia suave anti-fatiga visual, ideal para lectura prolongada"
    ),
    FOREST_SAGE(
        id = "forest_sage",
        displayName = "Salvia Natural",
        previewPrimary = Color(0xFF68D391),
        previewAccent = Color(0xFF4FD1C5),
        isDarkTheme = true,
        isEyeComfort = true,
        description = "Tonos verde salvia y bosque mate relajantes para la vista"
    ),
    OCEAN_BREEZE(
        id = "ocean_breeze",
        displayName = "Brisa Marina",
        previewPrimary = Color(0xFF2563EB),
        previewAccent = Color(0xFF0891B2),
        isDarkTheme = false,
        isEyeComfort = true,
        description = "Tema claro minimalista, limpio y corporativo con tonos azul suave"
    ),
    DAYLIGHT_CLEAN(
        id = "daylight_clean",
        displayName = "Luz de Día",
        previewPrimary = Color(0xFF0284C7),
        previewAccent = Color(0xFF0D9488),
        isDarkTheme = false,
        isEyeComfort = true,
        description = "Modo claro nítido para inspecciones de campo bajo luz solar directa"
    ),
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "Cyber Cyan",
        previewPrimary = Color(0xFF00E5FF),
        previewAccent = Color(0xFF1DE9B6),
        isDarkTheme = true,
        isEyeComfort = false,
        description = "Estilo clásico OT/Cyberpunk con azul cian eléctrico y fondo nocturno profundo"
    ),
    TACTICAL_AMBER(
        id = "tactical_amber",
        displayName = "Ámbar Táctico",
        previewPrimary = Color(0xFFFFB300),
        previewAccent = Color(0xFFFF9100),
        isDarkTheme = true,
        isEyeComfort = false,
        description = "Paleta industrial SCADA / Maquinaria pesada con acentos dorados y ámbar"
    ),
    MATRIX_EMERALD(
        id = "matrix_emerald",
        displayName = "Verde Matrix",
        previewPrimary = Color(0xFF00E676),
        previewAccent = Color(0xFF69F0AE),
        isDarkTheme = true,
        isEyeComfort = false,
        description = "Terminal verde de fósforo para inspección y ciberdefensa perimetral"
    ),
    SYNTH_VIOLET(
        id = "synth_violet",
        displayName = "Neón Violeta",
        previewPrimary = Color(0xFFC77DFF),
        previewAccent = Color(0xFFFF4081),
        isDarkTheme = true,
        isEyeComfort = false,
        description = "Modo furtivo de alta tecnología con tonos violeta eléctrico y magenta"
    ),
    CRIMSON_OPS(
        id = "crimson_ops",
        displayName = "Rojo Táctico",
        previewPrimary = Color(0xFFFF3D00),
        previewAccent = Color(0xFFFF1744),
        isDarkTheme = true,
        isEyeComfort = false,
        description = "Operaciones de máxima alerta y visión nocturna de alto contraste"
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
        AppPalette.NORDIC_SLATE -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF1B2028),
            surface = Color(0xFF242B35),
            surfaceVariant = Color(0xFF2E3744),
            cardBorder = Color(0xFF434F61),
            primary = Color(0xFF7AA2F7),
            primaryDim = Color(0xFF3D59A1),
            secondary = Color(0xFF73DACA),
            statusSafe = Color(0xFF4ADE80),
            statusSafeBg = Color(0xFF193228),
            statusWarning = Color(0xFFFBBF24),
            statusWarningBg = Color(0xFF332B19),
            statusAlert = Color(0xFFF87171),
            statusAlertBg = Color(0xFF381E24),
            textPrimary = Color(0xFFECEFF4),
            textSecondary = Color(0xFFD8DEE9),
            textMuted = Color(0xFF9AA5CE),
            isDark = true
        )

        AppPalette.WARM_PAPER -> AppPaletteColors(
            palette = palette,
            background = Color(0xFFF7F3EB),
            surface = Color(0xFFFFFDF9),
            surfaceVariant = Color(0xFFEDE6D8),
            cardBorder = Color(0xFFC5B9A5),
            primary = Color(0xFF2B6CB0),
            primaryDim = Color(0xFF2C5282),
            secondary = Color(0xFF2C7A7B),
            statusSafe = Color(0xFF2F855A),
            statusSafeBg = Color(0xFFE6FFFA),
            statusWarning = Color(0xFFB7791F),
            statusWarningBg = Color(0xFFFEFCBF),
            statusAlert = Color(0xFFC53030),
            statusAlertBg = Color(0xFFFFF5F5),
            textPrimary = Color(0xFF231F1C),
            textSecondary = Color(0xFF4A433D),
            textMuted = Color(0xFF70675E),
            isDark = false
        )

        AppPalette.FOREST_SAGE -> AppPaletteColors(
            palette = palette,
            background = Color(0xFF151F1B),
            surface = Color(0xFF1D2B25),
            surfaceVariant = Color(0xFF273931),
            cardBorder = Color(0xFF3E594E),
            primary = Color(0xFF68D391),
            primaryDim = Color(0xFF2F855A),
            secondary = Color(0xFF4FD1C5),
            statusSafe = Color(0xFF68D391),
            statusSafeBg = Color(0xFF193529),
            statusWarning = Color(0xFFF6AD55),
            statusWarningBg = Color(0xFF332818),
            statusAlert = Color(0xFFFC8181),
            statusAlertBg = Color(0xFF381D21),
            textPrimary = Color(0xFFF0FDF4),
            textSecondary = Color(0xFFCBD5E1),
            textMuted = Color(0xFF94A3B8),
            isDark = true
        )

        AppPalette.OCEAN_BREEZE -> AppPaletteColors(
            palette = palette,
            background = Color(0xFFF0F6FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE1ECF7),
            cardBorder = Color(0xFF93B4D4),
            primary = Color(0xFF2563EB),
            primaryDim = Color(0xFF1D4ED8),
            secondary = Color(0xFF0891B2),
            statusSafe = Color(0xFF16A34A),
            statusSafeBg = Color(0xFFDCFCE7),
            statusWarning = Color(0xFFD97706),
            statusWarningBg = Color(0xFFFEF3C7),
            statusAlert = Color(0xFFDC2626),
            statusAlertBg = Color(0xFFFEE2E2),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF334155),
            textMuted = Color(0xFF64748B),
            isDark = false
        )

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
