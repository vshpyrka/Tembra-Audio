rootProject.name = "tembra"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared:core")
include(":shared:engine")
include(":shared:data")
include(":shared:ui")
include(":engine:android")
include(":androidApp")
// The Macrobenchmark module (feature 007) joins the build only for measurement runs so the default
// build and the test lanes never configure it: `./gradlew -Ptembra.perf=true :perf-benchmarks:assemblePerf`.
if (providers.gradleProperty("tembra.perf").isPresent) {
    include(":perf-benchmarks")
}
