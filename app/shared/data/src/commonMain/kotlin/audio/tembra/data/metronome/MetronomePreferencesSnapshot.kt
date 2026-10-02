package audio.tembra.data.metronome

import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.CustomSoundRef
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature

/**
 * The remembered metronome state (spec FR-026). Stored values are text; anything absent or unreadable decodes to
 * the field's default (contracts/metronome-preferences.md).
 */
data class MetronomePreferencesSnapshot(val settings: MetronomeSettings = MetronomeSettings()) {

    /** The stored keys as strings; `customName` and `customPath` are absent while no file is imported, and `style` while the levels were edited by hand. */
    fun encode(): Map<String, String> = buildMap {
        put(MetronomePreferenceKeys.TEMPO, settings.tempo.bpm.toString())
        put(MetronomePreferenceKeys.SIGNATURE, settings.signature.id)
        put(MetronomePreferenceKeys.SUBDIVISION, settings.subdivision.id)
        put(MetronomePreferenceKeys.LEVELS, settings.pattern.encode())
        settings.style?.let { put(MetronomePreferenceKeys.STYLE, it.id) }
        put(
            MetronomePreferenceKeys.SOUND,
            when (val sound = settings.sound) {
                is ClickSound.BuiltIn -> sound.sound.id
                is ClickSound.Custom -> CUSTOM_SOUND_ID
            },
        )
        settings.customSound?.let {
            put(MetronomePreferenceKeys.CUSTOM_NAME, it.displayName)
            put(MetronomePreferenceKeys.CUSTOM_PATH, it.path)
        }
    }

    companion object {
        private const val CUSTOM_SOUND_ID = "custom"

        /** Builds a snapshot from stored strings; unknown ids fall back to the defaults and the levels are re-fitted to the signature. */
        fun decode(values: Map<String, String?>): MetronomePreferencesSnapshot {
            val tempo = values[MetronomePreferenceKeys.TEMPO]?.toIntOrNull()
                ?.takeIf { it in Tempo.MIN..Tempo.MAX }
                ?.let(::Tempo) ?: Tempo.Default
            val signature = values[MetronomePreferenceKeys.SIGNATURE]?.let(TimeSignature::byId) ?: TimeSignature.Default
            val subdivision = values[MetronomePreferenceKeys.SUBDIVISION]?.let(Subdivision::byId) ?: Subdivision.Default
            val storedStyle = values[MetronomePreferenceKeys.STYLE]?.let(MetronomeStyle::byId)
            val stylePattern = (storedStyle ?: MetronomeStyle.Standard).pattern(signature)
            val pattern = values[MetronomePreferenceKeys.LEVELS]
                ?.let { AccentPattern.decode(it, signature.beats) } ?: stylePattern
            val style = storedStyle ?: MetronomeStyle.matching(pattern, signature)

            val name = values[MetronomePreferenceKeys.CUSTOM_NAME]
            val path = values[MetronomePreferenceKeys.CUSTOM_PATH]
            val customSound = if (name != null && path != null) CustomSoundRef(name, path) else null
            val soundId = values[MetronomePreferenceKeys.SOUND]
            val sound = when {
                soundId == CUSTOM_SOUND_ID && customSound != null -> ClickSound.Custom(customSound.displayName)
                else -> ClickSound.BuiltIn(BuiltInClickSound.entries.firstOrNull { it.id == soundId } ?: BuiltInClickSound.ClassicWood)
            }
            return MetronomePreferencesSnapshot(MetronomeSettings(tempo, signature, subdivision, pattern, style, sound, customSound))
        }
    }
}
