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

/**
 * One input block of the duplex transport (feature 010, research R2, R3): [interleaved] holds [channels] × [frames]
 * floats read in the same device callback that is about to pull the output (zero-padded when the read was short by
 * [shortFrames]; 0 during the device's warm-up bursts). Delivered immediately before the render callback, on the
 * same thread, so the renderer mixes and captures the block its output corresponds to.
 */
using CaptureCallback = void (*)(void* context, const float* interleaved, uint32_t channels, uint32_t frames, uint32_t shortFrames);

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

    /** True when the open stream's audio thread holds real-time status (Oboe low-latency granted; RemoteIO always). */
    virtual bool realtime() const { return false; }

    /**
     * Underruns and overruns the platform stream counted since it opened (feature 007). Host threads only: Oboe's
     * getXRunCount() is not for the callback; the health getter samples it off the audio thread.
     */
    virtual uint32_t xrunCount() const { return 0; }

    // --- The duplex input beside an open output (feature 010, contracts/engine-c-abi-v7.md "Arming") ---------------

    /**
     * Opens the input at the output's [rate] with the device's channel count clamped to [maxChannels] (1 or 2), without
     * voice processing, while the output stays open and uninterrupted where the platform allows it (Android; iOS restarts
     * the unit). The block reaches [callback] before each render callback. Returns a tembra_error; control thread.
     */
    virtual int32_t openInputBeside(uint32_t /*rate*/, uint32_t /*maxChannels*/, CaptureCallback /*callback*/, void* /*context*/) { return -2; }

    /** Closes the duplex input, leaving the output open; idempotent. Control thread. */
    virtual void closeInput() {}

    /** The open duplex input's channel count; 0 while none is open. */
    virtual uint32_t inputChannels() const { return 0; }

    /** The input latency (capture → the callback) in nanoseconds, 0 when unknown; a cached atomic, never blocks. */
    virtual int64_t inputLatencyNanos() const { return 0; }

    /** The host-measured input latency (iOS); ignored where the device measures it. */
    virtual void setReportedInputLatencyNanos(int64_t /*nanos*/) {}

    /** The input stream's own xrun count since it opened (Android); host threads only. */
    virtual uint32_t inputXruns() const { return 0; }

    /** +1 every time the platform closed and reopened the input on its own; lock-free, read on the callback. */
    virtual uint32_t inputGeneration() const { return 0; }

    /** +1 every time the platform closed and reopened the output on its own; lock-free, read on the callback. */
    virtual uint32_t outputGeneration() const { return 0; }
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_AUDIO_DEVICE_H
