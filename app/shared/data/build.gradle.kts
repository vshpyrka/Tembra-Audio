// :shared:data — persistence layer: platform directories and the DataStore-backed preferences (feature 003).
plugins {
    id("tembra.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.datastore.preferences.core)
        }
    }
}
