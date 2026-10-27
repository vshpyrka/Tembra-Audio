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

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerSetGate(JNIEnv*, jobject, jboolean enabled, jfloat openDb) {
    return static_cast<jint>(tembra_tuner_set_gate(enabled == JNI_TRUE ? 1u : 0u, openDb));
}

JNIEXPORT jlong JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTunerRead(JNIEnv* env, jobject, jfloatArray out) {
    tembra_pitch_reading reading{};
    tembra_tuner_read(&reading);
    if (out != nullptr && env->GetArrayLength(out) >= 9) {
        const jfloat fields[9] = {
            static_cast<jfloat>(reading.state),
            static_cast<jfloat>(reading.sample_rate),
            reading.frequency_hz,
            reading.clarity,
            reading.input_peak,
            static_cast<jfloat>(reading.has_pitch),
            static_cast<jfloat>(reading.synthetic),
            reading.input_level_db,
            static_cast<jfloat>(reading.gate_open),
        };
        env->SetFloatArrayRegion(out, 0, 9, fields);
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
Java_audio_tembra_engine_NativeAudioEngine_nativeAudioHealth(JNIEnv* env, jobject, jlongArray out) {
    // Feature 007: fills [callbacks, lastUs, maxUs, overBudget, xruns, realtime, avgUs, starvation, clipsActive,
    // inputOverruns, recordBacklog, inputXruns] (avgUs..clipsActive since ABI revision 6, feature 009; the last three
    // since revision 7, feature 010); -1 while no stream is open.
    if (out == nullptr || env->GetArrayLength(out) < 12) return TEMBRA_ERR_ARGUMENT;
    tembra_audio_health health{};
    const int32_t result = tembra_engine_audio_health(&health);
    if (result != 0) return result;
    const jlong fields[12] = {
        static_cast<jlong>(health.callbacks), static_cast<jlong>(health.last_us), static_cast<jlong>(health.max_us),
        static_cast<jlong>(health.over_budget), static_cast<jlong>(health.xruns), static_cast<jlong>(health.realtime),
        static_cast<jlong>(health.avg_us), static_cast<jlong>(health.starvation), static_cast<jlong>(health.clips_active),
        static_cast<jlong>(health.input_overruns), static_cast<jlong>(health.record_backlog), static_cast<jlong>(health.input_xruns),
    };
    env->SetLongArrayRegion(out, 0, 12, fields);
    return 0;
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

// --- Transport, clip sources and offline tools (ABI revision 6, specs/009-timeline-arranger/contracts/engine-c-abi-v6.md) ---

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportOpen(JNIEnv*, jobject) {
    const tembra_transport_config config{};
    return static_cast<jint>(tembra_transport_open(&config));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportClose(JNIEnv*, jobject) {
    tembra_transport_close();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetTempo(JNIEnv*, jobject, jint bpm, jint beatsPerBar, jint beatUnit) {
    return static_cast<jint>(tembra_transport_set_tempo(static_cast<uint32_t>(bpm), static_cast<uint32_t>(beatsPerBar),
                                                        static_cast<uint32_t>(beatUnit)));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetArrangement(JNIEnv* env, jobject, jobject buffer) {
    // The arrangement crosses JNI as one direct ByteBuffer laid out as the C struct (no per-clip JNI calls).
    if (buffer == nullptr) return TEMBRA_ERR_ARGUMENT;
    void* address = env->GetDirectBufferAddress(buffer);
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (address == nullptr || capacity < static_cast<jlong>(sizeof(tembra_arrangement))) return TEMBRA_ERR_ARGUMENT;
    return static_cast<jint>(tembra_transport_set_arrangement(static_cast<const tembra_arrangement*>(address)));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetTrack(JNIEnv*, jobject, jint index, jfloat gain, jboolean audible) {
    return static_cast<jint>(tembra_transport_set_track(static_cast<uint32_t>(index), gain, audible ? 1u : 0u));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetMaster(JNIEnv*, jobject, jfloat gain) {
    return static_cast<jint>(tembra_transport_set_master(gain));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetMetronome(JNIEnv*, jobject, jboolean enabled) {
    return static_cast<jint>(tembra_transport_set_metronome(enabled ? 1u : 0u));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportPlay(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_transport_play());
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportPause(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_transport_pause());
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSeekBeats(JNIEnv*, jobject, jdouble beats) {
    return static_cast<jint>(tembra_transport_seek_beats(beats));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportRead(JNIEnv* env, jobject, jlongArray longs, jdoubleArray doubles) {
    // Fills longs [playing, positionFrames, blockTimeNs, latencyNs, sampleRate, countInBeatsLeft, recording] (the last two
    // since ABI revision 7, feature 010) and doubles [positionBeats, audibleSeconds] from one seqlock read; allocates
    // nothing (draw-phase caller).
    if (longs == nullptr || doubles == nullptr || env->GetArrayLength(longs) < 7 || env->GetArrayLength(doubles) < 2) return;
    tembra_transport_snapshot snapshot{};
    tembra_transport_read(&snapshot);
    const jlong l[7] = {
        static_cast<jlong>(snapshot.playing), static_cast<jlong>(snapshot.position_frames), static_cast<jlong>(snapshot.block_time_ns),
        static_cast<jlong>(snapshot.latency_ns), static_cast<jlong>(snapshot.sample_rate),
        static_cast<jlong>(snapshot.count_in_beats_left), static_cast<jlong>(snapshot.recording),
    };
    const jdouble d[2] = {snapshot.position_beats, snapshot.audible_seconds};
    env->SetLongArrayRegion(longs, 0, 7, l);
    env->SetDoubleArrayRegion(doubles, 0, 2, d);
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeClipSourceOpen(JNIEnv* env, jobject, jstring path, jintArray ints, jlongArray longs) {
    // Returns the handle (>= 0) or the error; fills ints [channels, sampleRate, format] and longs [frames].
    if (path == nullptr || ints == nullptr || longs == nullptr) return TEMBRA_ERR_ARGUMENT;
    if (env->GetArrayLength(ints) < 3 || env->GetArrayLength(longs) < 1) return TEMBRA_ERR_ARGUMENT;
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_clip_info info{};
    const int32_t result = tembra_clip_source_open(utf, &info);
    env->ReleaseStringUTFChars(path, utf);
    if (result < 0) return static_cast<jint>(result);
    const jint i[3] = {static_cast<jint>(info.channels), static_cast<jint>(info.sample_rate), static_cast<jint>(info.format)};
    const jlong f[1] = {static_cast<jlong>(info.frames)};
    env->SetIntArrayRegion(ints, 0, 3, i);
    env->SetLongArrayRegion(longs, 0, 1, f);
    return static_cast<jint>(result);
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeClipSourceClose(JNIEnv*, jobject, jint handle) {
    tembra_clip_source_close(static_cast<int32_t>(handle));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeAudioInspect(JNIEnv* env, jobject, jstring path, jintArray ints, jlongArray longs) {
    // Fills ints [format, channels, sampleRate] and longs [frames]; TEMBRA_ERR_DECODE for anything unreadable.
    if (path == nullptr || ints == nullptr || longs == nullptr) return TEMBRA_ERR_ARGUMENT;
    if (env->GetArrayLength(ints) < 3 || env->GetArrayLength(longs) < 1) return TEMBRA_ERR_ARGUMENT;
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_audio_info info{};
    const int32_t result = tembra_audio_inspect(utf, &info);
    env->ReleaseStringUTFChars(path, utf);
    if (result != TEMBRA_OK) return static_cast<jint>(result);
    const jint i[3] = {static_cast<jint>(info.format), static_cast<jint>(info.channels), static_cast<jint>(info.sample_rate)};
    const jlong f[1] = {static_cast<jlong>(info.frames)};
    env->SetIntArrayRegion(ints, 0, 3, i);
    env->SetLongArrayRegion(longs, 0, 1, f);
    return TEMBRA_OK;
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeAudioOverviewBuild(JNIEnv* env, jobject, jstring path, jstring outPath) {
    if (path == nullptr || outPath == nullptr) return TEMBRA_ERR_ARGUMENT;
    const char* source = env->GetStringUTFChars(path, nullptr);
    const char* target = env->GetStringUTFChars(outPath, nullptr);
    int32_t result = TEMBRA_ERR_ARGUMENT;
    if (source != nullptr && target != nullptr) result = tembra_audio_overview_build(source, target);
    if (source != nullptr) env->ReleaseStringUTFChars(path, source);
    if (target != nullptr) env->ReleaseStringUTFChars(outPath, target);
    return static_cast<jint>(result);
}

// --- Capture, takes and monitoring (ABI revision 7, specs/010-timeline-recording/contracts/engine-c-abi-v7.md) ---

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportArm(JNIEnv*, jobject, jint maxChannels) {
    tembra_capture_config config{};
    config.max_channels = static_cast<uint32_t>(maxChannels);
    return static_cast<jint>(tembra_transport_arm(&config));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportDisarm(JNIEnv*, jobject) {
    tembra_transport_disarm();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportSetMonitor(JNIEnv*, jobject, jboolean enabled, jint trackIndex) {
    if (trackIndex < 0) return TEMBRA_ERR_ARGUMENT;
    return static_cast<jint>(tembra_transport_set_monitor(enabled ? 1u : 0u, static_cast<uint32_t>(trackIndex)));
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeCaptureSetInputLatencyNs(JNIEnv*, jobject, jlong nanos) {
    tembra_capture_set_input_latency_ns(static_cast<int64_t>(nanos));
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportRecordStart(JNIEnv* env, jobject, jstring path, jint trackIndex, jint countInBeats,
                                                                      jlong maxFrames, jlong minFreeBytes) {
    if (path == nullptr || trackIndex < 0 || countInBeats < 0 || maxFrames < 0 || minFreeBytes < 0) return TEMBRA_ERR_ARGUMENT;
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_record_config config{};
    config.wav_path = utf;
    config.track_index = static_cast<uint32_t>(trackIndex);
    config.count_in_beats = static_cast<uint32_t>(countInBeats);
    config.max_frames = static_cast<int64_t>(maxFrames);
    config.min_free_bytes = static_cast<uint64_t>(minFreeBytes);
    const int32_t result = tembra_transport_record_start(&config);
    env->ReleaseStringUTFChars(path, utf);
    return static_cast<jint>(result);
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportRecordCancel(JNIEnv*, jobject) {
    return static_cast<jint>(tembra_transport_record_cancel());
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeTransportRecordStop(JNIEnv* env, jobject, jlongArray longs, jdoubleArray doubles, jintArray ints) {
    // Fills longs [startFrames, frames, alignmentFrames], doubles [startBeats] and ints [channels, sampleRate, stopReason].
    if (longs == nullptr || doubles == nullptr || ints == nullptr) return TEMBRA_ERR_ARGUMENT;
    if (env->GetArrayLength(longs) < 3 || env->GetArrayLength(doubles) < 1 || env->GetArrayLength(ints) < 3) return TEMBRA_ERR_ARGUMENT;
    tembra_take_info info{};
    const int32_t result = tembra_transport_record_stop(&info);
    if (result != TEMBRA_OK) return static_cast<jint>(result);
    const jlong l[3] = {static_cast<jlong>(info.start_frames), static_cast<jlong>(info.frames), static_cast<jlong>(info.alignment_frames)};
    const jdouble d[1] = {info.start_beats};
    const jint i[3] = {static_cast<jint>(info.channels), static_cast<jint>(info.sample_rate), static_cast<jint>(info.stop_reason)};
    env->SetLongArrayRegion(longs, 0, 3, l);
    env->SetDoubleArrayRegion(doubles, 0, 1, d);
    env->SetIntArrayRegion(ints, 0, 3, i);
    return TEMBRA_OK;
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeCaptureRead(JNIEnv* env, jobject, jlongArray longs, jfloatArray floats, jintArray ints) {
    // Fills ints [armed, recording, preRoll, monitoring, channels, sampleRate, clipped, stopReason, inputOverruns, backlogFrames],
    // floats [peak0, peak1] and longs [framesCaptured, takeStartFrames, alignmentFrames, inputLatencyNs, outputLatencyNs]
    // from one seqlock read; allocates nothing (draw-phase caller).
    if (longs == nullptr || floats == nullptr || ints == nullptr) return;
    if (env->GetArrayLength(longs) < 5 || env->GetArrayLength(floats) < 2 || env->GetArrayLength(ints) < 10) return;
    tembra_capture_snapshot snapshot{};
    tembra_capture_read(&snapshot);
    const jint i[10] = {
        static_cast<jint>(snapshot.armed), static_cast<jint>(snapshot.recording), static_cast<jint>(snapshot.pre_roll),
        static_cast<jint>(snapshot.monitoring), static_cast<jint>(snapshot.input_channels), static_cast<jint>(snapshot.sample_rate),
        static_cast<jint>(snapshot.clipped), static_cast<jint>(snapshot.stop_reason), static_cast<jint>(snapshot.input_overruns),
        static_cast<jint>(snapshot.backlog_frames),
    };
    const jfloat f[2] = {snapshot.peak[0], snapshot.peak[1]};
    const jlong l[5] = {
        static_cast<jlong>(snapshot.frames_captured), static_cast<jlong>(snapshot.take_start_frames), static_cast<jlong>(snapshot.alignment_frames),
        static_cast<jlong>(snapshot.input_latency_ns), static_cast<jlong>(snapshot.output_latency_ns),
    };
    env->SetIntArrayRegion(ints, 0, 10, i);
    env->SetFloatArrayRegion(floats, 0, 2, f);
    env->SetLongArrayRegion(longs, 0, 5, l);
}

JNIEXPORT void JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeCaptureResetClip(JNIEnv*, jobject) {
    tembra_capture_reset_clip();
}

JNIEXPORT jint JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeCaptureReadPeaks(JNIEnv* env, jobject, jlong fromIndex, jobject buffer, jint maxCount, jlongArray next) {
    // The buckets land in one direct ByteBuffer reused per instance (4 bytes each: min[2], max[2]); returns the count.
    if (buffer == nullptr || next == nullptr || maxCount < 0 || fromIndex < 0 || env->GetArrayLength(next) < 1) return 0;
    void* address = env->GetDirectBufferAddress(buffer);
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (address == nullptr || capacity < static_cast<jlong>(sizeof(tembra_peak_bucket))) return 0;
    const uint32_t fits = static_cast<uint32_t>(capacity / static_cast<jlong>(sizeof(tembra_peak_bucket)));
    const uint32_t wanted = static_cast<uint32_t>(maxCount) < fits ? static_cast<uint32_t>(maxCount) : fits;
    uint64_t nextIndex = 0;
    const uint32_t count = tembra_capture_read_peaks(static_cast<uint64_t>(fromIndex), static_cast<tembra_peak_bucket*>(address), wanted, &nextIndex);
    const jlong n[1] = {static_cast<jlong>(nextIndex)};
    env->SetLongArrayRegion(next, 0, 1, n);
    return static_cast<jint>(count);
}

JNIEXPORT jlong JNICALL
Java_audio_tembra_engine_NativeAudioEngine_nativeStorageFreeBytes(JNIEnv* env, jobject, jstring path) {
    if (path == nullptr) return -1;
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return -1;
    const int64_t result = tembra_storage_free_bytes(utf);
    env->ReleaseStringUTFChars(path, utf);
    return static_cast<jlong>(result);
}

}  // extern "C"
