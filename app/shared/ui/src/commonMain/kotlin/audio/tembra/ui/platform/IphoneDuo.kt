package audio.tembra.ui.platform

/**
 * Experiment (not in any spec): true on the iPhone Duo, whose closed outer display gets the end-edge rail
 * (LayoutMode.EndRail) and the metronome's Pocket form. Every other device keeps the size-class forms.
 */
internal expect val isIphoneDuo: Boolean
