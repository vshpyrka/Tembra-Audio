// JNI shim for audio.tembra.engine.NativeAudioEngine. Thin: every function forwards to the C ABI in
// tembra_engine.h and converts the result. Never called on an audio thread.
#include <jni.h>

#include "tembra_engine.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeAbiVersion(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_engine_abi_version());
}

JNIEXPORT jstring JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeName(JNIEnv* env, jobject) {
    return env->NewStringUTF(tembra_engine_name());
}

JNIEXPORT jstring JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(tembra_engine_version());
}

JNIEXPORT jstring JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeDescribe(JNIEnv* env, jobject) {
    return env->NewStringUTF(tembra_engine_describe());
}

}  // extern "C"
