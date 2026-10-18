package audio.tembra.ui.platform

import platform.Foundation.NSURL

/**
 * The security-scoped access a picked document needs on iOS (contracts/project-storage.md): begun before the copy
 * and ended after it. An interface so the picker's tests can inject a recording one.
 */
internal interface SecurityScopedAccess {
    /** True when access was granted and must be ended; false for a URL that needs none (a file of the app's own). */
    fun begin(url: NSURL): Boolean

    fun end(url: NSURL)

    /** The real thing: `startAccessingSecurityScopedResource` / `stopAccessingSecurityScopedResource`. */
    object System : SecurityScopedAccess {
        override fun begin(url: NSURL): Boolean = url.startAccessingSecurityScopedResource()
        override fun end(url: NSURL) = url.stopAccessingSecurityScopedResource()
    }
}
