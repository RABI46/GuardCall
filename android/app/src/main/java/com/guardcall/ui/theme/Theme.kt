package com.guardcall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème **bleu-blanc-rouge**.
 *
 * Palette calée sur le drapeau français :
 * - bleu  `#002395` (et sa variante `#0055A4` pour les accents),
 * - blanc `#FFFFFF` en fond et surfaces en thème clair,
 * - rouge `#ED2939` en couleur secondaire / accent.
 *
 * Le schéma reste un `ColorScheme` Material3 complet (containers, `outline`,
 * `error`…) : les vagues de teintes automatiques par défaut produisaient des
 * violets qui cassaient la cohérence du tricolore.
 *
 * Les teintes sombres sont désaturées plutôt qu'assombries, pour conserver un
 * contraste suffisant sur fond sombre (les couleurs de drapeau pures y sont
 * illisibles).
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF002395),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00134D),

    secondary = Color(0xFFED2939),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF5F0007),

    tertiary = Color(0xFF0055A4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF001A3D),

    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFF1F3F9),
    onSurfaceVariant = Color(0xFF42474E),

    outline = Color(0xFF72787F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C6FF),
    onPrimary = Color(0xFF00218A),
    primaryContainer = Color(0xFF0030A8),
    onPrimaryContainer = Color(0xFFDCE3FF),

    secondary = Color(0xFFFFB3AC),
    onSecondary = Color(0xFF8C0009),
    secondaryContainer = Color(0xFFB3261E),
    onSecondaryContainer = Color(0xFFFFDAD6),

    tertiary = Color(0xFF8FBCFF),
    onTertiary = Color(0xFF00325F),
    tertiaryContainer = Color(0xFF004A88),
    onTertiaryContainer = Color(0xFFD4E3FF),

    background = Color(0xFF101318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFFC2C7D0),

    outline = Color(0xFF8C9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun OnraccrocheTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
