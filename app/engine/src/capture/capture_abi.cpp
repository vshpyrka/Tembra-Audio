// The revision-7 C ABI capture functions (specs/010-timeline-recording/contracts/engine-c-abi-v7.md): thin forwarders
// to the EngineCore singleton with argument checks, plus the free-space probe. Never called on an audio callback.
// The arm is traced here as engine.takeAlignment (the engine core is a real-time unit and may not trace).
#include <cstring>
#include <sys/statvfs.h>

#include "core/engine_core.h"
#include "tembra_engine.h"
#include "tembra_trace.h"
#include "tembra_trace_names.h"

extern "C" {

int32_t tembra_transport_arm(const tembra_capture_config* config) {
    if (config == nullptr) return TEMBRA_ERR_ARGUMENT;
    TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_TAKE_ALIGNMENT);
    return tembra::EngineCore::instance().arm(*config);
}

void tembra_transport_disarm(void) {
    tembra::EngineCore::instance().disarm();
}

int32_t tembra_transport_set_monitor(uint32_t enabled, uint32_t track_index) {
    if (track_index >= TEMBRA_MAX_TRACKS) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setMonitor(enabled != 0, track_index);
}

void tembra_capture_set_input_latency_ns(int64_t nanos) {
    tembra::EngineCore::instance().setInputLatencyNs(nanos);
}

int32_t tembra_transport_record_start(const tembra_record_config* config) {
    if (config == nullptr || config->wav_path == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().recordStart(*config);
}

int32_t tembra_transport_record_cancel(void) {
    return tembra::EngineCore::instance().recordCancel();
}

int32_t tembra_transport_record_stop(tembra_take_info* out) {
    if (out == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().recordStop(*out);
}

void tembra_capture_read(tembra_capture_snapshot* out) {
    if (out == nullptr) return;
    tembra::EngineCore::instance().readCapture(*out);
}

void tembra_capture_reset_clip(void) {
    tembra::EngineCore::instance().resetClip();
}

uint32_t tembra_capture_read_peaks(uint64_t from_index, tembra_peak_bucket* out, uint32_t max_count, uint64_t* out_next_index) {
    return tembra::EngineCore::instance().readPeaks(from_index, out, max_count, out_next_index);
}

int64_t tembra_storage_free_bytes(const char* path) {
    if (path == nullptr) return -1;
    struct statvfs stat {};
    if (statvfs(path, &stat) != 0) return -1;
    return static_cast<int64_t>(stat.f_bavail) * static_cast<int64_t>(stat.f_frsize);
}

}  // extern "C"
