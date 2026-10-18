package audio.tembra.android

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On Android 13+ the metronome's "Running" notification and its Stop action show only with the notifications
 * permission, so the first time playback goes to the background service in a session the app asks for it once; a grant
 * re-posts the notification of the service already running.
 */
class NotificationPermissionHostTest {

    private val launched = mutableListOf<String>()
    private var granted = false
    private var reposts = 0

    private fun host(sdk: Int) = NotificationPermissionHost(sdk = sdk, onGranted = { reposts++ }).also {
        it.attach(isGranted = { granted }, launch = { permission -> launched += permission })
    }

    @Test
    fun asksOnceWhenPlaybackFirstGoesToTheBackground() {
        val host = host(sdk = 33)
        host.requestOnceForPlayback()
        host.requestOnceForPlayback()
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), launched)
    }

    @Test
    fun neverAsksBeforeAndroid13() {
        host(sdk = 32).requestOnceForPlayback()
        assertEquals(emptyList(), launched)
    }

    @Test
    fun neverAsksWhenNotificationsAreAllowed() {
        granted = true
        host(sdk = 33).requestOnceForPlayback()
        assertEquals(emptyList(), launched)
    }

    @Test
    fun withoutAnActivityItAsksLater() {
        val host = NotificationPermissionHost(sdk = 33, onGranted = {})
        host.requestOnceForPlayback()
        host.attach(isGranted = { granted }, launch = { permission -> launched += permission })
        host.requestOnceForPlayback()
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), launched)
    }

    @Test
    fun aGrantRepostsTheRunningNotificationAndADenialDoesNot() {
        val host = host(sdk = 33)
        host.requestOnceForPlayback()
        host.onResult(false)
        assertEquals(0, reposts)
        host.onResult(true)
        assertEquals(1, reposts)
    }
}
