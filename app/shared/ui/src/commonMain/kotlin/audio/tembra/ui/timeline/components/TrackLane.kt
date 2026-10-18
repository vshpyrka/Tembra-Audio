package audio.tembra.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ClipUi
import audio.tembra.ui.timeline.Selection
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TrackUi
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One track's clips (spec FR-016; contracts/timeline-screen.md): a single `Canvas` draws the faint bar lines and
 * every clip in view through [drawClip]; over it, one transparent node per visible clip carries the clip's semantics
 * ("drums, track 1, bar 1 beat 1, 31 seconds", `Role.Button`, selected) and its tap. A tap on empty lane clears the
 * selection. Tagged `timeline.lane.<index>`; clips `timeline.clip.first` then `timeline.clip.<n>` in project order.
 * Drag, long-press move and pinch arrive with User Stories 3 and 4.
 */
@Composable
internal fun TrackLane(
    track: TrackUi,
    index: Int,
    firstClipIndex: Int,
    viewport: TimelineViewport,
    selection: Selection,
    signature: TimeSignature,
    height: Dp,
    onSelectClip: (ClipId) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daw = TembraTheme.daw
    val colors = TembraTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val captionStyle = TembraTheme.typographyExtras.microLabel
    val style = remember(daw, colors, captionStyle, density) {
        ClipStyle(
            tints = daw.trackTints,
            captionBand = daw.clipCaptionBand,
            selectionOutline = daw.selectionOutline,
            unavailable = daw.unavailableClip,
            cornerRadiusPx = with(density) { TembraShapes.clip.toPx() },
            outlineWidthPx = with(density) { OUTLINE_WIDTH_DP.dp.toPx() },
            captionStyle = captionStyle,
            captionColor = colors.onSurface,
            placeholderColor = colors.outlineVariant,
        )
    }
    val tint = style.tintOf(track.colorIndex)
    val gridColor = daw.gridOverlayFaint
    val background = daw.canvasLevel0
    val selectedClipId = (selection as? Selection.ClipSelected)?.takeIf { it.trackId == track.id }?.clipId
    val heightPx = with(density) { height.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(TimelineTestTags.lane(index)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onClearSelection) { detectTapGestures(onTap = { onClearSelection() }) },
        ) {
            drawRect(background)
            val beatsPerBar = viewport.beatsPerBar.toDouble()
            var bar = floor(viewport.scrollBeats.value / beatsPerBar) * beatsPerBar
            while (true) {
                val x = viewport.xOf(Beats(bar)).toFloat()
                if (x > size.width) break
                if (x >= 0f) drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                bar += beatsPerBar
            }
            for (clip in track.clips) {
                val left = viewport.xOf(clip.startBeats).toFloat()
                val right = viewport.xOf(clip.endBeats).toFloat()
                if (right < 0f || left > size.width) continue
                drawClip(clip, left, right, tint, selected = clip.id == selectedClipId, style = style, measurer = measurer)
            }
        }
        track.clips.forEachIndexed { clipIndex, clip ->
            val left = viewport.xOf(clip.startBeats)
            val right = viewport.xOf(clip.endBeats)
            if (viewport.widthPx <= 0.0 || right < 0.0 || left > viewport.widthPx) return@forEachIndexed
            val description = clipDescription(clip, index, signature)
            val state = clipState(clip)
            val interaction = remember(clip.id) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width(with(density) { (right - left).toFloat().toDp() })
                    .height(with(density) { heightPx.toDp() })
                    .testTag(TimelineTestTags.clip(firstClipIndex + clipIndex))
                    .semantics {
                        contentDescription = description
                        selected = clip.id == selectedClipId
                        if (state != null) stateDescription = state
                    }
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onSelectClip(clip.id) },
            )
        }
    }
}

/** "drums, track 1, bar 1 beat 1, 31 seconds", with "unavailable" appended when the audio is missing (spec FR-058). */
internal fun clipDescription(clip: ClipUi, trackIndex: Int, signature: TimeSignature): String {
    val beatsPerBar = signature.beats
    val bar = floor(clip.startBeats.value / beatsPerBar).toInt() + 1
    val beat = floor(clip.startBeats.value - (bar - 1) * beatsPerBar).toInt() + 1
    val seconds = clip.lengthSeconds.roundToInt()
    val base = "${clip.name}, track ${trackIndex + 1}, bar $bar beat $beat, $seconds seconds"
    return if (clip.available) base else "$base, $UNAVAILABLE"
}

/** The overview's progress as a state for screen readers; null once drawn. */
internal fun clipState(clip: ClipUi): String? = when (val status = clip.overviewStatus) {
    is OverviewStatus.Building -> if (clip.available) "$LOADING_WAVEFORM ${(status.completedFraction * 100).roundToInt()} %" else null
    OverviewStatus.Ready, OverviewStatus.Failed -> null
}

internal const val UNAVAILABLE = "audio unavailable"
internal const val LOADING_WAVEFORM = "Loading waveform"
private const val OUTLINE_WIDTH_DP = 2
