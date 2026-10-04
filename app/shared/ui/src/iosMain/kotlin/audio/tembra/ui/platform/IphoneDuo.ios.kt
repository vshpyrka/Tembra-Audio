package audio.tembra.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

/** The iPhone Duo's model identifier; the simulator reports the simulated model in its environment. */
private const val IPHONE_DUO_MODEL = "iPhone19,4"

internal actual val isIphoneDuo: Boolean by lazy {
    val simulated = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String
    (simulated ?: hardwareModel()) == IPHONE_DUO_MODEL
}

@OptIn(ExperimentalForeignApi::class)
private fun hardwareModel(): String? = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) != 0) null else info.machine.toKString()
}
