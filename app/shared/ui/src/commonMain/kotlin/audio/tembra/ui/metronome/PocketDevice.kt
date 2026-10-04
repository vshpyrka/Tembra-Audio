package audio.tembra.ui.metronome

/**
 * Experiment (not in any spec): true on the iPhone Duo, whose closed outer screen gets the [MetronomeLayout.Pocket]
 * form. Every other device keeps the size-class forms.
 */
internal expect val isIphoneDuo: Boolean
