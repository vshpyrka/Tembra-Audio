package audio.tembra.ui.perf

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable

@Composable
actual fun ReportFirstContentDrawn(ready: Boolean) = ReportDrawnWhen { ready }
