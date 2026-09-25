// :androidApp — thin Android shell: Application (Metro graph) and MainActivity (Compose host).
plugins {
    id("tembra.android.app")
}

android {
    namespace = "audio.tembra.android"
    defaultConfig {
        applicationId = "audio.tembra.app"
        versionCode = 1
        versionName = "0.1.0"
    }
    // BuildConfig.DEBUG gates the developer-view launch argument (feature 002, FR-022).
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.metrox.viewmodel.compose)
}
