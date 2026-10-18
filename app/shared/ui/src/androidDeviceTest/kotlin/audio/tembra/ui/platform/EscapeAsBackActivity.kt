package audio.tembra.ui.platform

import android.view.KeyEvent
import androidx.activity.ComponentActivity

/** A test host that routes key events through [dispatchEscapeAsBack] first, as MainActivity does. */
class EscapeAsBackActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = dispatchEscapeAsBack(event) || super.dispatchKeyEvent(event)
}
