package dev.homepanel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HomePanelDarkColors = darkColorScheme(
    primary = Color(0xFF8CB4FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF173F73),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF8DD7C7),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF174E45),
    onSecondaryContainer = Color(0xFFAAEFE0),
    tertiary = Color(0xFFF2B66D),
    onTertiary = Color(0xFF432B00),
    tertiaryContainer = Color(0xFF604100),
    onTertiaryContainer = Color(0xFFFFDEAC),
    background = Color(0xFF0B1017),
    onBackground = Color(0xFFE1E7F0),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE1E7F0),
    surfaceVariant = Color(0xFF1A232E),
    onSurfaceVariant = Color(0xFFB9C4D0),
    outline = Color(0xFF65717E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5B1B19),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val HomePanelLightColors = lightColorScheme(
    primary = Color(0xFF245FAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF356B61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9EDE0),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF805600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA7),
    onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFE7ECF2),
    onSurfaceVariant = Color(0xFF414A55),
    outline = Color(0xFF717A86),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val HomePanelShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun HomePanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) HomePanelDarkColors else HomePanelLightColors,
        typography = Typography(),
        shapes = HomePanelShapes,
        content = content
    )
}
