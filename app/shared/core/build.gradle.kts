// :shared:core — pure Kotlin domain. No project dependencies, no Compose, no platform APIs (Constitution II).
// Its only libraries are the two Kotlin-only ones the constitution's stack names for the document model
// (feature 009, research R19): kotlinx.serialization and kotlinx.collections.immutable.
plugins {
    id("tembra.kmp.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.collections.immutable)
        }
    }
}
