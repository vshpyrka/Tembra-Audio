package audio.tembra.core.project

/*
 * Derived values of the document (data-model.md section 1, "Derived values"): pure functions over a Project kept
 * beside the data holder as extensions so the serialisable classes stay plain (`kotlin-api-design`: project-owned
 * receivers, no policy, no state).
 */

/** The one-entry tempo map of the project. */
val Project.tempoMap: TempoMap get() = TempoMap(tempo, timeSignature)

/** A clip's length in beats at the current tempo; zero when its asset is missing from the document. */
fun Project.clipLengthBeats(clip: Clip): Beats {
    val asset = assets[clip.assetId] ?: return Beats.Zero
    return tempoMap.framesToBeats(clip.lengthFrames, asset.sampleRate)
}

/** Where a clip ends in beats at the current tempo. */
fun Project.clipEndBeats(clip: Clip): Beats = clip.startBeats + clipLengthBeats(clip)

/** The end of the last clip; zero for a project without clips (spec FR-010). */
val Project.lengthBeats: Beats
    get() = tracks.asSequence().flatMap { it.clips.asSequence() }.map { clipEndBeats(it) }.maxOrNull() ?: Beats.Zero

/** [lengthBeats] through the tempo map. */
val Project.lengthSeconds: Double get() = tempoMap.beatsToSeconds(lengthBeats)

/** The solo/mute truth table of spec FR-024: with any solo, the soloed and unmuted tracks; otherwise every unmuted one. */
val Project.audibleTracks: Set<TrackId>
    get() {
        val anySolo = tracks.any { it.soloed }
        return tracks.filter { !it.muted && (!anySolo || it.soloed) }.map { it.id }.toSet()
    }

/** Asset ids no clip references (deleted by the repository once the history no longer holds them). */
val Project.unusedAssets: Set<AudioAssetId>
    get() {
        val used = tracks.asSequence().flatMap { it.clips.asSequence() }.map { it.assetId }.toSet()
        return assets.keys.filterNot { it in used }.toSet()
    }

/** Clips across every track. */
val Project.clipCount: Int get() = tracks.sumOf { it.clips.size }

fun Project.findTrack(id: TrackId): Track? = tracks.firstOrNull { it.id == id }

fun Project.findClip(id: ClipId): Clip? = tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == id }

/** The track holding a clip. */
fun Project.trackOf(clipId: ClipId): Track? = tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
