package ch.olivsoft.android.blindman

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun BlindManTheme(
    content: @Composable () -> Unit
) {
    val darkScheme = darkColorScheme(
        // Color changes
    )

    val typography = Typography(
        // Typography changes
    )

    MaterialTheme(
        colorScheme = darkScheme,
        typography = typography,
        content = content
    )
}
