// :shared:data — persistence layer: platform directories, the DataStore-backed preferences (feature 003) and the
// project folders, library index and import pipeline (feature 009).
plugins {
    id("tembra.kmp.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // The import pipeline inspects files and builds overviews through the engine's offline tools (feature 009, research R10, R11).
            implementation(project(":shared:engine"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.okio.fakefilesystem)
        }
    }
}
