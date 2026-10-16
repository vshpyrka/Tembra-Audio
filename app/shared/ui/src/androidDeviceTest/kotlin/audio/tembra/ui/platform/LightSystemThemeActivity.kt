package audio.tembra.ui.platform

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity

/** A test host that sees the light system theme whatever the device's own night mode. */
class LightSystemThemeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO })
    }
}
