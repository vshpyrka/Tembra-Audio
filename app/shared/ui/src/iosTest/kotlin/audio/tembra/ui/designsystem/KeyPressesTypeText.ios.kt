package audio.tembra.ui.designsystem

/** iOS text fields take text through UIKit's text input, which `performKeyInput` does not reach. */
internal actual val keyPressesTypeText: Boolean = false
