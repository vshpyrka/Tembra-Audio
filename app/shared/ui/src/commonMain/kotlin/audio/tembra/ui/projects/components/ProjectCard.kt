package audio.tembra.ui.projects.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.designsystem.TembraCard
import audio.tembra.ui.designsystem.TembraPill
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.projects.ProjectCardUi
import audio.tembra.ui.projects.ProjectsCallbacks
import audio.tembra.ui.projects.ProjectsCopy
import audio.tembra.ui.projects.ProjectsLayout
import audio.tembra.ui.projects.ProjectsTestTags
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportButton

/**
 * One project (spec FR-051, FR-052; contracts/projects-library-screen.md): the badges and the relative time, the
 * name, the description, the overview in the card's dark well, the stats row, the footer with the size and the
 * "Resume" / "Open" pill, and the menu. The card is one `Role.Button` with a merged description; a hover elevates it.
 * Tagged `projects.card.<id>`, the demo's `projects.card.demo` (the journey's anchor).
 */
@Composable
internal fun ProjectCard(
    card: ProjectCardUi,
    layout: ProjectsLayout,
    callbacks: ProjectsCallbacks,
    modifier: Modifier = Modifier,
) {
    // The demo reads `projects.card.demo` whatever its id (a journey anchor); every child tag hangs off the card's tag.
    val cardTag = if (card.isDemo) ProjectsTestTags.CARD_DEMO else ProjectsTestTags.card(card.id)
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val menuSource = remember { MutableInteractionSource() }
    var menuOpen by remember { mutableStateOf(false) }
    val description = card.accessibilityDescription
    TembraCard(
        modifier = modifier
            .hoverable(source)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { callbacks.onOpen(card.id) },
        elevated = hovered,
        testTag = cardTag,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (card.isActive) ProjectBadge(ProjectsCopy.active, accent = true)
                if (card.isDemo) ProjectBadge(ProjectsCopy.demo, accent = false)
                Text(card.relativeTime, style = TembraTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.weight(1f))
                ProjectCardMenu(
                    expanded = menuOpen,
                    isDemo = card.isDemo,
                    onDismiss = { menuOpen = false },
                    onRename = { callbacks.onRename(card.id) },
                    onDescribe = { callbacks.onDescribe(card.id) },
                    onKey = { callbacks.onKey(card.id) },
                    onDuplicate = { callbacks.onDuplicate(card.id) },
                    onDelete = { callbacks.onDelete(card.id) },
                    onRestoreDemo = callbacks.onRestoreDemo,
                    testTag = "$cardTag.menu",
                ) {
                    TembraTooltip(text = ProjectsCopy.menu, interactionSource = menuSource) {
                        TransportButton(
                            icon = TembraIcon.MoreVert,
                            description = ProjectsCopy.menu,
                            onClick = { menuOpen = true },
                            interactionSource = menuSource,
                            testTag = "$cardTag.menu.button",
                        )
                    }
                }
            }
            Text(card.name, style = TembraTheme.typography.headlineSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.description.isNotBlank()) {
                Text(card.description, style = TembraTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProjectOverviewChart(overview = card.overview, asLine = layout.overviewAsLine, testTag = "$cardTag.overview")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                ProjectStat(card.tempoText)
                ProjectStat(card.keyText)
                ProjectStat(card.lengthText)
                ProjectStat(card.tracksText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.sizeText, style = TembraTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.weight(1f))
                TembraPill(
                    text = if (card.isActive) ProjectsCopy.resume else ProjectsCopy.open,
                    selected = card.isActive,
                    onClick = { callbacks.onOpen(card.id) },
                    testTag = "$cardTag.open",
                )
            }
        }
    }
}
