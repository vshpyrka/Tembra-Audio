package audio.tembra.data.projects

import audio.tembra.core.project.ProjectDocument

/**
 * What [ProjectDocumentCodec.decode] answers: the document, or one of the named refusals ([DocumentTooNew] lives in
 * its own file). Anything but [Decoded] opens as an "unavailable project" with [failureReason] as the message.
 */
sealed interface DecodeResult {
    data class Decoded(val document: ProjectDocument) : DecodeResult

    /** Not JSON, not the envelope, or a value no type can hold. */
    data class Malformed(val message: String) : DecodeResult

    /** Well-formed but breaking a document rule (contracts/project-document.md, validation on read). */
    data class Invalid(val failures: List<ValidationFailure>) : DecodeResult
}

/** The user-facing reason a document could not be opened; null for a decoded one. */
fun DecodeResult.failureReason(): String? = when (this) {
    is DecodeResult.Decoded -> null
    is DecodeResult.Malformed -> message
    is DecodeResult.Invalid -> failures.joinToString("; ") { it.message }
    is DocumentTooNew -> "This project was saved by a newer version of the app (format $version)"
}
