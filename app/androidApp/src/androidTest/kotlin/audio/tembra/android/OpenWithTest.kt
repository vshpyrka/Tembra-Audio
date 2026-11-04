package audio.tembra.android

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.ui.drop.DroppedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Open with" Tembra Audio from a file manager or ChromeOS Files (feature 011, spec FR-026a; research R22): the package
 * resolves `ACTION_VIEW` for WAV, FLAC and MP3 to [MainActivity], one view intent reaches [AndroidOpenedFiles] once with
 * every file it carries, and other intents bring nothing.
 */
class OpenWithTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun viewIntentsForAudioResolveToTheMainActivity() {
        for (type in listOf("audio/wav", "audio/x-wav", "audio/flac", "audio/mpeg")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://audio.tembra.test/a"), type).setPackage(context.packageName)
            @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int): the ResolveInfoFlags overload needs API 33; minSdk is 26.
            val found = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(type, listOf(ComponentName(context, MainActivity::class.java)), found.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) })
        }
    }

    @Test
    fun oneViewIntentReachesOpenedFilesOnceWithEveryFile() = runBlocking {
        val opened = AndroidOpenedFiles(context)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://audio.tembra.test/kick.wav"), "audio/wav")
        intent.clipData = ClipData.newRawUri("files", Uri.parse("content://audio.tembra.test/kick.wav")).apply {
            addItem(ClipData.Item(Uri.parse("content://audio.tembra.test/bass.flac")))
        }
        assertTrue(opened.deliver(intent))
        val items = withTimeout(5_000) { opened.files.first() }
        assertEquals(listOf("kick.wav", "bass.flac"), items.map { (it as DroppedItem.AudioFile).file.displayName })
        // A second request is a second emission, never a repeat of the first.
        assertTrue(opened.deliver(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://audio.tembra.test/hat.wav"), "audio/wav")))
        assertEquals(listOf("hat.wav"), withTimeout(5_000) { opened.files.take(1).toList() }.single().map { (it as DroppedItem.AudioFile).file.displayName })
    }

    @Test
    fun otherIntentsBringNothing() {
        val opened = AndroidOpenedFiles(context)
        assertFalse(opened.deliver(Intent(Intent.ACTION_MAIN)))
        assertFalse(opened.deliver(Intent(Intent.ACTION_VIEW)))
    }
}
