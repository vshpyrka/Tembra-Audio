package audio.tembra.ui.shortcuts

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The single source of every shortcut (feature 011, spec FR-017 to FR-023, FR-018a; contracts/keyboard-shortcuts.md):
 * no chord is listed twice where it could fire (Everywhere plus any one destination), every entry has a label, and
 * every action is in the catalogue and has a target in the dispatcher. No chord can use Alt or Meta (both platforms
 * reserve them): [KeyChord] has no flag for either, and Command on iPad is the primary modifier.
 */
class ShortcutCatalogueTest {

    private val all = ShortcutCatalogue.all

    @Test
    fun noChordIsListedTwiceWhereItCouldFire() {
        for (scope in ShortcutScope.entries.filter { it != ShortcutScope.Everywhere }) {
            val reachable = all.filter { it.scope == ShortcutScope.Everywhere || it.scope == scope }
            val chords = reachable.flatMap { shortcut -> shortcut.chords.map { it to shortcut.action } }
            val duplicates = chords.groupBy { it.first }.filterValues { it.size > 1 }
            assertTrue(duplicates.isEmpty(), "$scope: chords listed twice: ${duplicates.keys}")
        }
    }

    @Test
    fun everyEntryHasALabelAndAChord() {
        for (shortcut in all) {
            assertTrue(shortcut.label.isNotBlank(), "${shortcut.action} has no label")
            assertTrue(shortcut.chords.isNotEmpty(), "${shortcut.action} has no chord")
        }
    }

    @Test
    fun everyActionIsInTheCatalogueOnce() {
        val listed = all.map { it.action }
        assertEquals(listed.toSet().size, listed.size, "an action is listed twice: $listed")
        assertEquals(ShortcutAction.entries.toSet(), listed.toSet(), "actions without a shortcut: ${ShortcutAction.entries - listed.toSet()}")
    }

    @Test
    fun everyActionReachesItsScopesTarget() {
        val reached = mutableListOf<ShortcutAction>()
        val targets = RecordingTargets { reached += it }
        for (action in ShortcutAction.entries) ShortcutDispatcher.dispatch(action, targets.all)
        assertEquals(ShortcutAction.entries.toList(), reached)
    }

    @Test
    fun theDestinationChordsArePrimaryOneToFiveAndTheSheetIsPrimarySlash() {
        val open = all.filter { it.scope == ShortcutScope.Everywhere }.associate { it.action to it.chords.first() }
        val digits = listOf(ShortcutAction.OpenTimeline, ShortcutAction.OpenTuner, ShortcutAction.OpenMetronome, ShortcutAction.OpenProjects, ShortcutAction.OpenProfile)
        digits.forEachIndexed { index, action ->
            assertEquals(KeyChord(ChordKey.Character('1' + index), primary = true), open.getValue(action), "$action")
        }
        assertEquals(KeyChord(ChordKey.Character('/'), primary = true), open.getValue(ShortcutAction.ShowShortcuts))
        // Escape, and ⌘. for the iPad's menu bar, where UIKit keeps Escape from the app.
        assertEquals(
            listOf(KeyChord(ChordKey.Named(Key.Escape)), KeyChord(ChordKey.Character('.'), primary = true)),
            all.single { it.action == ShortcutAction.CloseOverlay }.chords,
        )
    }

    @Test
    fun theIpadMenuServesEachEntrysFirstCommandChordAndComposeTheRest() {
        // A menu item has one key equivalent: Redo's ⇧⌘Z is the menu's, its ⌘Y stays with Compose; plain keys are Compose's.
        val ipad = ShortcutPlatform(PrimaryModifier.Meta, menuServesPrimaryChords = true)
        fun key(key: Key, char: Char, meta: Boolean = false, shift: Boolean = false) =
            KeyInput(key = key, codePoint = char.code, ctrl = false, meta = meta, shift = shift, alt = false, down = true)
        assertEquals(null, ShortcutCatalogue.match(key(Key.Z, 'z', meta = true, shift = true), ShortcutScope.Timeline, ipad))
        assertEquals(null, ShortcutCatalogue.match(key(Key.Z, 'z', meta = true), ShortcutScope.Timeline, ipad))
        assertEquals(ShortcutAction.TimelineRedo, ShortcutCatalogue.match(key(Key.Y, 'y', meta = true), ShortcutScope.Timeline, ipad))
        assertEquals(ShortcutAction.TimelineSplit, ShortcutCatalogue.match(key(Key.S, 's'), ShortcutScope.Timeline, ipad))
        val menu = all.mapNotNull { shortcut -> shortcut.menuChord?.let { shortcut.action to it } }
        assertTrue(menu.all { (_, chord) -> chord.primary }, "a menu chord without Command")
        assertEquals(all.count { shortcut -> shortcut.chords.any { it.primary } }, menu.size)
    }
}
