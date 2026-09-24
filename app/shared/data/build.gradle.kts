// :shared:data — persistence layer. In the scaffold it only exposes platform directories.
plugins {
    id("tembra.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
        }
    }
}
