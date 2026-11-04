package audio.tembra.android

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asking the system for full screen and back (feature 011, FR-010a, T141): the Desktop (Preview) image refuses a request
 * sent while the menu that asked for it is still closing ("Fullscreen request denied by system policy") and accepts the
 * same request a moment later, so a refusal while the window is still in the mode it is leaving is asked once more
 * after [RETRY_DELAY_MS]; a second refusal, or one after the window already changed, is reported.
 */
class FullScreenRequestTest {

    private val calls = mutableListOf<String>()
    private val outcomes = ArrayDeque<Boolean>()
    private var windowed = true
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()

    private val request = FullScreenRequest(
        send = { enter, onApproved, onRefused ->
            calls += if (enter) "send(enter)" else "send(exit)"
            if (outcomes.removeFirst()) onApproved() else onRefused(IllegalStateException("denied"))
        },
        later = { delayMs, action -> pending += delayMs to action },
        windowed = { windowed },
    )

    private fun run(enter: Boolean) = request.request(enter, onApproved = { calls += "approved" }, onRefused = { calls += "refused(${it.message})" })

    private fun runPending() {
        val due = pending.toList()
        pending.clear()
        due.forEach { (delay, action) -> calls += "after($delay)"; action() }
    }

    @Test
    fun anApprovedRequestIsSentOnce() {
        outcomes += true
        run(enter = true)
        assertEquals(listOf("send(enter)", "approved"), calls)
        assertEquals(emptyList(), pending)
    }

    @Test
    fun aRefusalWhileStillWindowedIsAskedOnceMoreAMomentLater() {
        outcomes += listOf(false, true)
        run(enter = true)
        assertEquals(listOf("send(enter)"), calls)
        runPending()
        assertEquals(listOf("send(enter)", "after($RETRY_DELAY_MS)", "send(enter)", "approved"), calls)
    }

    @Test
    fun aSecondRefusalIsReported() {
        outcomes += listOf(false, false)
        run(enter = true)
        runPending()
        assertEquals(listOf("send(enter)", "after($RETRY_DELAY_MS)", "send(enter)", "refused(denied)"), calls)
        assertEquals(emptyList(), pending)
    }

    @Test
    fun aRefusalAfterTheWindowAlreadyChangedIsReportedAtOnce() {
        // The window shell carried the request out and the callback still says refused (window animations off).
        outcomes += false
        windowed = false
        run(enter = true)
        assertEquals(listOf("send(enter)", "refused(denied)"), calls)
        assertEquals(emptyList(), pending)
    }

    @Test
    fun leavingFullScreenRetriesWhileTheWindowIsStillFullScreen() {
        windowed = false
        outcomes += listOf(false, true)
        run(enter = false)
        runPending()
        assertEquals(listOf("send(exit)", "after($RETRY_DELAY_MS)", "send(exit)", "approved"), calls)
    }
}
