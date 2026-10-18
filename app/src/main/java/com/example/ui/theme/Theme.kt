package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppTheme

private val NeoSerfDarkColors = darkColorScheme(
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
  onErrorContainer = Color(0xFFFFDAD6),
  surfaceDim = NeoBackground,
  surfaceBright = Color(0xFF383838),
  surfaceContainerLowest = Color(0xFF141414),
  surfaceContainerLow = NeoSurface,
  surfaceContainer = NeoSurfaceVariant,
  surfaceContainerHigh = Color(0xFF2D2D2D),
  surfaceContainerHighest = NeoSurfaceElevated
)

private val NeoSerfLightColors = lightColorScheme(
  primary = Color(0xFF166AD8),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD9E9FF),
  onPrimaryContainer = Color(0xFF12385E),
  secondary = Color(0xFF166AD8),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD9E9FF),
  onSecondaryContainer = Color(0xFF12385E),
  tertiary = Color(0xFF6545CC),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFE8DEFF),
  onTertiaryContainer = Color(0xFF30215C),
  background = Color(0xFFF3F3F3),
  onBackground = Color(0xFF1B1B1B),
  surface = Color(0xFFF3F3F3),
  onSurface = Color(0xFF1B1B1B),
  surfaceVariant = Color(0xFFE6E6E6),
  onSurfaceVariant = Color(0xFF606060),
  outline = Color(0xFFCBCBCB),
  outlineVariant = Color(0xFFDADADA),
  surfaceDim = Color(0xFFE1E1E1),
  surfaceBright = Color(0xFFFAFAFA),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF7F7F7),
  surfaceContainer = Color(0xFFEDEDED),
  surfaceContainerHigh = Color(0xFFE6E6E6),
  surfaceContainerHighest = Color(0xFFDDDDDD),
  error = Color(0xFFB3261E),
  onError = Color.White,
  errorContainer = Color(0xFFF9DEDC),
  onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun NeoSerfTheme(
  appTheme: AppTheme = AppTheme.SYSTEM,
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (appTheme.usesDarkColors(isSystemInDarkTheme())) NeoSerfDarkColors else NeoSerfLightColors,
    typography = Typography,
    content = content
  )
}
