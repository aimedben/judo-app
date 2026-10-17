package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Brand light theme: clean near-white surfaces with the Judo red / navy / gold identity.
 * Background is a very light neutral so cards and the header (pure white) stand out.
 */
private val LightColorScheme = lightColorScheme(
  primary = JudoRed,
  onPrimary = Color.White,
  primaryContainer = JudoRedContainer,
  onPrimaryContainer = JudoRedOnContainer,
  secondary = JudoNavy,
  onSecondary = Color.White,
  secondaryContainer = JudoNavyContainer,
  onSecondaryContainer = JudoNavyOnContainer,
  tertiary = JudoGoldDark,
  onTertiary = Color.White,
  tertiaryContainer = JudoGoldContainer,
  onTertiaryContainer = JudoGoldOnContainer,

  background = JudoLightBackground,
  onBackground = JudoLightOnSurface,
  surface = JudoLightSurface,
  onSurface = JudoLightOnSurface,
  surfaceVariant = JudoLightSurfaceVariant,
  onSurfaceVariant = JudoLightOnSurfaceVariant,
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFFBFAFC),
  surfaceContainer = Color(0xFFF3F1F4),
  surfaceContainerHigh = Color(0xFFEDEBEF),
  surfaceContainerHighest = Color(0xFFE7E5E9),

  outline = Color(0xFF8B8A90),
  outlineVariant = Color(0xFFDCDADF),
  error = Color(0xFFB3261E),
  onError = Color.White,
  errorContainer = Color(0xFFF9DEDC),
  onErrorContainer = Color(0xFF410E0B),
  inverseSurface = Color(0xFF303033),
  inverseOnSurface = Color(0xFFF3F0F4),
)

/**
 * Brand dark theme: deep neutral surfaces with lighter red / gold accents for contrast.
 */
private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFFFB4AB),
  onPrimary = Color(0xFF690005),
  primaryContainer = Color(0xFF93000A),
  onPrimaryContainer = Color(0xFFFFDAD6),
  secondary = Color(0xFFB8C3FF),
  onSecondary = Color(0xFF161E5A),
  secondaryContainer = Color(0xFF2E3672),
  onSecondaryContainer = Color(0xFFE0E0FF),
  tertiary = JudoGold,
  onTertiary = Color(0xFF3A2300),
  tertiaryContainer = Color(0xFF6B4A00),
  onTertiaryContainer = JudoGoldContainer,

  background = Color(0xFF131215),
  onBackground = Color(0xFFE6E1E6),
  surface = Color(0xFF131215),
  onSurface = Color(0xFFE6E1E6),
  surfaceVariant = Color(0xFF2A282D),
  onSurfaceVariant = Color(0xFFC9C5CE),
  surfaceContainerLowest = Color(0xFF0E0D10),
  surfaceContainerLow = Color(0xFF1B1A1E),
  surfaceContainer = Color(0xFF1F1E23),
  surfaceContainerHigh = Color(0xFF2A292E),
  surfaceContainerHighest = Color(0xFF35343A),
  outline = Color(0xFF948F99),
  outlineVariant = Color(0xFF47454C),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690001),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
  scrim = Color(0xFF000000)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic (Material You) color is disabled by default so the club's brand identity
  // (Judo red / gold) stays consistent instead of following the device wallpaper.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
