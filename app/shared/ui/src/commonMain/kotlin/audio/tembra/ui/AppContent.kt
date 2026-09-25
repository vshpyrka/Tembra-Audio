package audio.tembra.ui

import androidx.compose.runtime.Composable
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.devtools.DeveloperViewScreen

/** Graph-free branch between a requested developer view and the navigation [shell] (spec FR-022); testable without Metro. */
@Composable
fun AppContent(developerView: DeveloperViewRequest, shell: @Composable () -> Unit) {
    val view = developerView.view
    if (view != null) DeveloperViewScreen(view) else shell()
}
