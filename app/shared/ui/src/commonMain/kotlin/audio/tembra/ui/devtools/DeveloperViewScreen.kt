package audio.tembra.ui.devtools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme

/** Full-screen host for a developer view inside the theme, with page margins and vertical scrolling. */
@Composable
fun DeveloperViewScreen(view: DeveloperView, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Scaffold(modifier = modifier, containerColor = TembraTheme.colors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.pageMargin, vertical = spacing.lg),
        ) {
            when (view) {
                DeveloperView.TokenPreview -> DesignTokenPreview(modifier = Modifier.testTag("devtools.tokenPreview"))
            }
        }
    }
}
