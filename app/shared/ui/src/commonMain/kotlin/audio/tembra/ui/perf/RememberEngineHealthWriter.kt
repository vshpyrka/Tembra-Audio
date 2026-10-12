package audio.tembra.ui.perf

import androidx.compose.runtime.Composable

/**
 * The platform's writer for `engine-health.json`: the app's Documents directory on iOS (pulled with
 * `devicectl device copy from --domain-type appDataContainer`), the app files directory on Android.
 */
@Composable
internal expect fun rememberEngineHealthWriter(): EngineHealthWriter
