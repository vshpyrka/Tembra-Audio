package audio.tembra.ui.platform

import android.content.res.Resources
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge with system bars styled for the app's dark-only theme: light status and navigation icons whatever the
 * system theme. The default style follows the system's night mode, so a phone in the light system theme drew dark icons
 * on the dark app, and below API 29 a light scrim behind three-button navigation (OPPO CPH2239, Redmi Note 5,
 * 2026-10-15). These are the default's own styles as it applies them to a dark system.
 */
fun ComponentActivity.enableEdgeToEdgeOverDarkContent() {
    val alwaysDark: (Resources) -> Boolean = { true }
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, alwaysDark),
        navigationBarStyle = SystemBarStyle.auto(DefaultLightScrim, DefaultDarkScrim, alwaysDark),
    )
}

// androidx.activity's default scrims (1.13.0); only the dark one is used, below API 29 where the system enforces no contrast.
private val DefaultLightScrim = Color.argb(0xe6, 0xff, 0xff, 0xff)
private val DefaultDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
