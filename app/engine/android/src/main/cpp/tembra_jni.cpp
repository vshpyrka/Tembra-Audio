// JNI shim for audio.tembra.engine.NativeAudioEngine. Thin: every function forwards to the C ABI in
// tembra_engine.h and converts the result. Never called on an audio thread; nativeTunerRead and nativeMetronomeRead
// allocate nothing.
#include <jni.h>

#include "tembra_engine.h"

namespace {

tembra_tuner_config makeConfig(jint window, jint hop, jfloat minHz, jfloat maxHz, jfloat clarity, jfloat peakRatio,
                               jint median, jfloat watchdog) {
    tembra_tuner_config config{};
    config.window_frames = static_cast<uint32_t>(window);
    config.hop_frames = static_cast<uint32_t>(hop);
    config.min_hz = minHz;
    config.max_hz = maxHz;
    config.clarity_threshold = clarity;
    config.peak_ratio = peakRatio;
    config.median_size = static_cast<uint32_t>(median);
    config.watchdog_seconds = watchdog;
    return config;
}

}  // namespace

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

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerStart(JNIEnv*, jobject, jint window, jint hop, jfloat minHz,
                                                            jfloat maxHz, jfloat clarity, jfloat peakRatio, jint median,
                                                            jfloat watchdog) {
    const tembra_tuner_config config = makeConfig(window, hop, minHz, maxHz, clarity, peakRatio, median, watchdog);
    return static_cast<jint>(tembra_tuner_start(&config));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerStartTestSignal(JNIEnv*, jobject, jdouble hz, jfloat amplitude,
                                                                      jint harmonics, jfloat fundamentalGain, jint window,
                                                                      jint hop, jfloat minHz, jfloat maxHz, jfloat clarity,
                                                                      jfloat peakRatio, jint median, jfloat watchdog) {
    const tembra_tuner_config config = makeConfig(window, hop, minHz, maxHz, clarity, peakRatio, median, watchdog);
    tembra_test_signal signal{};
    signal.frequency_hz = hz;
    signal.amplitude = amplitude;
    signal.harmonics = static_cast<uint32_t>(harmonics);
    signal.fundamental_gain = fundamentalGain;
    return static_cast<jint>(tembra_tuner_start_test_signal(&config, &signal));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerStop(JNIEnv*, jobject) {
    tembra_tuner_stop();
}

JNIEXPORT jlong JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerRead(JNIEnv* env, jobject, jfloatArray out) {
    tembra_pitch_reading reading{};
    tembra_tuner_read(&reading);
    if (out != nullptr && env->GetArrayLength(out) >= 7) {
        const jfloat fields[7] = {
            static_cast<jfloat>(reading.state),
            static_cast<jfloat>(reading.sample_rate),
            reading.frequency_hz,
            reading.clarity,
            reading.input_peak,
            static_cast<jfloat>(reading.has_pitch),
            static_cast<jfloat>(reading.synthetic),
        };
        env->SetFloatArrayRegion(out, 0, 7, fields);
    }
    return static_cast<jlong>(reading.sequence);
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeToneStart(JNIEnv*, jobject, jdouble hz, jint durationMs) {
    return static_cast<jint>(tembra_tone_start(hz, static_cast<uint32_t>(durationMs)));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeSampleLoadWav(JNIEnv* env, jobject, jbyteArray bytes) {
    if (bytes == nullptr) return TEMBRA_ERR_ARGUMENT;
    const jsize length = env->GetArrayLength(bytes);
    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    if (data == nullptr) return TEMBRA_ERR_ARGUMENT;
    const int32_t result = tembra_sample_load_wav(reinterpret_cast<const uint8_t*>(data), static_cast<uint32_t>(length));
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return static_cast<jint>(result);
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeSamplePlay(JNIEnv*, jobject, jint handle, jboolean loop) {
    return static_cast<jint>(tembra_sample_play(handle, loop == JNI_TRUE ? 1u : 0u));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeSampleUnload(JNIEnv*, jobject, jint handle) {
    tembra_sample_unload(handle);
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeToneStop(JNIEnv*, jobject) {
    tembra_tone_stop();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeAudioState(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_audio_state());
}

// --- Metronome (ABI revision 3, specs/004-metronome/contracts/engine-c-abi-v3.md) ---

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeSetGrid(JNIEnv* env, jobject, jdouble bpm, jint ticksPerBeat,
                                                                  jint ticksPerBar, jbyteArray levels) {
    if (levels == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_metronome_grid grid{};
    grid.bpm = bpm;
    grid.ticks_per_beat = static_cast<uint32_t>(ticksPerBeat);
    grid.ticks_per_bar = static_cast<uint32_t>(ticksPerBar);
    const jsize available = env->GetArrayLength(levels);
    const jsize count = available < 32 ? available : 32;
    env->GetByteArrayRegion(levels, 0, count, reinterpret_cast<jbyte*>(grid.levels));
    return static_cast<jint>(tembra_metronome_set_grid(&grid));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeSelectSound(JNIEnv*, jobject, jint sound) {
    return static_cast<jint>(tembra_metronome_select_sound(static_cast<uint32_t>(sound)));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeLoadCustomSound(JNIEnv* env, jobject, jstring path, jintArray info,
                                                                          jfloatArray ms) {
    if (path == nullptr) return TEMBRA_ERR_ARGUMENT;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_custom_sound_info out{};
    const int32_t result = tembra_metronome_load_custom_sound(chars, &out);
    env->ReleaseStringUTFChars(path, chars);
    if (result == TEMBRA_OK) {
        if (info != nullptr && env->GetArrayLength(info) >= 4) {
            const jint fields[4] = {
                static_cast<jint>(out.source_rate),
                static_cast<jint>(out.source_channels),
                static_cast<jint>(out.source_bits),
                static_cast<jint>(out.kept_frames),
            };
            env->SetIntArrayRegion(info, 0, 4, fields);
        }
        if (ms != nullptr && env->GetArrayLength(ms) >= 2) {
            const jfloat fields[2] = {out.trimmed_ms, out.kept_ms};
            env->SetFloatArrayRegion(ms, 0, 2, fields);
        }
    }
    return static_cast<jint>(result);
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeUnloadCustomSound(JNIEnv*, jobject) {
    tembra_metronome_unload_custom_sound();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeStart(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_metronome_start());
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeStop(JNIEnv*, jobject) {
    tembra_metronome_stop();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomePreview(JNIEnv*, jobject, jint level) {
    return static_cast<jint>(tembra_metronome_preview(static_cast<uint32_t>(level)));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeSetOutputLatencyNs(JNIEnv*, jobject, jlong nanos) {
    tembra_metronome_set_output_latency_ns(static_cast<int64_t>(nanos));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeMetronomeRead(JNIEnv* env, jobject, jlongArray out) {
    // Called every frame by the beat light: fills the caller's array, allocates nothing.
    if (out == nullptr || env->GetArrayLength(out) < 7) return;
    tembra_metronome_tick tick{};
    tembra_metronome_read(&tick);
    const jlong fields[7] = {
        static_cast<jlong>(tick.tick_index),
        static_cast<jlong>(tick.beat_index),
        static_cast<jlong>(tick.level),
        static_cast<jlong>(tick.audible_in_ns),
        static_cast<jlong>(tick.beat_period_ns),
        static_cast<jlong>(tick.running),
        static_cast<jlong>(tick.sample_rate),
    };
    env->SetLongArrayRegion(out, 0, 7, fields);
}

}  // extern "C"
