package pl.mmarkowebuty.admin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MmOrange = Color(0xFFF28C00)
val MmBg = Color(0xFF070707)
val MmCard = Color(0xFF111214)
val MmLine = Color(0xFF2B2E33)
val MmMuted = Color(0xFFA7ABB3)
val MmGreen = Color(0xFF67D49E)
val MmRed = Color(0xFFFF8D86)

private val Colors = darkColorScheme(
    primary = MmOrange,
    onPrimary = Color(0xFF151515),
    background = MmBg,
    onBackground = Color(0xFFF4F4F4),
    surface = MmCard,
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF191B1E),
    onSurfaceVariant = Color(0xFFC9CDD3),
    outline = MmLine,
    error = MmRed,
)

@Composable
fun MMarkoweButyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
