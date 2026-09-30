// The thin platform I/O seam of the engine (research R3): Oboe on Android, RemoteIO on iOS, a null device on the
// macOS host, a synthetic device for test signals. Callbacks run on the platform's audio thread and must only copy.
#ifndef TEMBRA_PLATFORM_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_AUDIO_DEVICE_H

#include <cstdint>

namespace tembra {

/** Mono float frames captured by the input stream; [frames] varies per callback (Constitution I). */
using InputCallback = void (*)(void* context, const float* mono, uint32_t frames);

/** Interleaved stereo float frames the output stream needs; the callee fills all 2 × [frames] samples. */
using RenderCallback = void (*)(void* context, float* interleavedStereo, uint32_t frames);

class AudioDevice {
public:
    virtual ~AudioDevice() = default;

    /** Opens the input stream at [preferredRate] (the device may pick another; see sampleRate()). Returns a tembra_error. */
    virtual int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) = 0;

    /** Opens the output stream at [preferredRate]. Returns a tembra_error. */
    virtual int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) = 0;

    /** Stops and closes whichever stream is open; idempotent. */
    virtual void close() = 0;

    /** The actual rate of the open stream (the preferred rate before any stream opened). */
    virtual uint32_t sampleRate() const = 0;

    /** True after the platform closed the input stream on its own (device disconnected); the host re-opens. */
    virtual bool deviceLost() const = 0;

    /**
     * The last measured output latency in nanoseconds, 0 when unknown (research R7). A cached atomic: any non-audio
     * thread, never blocks (the beat light reads it every frame through tembra_metronome_read).
     */
    virtual int64_t outputLatencyNanos() const { return 0; }

    /**
     * Measures the output latency again and updates the cache. Control thread only: on Android it calls Oboe's
     * calculateLatencyMillis(), which is not safe on the data callback before Android R and may block.
     */
    virtual void refreshOutputLatency() {}

    /** Latency measured by the host (iOS: AVAudioSession.outputLatency + ioBufferDuration); ignored where the device measures it. */
    virtual void setReportedLatencyNanos(int64_t /*nanos*/) {}
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_AUDIO_DEVICE_H
