package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeoSerfColorScheme = darkColorScheme(
  primary = NeoBluePrimary,
  onPrimary = NeoOnBluePrimary,
  primaryContainer = NeoBlueContainer,
  onPrimaryContainer = NeoOnBlueContainer,
  secondary = NeoEmeraldSecondary,
  onSecondary = Color(0xFF06211A),
  secondaryContainer = NeoEmeraldContainer,
  onSecondaryContainer = NeoOnEmeraldContainer,
  tertiary = NeoIndigoTertiary,
  onTertiary = Color.White,
  tertiaryContainer = NeoIndigoContainer,
  onTertiaryContainer = NeoOnIndigoContainer,
  background = NeoBackground,
  onBackground = NeoTextPrimary,
  surface = NeoSurface,
  onSurface = NeoTextPrimary,
  surfaceVariant = NeoSurfaceVariant,
  onSurfaceVariant = NeoTextSecondary,
  outline = NeoOutline,
  error = NeoError,
  errorContainer = NeoErrorContainer,
  onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun NeoSerfTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = NeoSerfColorScheme,
    typography = Typography,
    content = content
  )
}
