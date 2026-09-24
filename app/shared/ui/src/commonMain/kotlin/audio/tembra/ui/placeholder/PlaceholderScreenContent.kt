package audio.tembra.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.ui.theme.DesignTokenPreview
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/** Stateless placeholder screen (contracts/placeholder-screen.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreenContent(
    state: PlaceholderUiState,
    onButtonPressed: () -> Unit,
    windowWidth: WindowWidth,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val wide = windowWidth.isWide
    Scaffold(modifier = modifier, containerColor = TembraTheme.colors.background) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .then(if (wide) Modifier.widthIn(max = spacing.wideContentMaxWidth) else Modifier.fillMaxWidth())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.pageMargin, vertical = spacing.lg)
                    .testTag(if (wide) "placeholder.layout.wide" else "placeholder.layout.compact"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(
                    text = "Tembra Audio",
                    style = if (wide) TembraTheme.typography.headlineLarge else TembraTheme.typographyExtras.headlineLgMobile,
                    modifier = Modifier.testTag("placeholder.title"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Adds one to the counter") } },
                        state = rememberTooltipState(),
                    ) {
                        Button(
                            onClick = onButtonPressed,
                            modifier = Modifier
                                .sizeIn(minWidth = spacing.touchTargetMin, minHeight = spacing.touchTargetMin)
                                .testTag("placeholder.button")
                                .semantics { contentDescription = "Count up" },
                        ) {
                            Text("Count up")
                        }
                    }
                    Text(
                        text = "Pressed ${state.pressCount} times",
                        style = TembraTheme.typographyExtras.tabularTimecode,
                        modifier = Modifier
                            .testTag("placeholder.counter")
                            .semantics { stateDescription = state.pressCount.toString() },
                    )
                }
                Text(
                    text = state.engine.rawDescription,
                    style = TembraTheme.typography.bodySmall,
                    color = TembraTheme.colors.onSurfaceVariant,
                    modifier = Modifier.testTag("placeholder.engineCaption"),
                )
                DesignTokenPreview(modifier = Modifier.testTag("placeholder.tokenPreview"))
            }
        }
    }
}
