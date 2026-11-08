package com.circapin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF21675D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F0E6), onPrimaryContainer = Color(0xFF103D34),
    secondary = Color(0xFF52685F), secondaryContainer = Color(0xFFE7EEEA), onSecondaryContainer = Color(0xFF263D33),
    tertiary = Color(0xFF52647F), tertiaryContainer = Color(0xFFE0E9F7), onTertiaryContainer = Color(0xFF20324B),
    background = Color(0xFFF4F7F5), onBackground = Color(0xFF18251F),
    surface = Color(0xFFF4F7F5), onSurface = Color(0xFF18251F), onSurfaceVariant = Color(0xFF53625A),
    surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFEDF2EF), surfaceContainerHighest = Color.White,
    outlineVariant = Color(0xFFDCE5DF), outline = Color(0xFF74857A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD9C8), onPrimary = Color(0xFF06382D),
    primaryContainer = Color(0xFF214C40), onPrimaryContainer = Color(0xFFD4F3E7),
    secondary = Color(0xFFB4CCBE), secondaryContainer = Color(0xFF2A3C33), onSecondaryContainer = Color(0xFFD7E8DE),
    tertiary = Color(0xFFB5C9E9), tertiaryContainer = Color(0xFF2F425C), onTertiaryContainer = Color(0xFFE1ECFC),
    background = Color(0xFF101813), onBackground = Color(0xFFE1EBE4),
    surface = Color(0xFF101813), onSurface = Color(0xFFE1EBE4), onSurfaceVariant = Color(0xFFB1C1B6),
    surfaceContainerLow = Color(0xFF1A251E), surfaceContainer = Color(0xFF1A251E), surfaceContainerHighest = Color(0xFF1E2C24),
    outlineVariant = Color(0xFF35473B), outline = Color(0xFF82978A),
)

@Composable
fun CircaPinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}
