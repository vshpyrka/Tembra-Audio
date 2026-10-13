# Keep rules for the shrunk release-like builds of the Android app (feature 008, spec FR-005a).
#
# Policy: a rule is added here only when a device run on the shrunk build fails (a journey of the performance
# round, or a screenshot of a primary destination that differs from the debug build), with a comment naming
# the code path and the symptom it fixed. Nothing speculative, no `-dontwarn` wildcards: an R8 note is answered
# by a rule that names the class, or by a justification beside it.
#
# What the stack already brings, so it is not repeated here: `proguard-android-optimize.txt` keeps classes with
# `native` methods and their names (the JNI symbols of the engine bridge), AAPT keeps every manifest-declared
# component (MainActivity, MetronomeForegroundService, the profile installer's initialisation provider), Metro
# and Room ship their consumer rules in their artifacts, and Compose Multiplatform resources are static accessors
# over assets.
#
# Known candidates if a run fails (not pre-added): kotlinx.serialization keep rules for the `audio.tembra.**`
# serializable classes (the project document and the navigation keys); Room's `_Impl` constructors should the
# KMP constructor path fall back to reflection on Android.
