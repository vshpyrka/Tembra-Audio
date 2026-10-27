package audio.tembra.data.projects

import kotlinx.serialization.json.JsonObject

/** One explicit step from [fromVersion] to the next version over the raw JSON envelope; the version stamp is the runner's job. */
class DocumentMigration(val fromVersion: Int, val transform: (JsonObject) -> JsonObject)
