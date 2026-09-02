package red.thugs.wardrive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Green = Color(0xFF3DDC97)
private val GreenDim = Color(0xFF2FB37E)
private val Red = Color(0xFFE64B5D)
private val Ink = Color(0xFF0B0E14)
private val Panel = Color(0xFF141925)
private val PanelHi = Color(0xFF1E2534)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Ink,
    secondary = GreenDim,
    onSecondary = Ink,
    tertiary = Red,
    onTertiary = Color.White,
    background = Ink,
    onBackground = Color(0xFFE6EAF2),
    surface = Panel,
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = PanelHi,
    onSurfaceVariant = Color(0xFF9AA4B8),
    error = Red,
    outline = Color(0xFF39425A),
)

private val LightColors = lightColorScheme(
    primary = GreenDim,
    tertiary = Red,
)

@Composable
fun WardriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
