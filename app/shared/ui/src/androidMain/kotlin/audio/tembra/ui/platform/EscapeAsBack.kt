package audio.tembra.ui.platform

import android.view.KeyEvent
import androidx.activity.ComponentActivity

/**
 * A hardware keyboard's Escape as the back action. Compose consumes Escape to move focus out, even with nothing
 * focused, so the platform's Escape-to-Back fallback never ran and a desktop window ignored the key (Pixel Tablet,
 * 2026-10-15). Call it first from `dispatchKeyEvent`: while back has somewhere to go, Escape goes back on release as the
 * back key does; on the start destination nothing handles back, so the key is left to Compose and never closes the app.
 * Pop-ups and dialogs are windows of their own and keep their Escape.
 */
fun ComponentActivity.dispatchEscapeAsBack(event: KeyEvent): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE || !onBackPressedDispatcher.hasEnabledCallbacks()) return false
    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
    return true
}
