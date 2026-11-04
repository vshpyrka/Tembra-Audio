#include "platform/apple/remote_io_audio_device.h"

#include "tembra_engine.h"

namespace tembra {

namespace {

AudioStreamBasicDescription floatFormat(double sampleRate, UInt32 channels, bool interleaved) {
    AudioStreamBasicDescription format{};
    format.mSampleRate = sampleRate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked | (interleaved ? 0u : kAudioFormatFlagIsNonInterleaved);
    format.mBitsPerChannel = 32;
    format.mChannelsPerFrame = channels;
    format.mFramesPerPacket = 1;
    const UInt32 bytesPerFrame = interleaved ? 4 * channels : 4;
    format.mBytesPerFrame = bytesPerFrame;
    format.mBytesPerPacket = bytesPerFrame;
    return format;
}

}  // namespace

RemoteIoAudioDevice::~RemoteIoAudioDevice() { close(); }

bool RemoteIoAudioDevice::createUnit() {
    AudioComponentDescription description{};
    description.componentType = kAudioUnitType_Output;
    description.componentSubType = kAudioUnitSubType_RemoteIO;
    description.componentManufacturer = kAudioUnitManufacturer_Apple;
    AudioComponent component = AudioComponentFindNext(nullptr, &description);
    if (component == nullptr) return false;
    return AudioComponentInstanceNew(component, &unit_) == noErr && unit_ != nullptr;
}

double RemoteIoAudioDevice::hardwareRate(UInt32 bus, double fallback) const {
    AudioStreamBasicDescription format{};
    UInt32 size = sizeof(format);
    const AudioUnitScope scope = bus == kInputBus ? kAudioUnitScope_Input : kAudioUnitScope_Output;
    if (AudioUnitGetProperty(unit_, kAudioUnitProperty_StreamFormat, scope, bus, &format, &size) == noErr && format.mSampleRate > 0.0) {
        return format.mSampleRate;
    }
    return fallback;
}

int32_t RemoteIoAudioDevice::openInput(uint32_t preferredRate, InputCallback callback, void* context) {
    close();
    if (!createUnit()) return TEMBRA_ERR_DEVICE;
    input_ = callback;
    context_ = context;
    UInt32 enable = 1;
    UInt32 disable = 0;
    if (AudioUnitSetProperty(unit_, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &enable, sizeof(enable)) != noErr ||
        AudioUnitSetProperty(unit_, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, kOutputBus, &disable, sizeof(disable)) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    // The client format follows the hardware rate on the input bus (analysis L4); RemoteIO does not resample input.
    const double rate = hardwareRate(kInputBus, preferredRate);
    AudioStreamBasicDescription format = floatFormat(rate, 1, false);
    if (AudioUnitSetProperty(unit_, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &format, sizeof(format)) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    UInt32 maxFrames = kMaxFramesPerSlice;
    AudioUnitSetProperty(unit_, kAudioUnitProperty_MaximumFramesPerSlice, kAudioUnitScope_Global, 0, &maxFrames, sizeof(maxFrames));
    AURenderCallbackStruct input{};
    input.inputProc = &RemoteIoAudioDevice::inputProc;
    input.inputProcRefCon = this;
    if (AudioUnitSetProperty(unit_, kAudioOutputUnitProperty_SetInputCallback, kAudioUnitScope_Global, 0, &input, sizeof(input)) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    inputBuffer_.assign(kMaxFramesPerSlice, 0.0f);
    inputList_.mNumberBuffers = 1;
    inputList_.mBuffers[0].mNumberChannels = 1;
    inputList_.mBuffers[0].mDataByteSize = static_cast<UInt32>(inputBuffer_.size() * sizeof(float));
    inputList_.mBuffers[0].mData = inputBuffer_.data();
    if (AudioUnitInitialize(unit_) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    // Report the rate the unit settled on.
    UInt32 size = sizeof(format);
    if (AudioUnitGetProperty(unit_, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &format, &size) == noErr && format.mSampleRate > 0.0) {
        sampleRate_.store(static_cast<uint32_t>(format.mSampleRate));
    } else {
        sampleRate_.store(static_cast<uint32_t>(rate));
    }
    if (AudioOutputUnitStart(unit_) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

int32_t RemoteIoAudioDevice::openOutput(uint32_t preferredRate, RenderCallback callback, void* context) {
    close();
    if (!createUnit()) return TEMBRA_ERR_DEVICE;
    render_ = callback;
    context_ = context;
    // RemoteIO defaults to output enabled and input disabled, which is exactly the tone path.
    const double rate = hardwareRate(kOutputBus, preferredRate);
    // Non-interleaved (the canonical layout); the render callback splits the engine's interleaved stereo.
    AudioStreamBasicDescription format = floatFormat(rate, 2, false);
    outputScratch_.assign(kMaxFramesPerSlice * 2, 0.0f);
    if (AudioUnitSetProperty(unit_, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kOutputBus, &format, sizeof(format)) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    UInt32 maxFrames = kMaxFramesPerSlice;
    AudioUnitSetProperty(unit_, kAudioUnitProperty_MaximumFramesPerSlice, kAudioUnitScope_Global, 0, &maxFrames, sizeof(maxFrames));
    AURenderCallbackStruct render{};
    render.inputProc = &RemoteIoAudioDevice::renderProc;
    render.inputProcRefCon = this;
    if (AudioUnitSetProperty(unit_, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Global, 0, &render, sizeof(render)) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    // The bare simulator test process has no output unit and fails here (-10851); the simulator app and devices do not.
    if (AudioUnitInitialize(unit_) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    sampleRate_.store(static_cast<uint32_t>(rate));
    if (AudioOutputUnitStart(unit_) != noErr) {
        close();
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

void RemoteIoAudioDevice::close() {
    if (unit_ != nullptr) {
        AudioOutputUnitStop(unit_);
        AudioUnitUninitialize(unit_);
        AudioComponentInstanceDispose(unit_);
        unit_ = nullptr;
    }
    input_ = nullptr;
    render_ = nullptr;
    capture_ = nullptr;
    duplexChannels_.store(0);
}

// --- The duplex input on bus 1 (feature 010, research R3) --------------------------------------------------------

bool RemoteIoAudioDevice::configureDuplexLocked(bool enableInput) {
    // EnableIO changes only on an uninitialised unit: stop, reconfigure, initialise, start (the measured output gap).
    AudioOutputUnitStop(unit_);
    AudioUnitUninitialize(unit_);
    UInt32 enable = enableInput ? 1 : 0;
    if (AudioUnitSetProperty(unit_, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &enable, sizeof(enable)) != noErr) return false;
    if (enableInput) {
        // The client format on bus 1's output scope: non-interleaved float at the hardware rate, the input's own
        // channels up to the maximum (RemoteIO shares one rate across its buses).
        AudioStreamBasicDescription hardware{};
        UInt32 size = sizeof(hardware);
        uint32_t channels = duplexMaxChannels_;
        if (AudioUnitGetProperty(unit_, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kInputBus, &hardware, &size) == noErr && hardware.mChannelsPerFrame > 0) {
            if (hardware.mChannelsPerFrame < channels) channels = hardware.mChannelsPerFrame;
        }
        const double rate = static_cast<double>(sampleRate_.load());
        AudioStreamBasicDescription format = floatFormat(rate, channels, false);
        if (AudioUnitSetProperty(unit_, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &format, sizeof(format)) != noErr) return false;
        duplexPlanar_.assign(channels, std::vector<float>(kMaxFramesPerSlice, 0.0f));
        duplexListStorage_.assign(sizeof(AudioBufferList) + sizeof(AudioBuffer) * channels, 0);
        duplexScratch_.assign(static_cast<size_t>(kMaxFramesPerSlice) * channels, 0.0f);
        duplexChannels_.store(channels);
    } else {
        duplexChannels_.store(0);
    }
    if (AudioUnitInitialize(unit_) != noErr) return false;
    return AudioOutputUnitStart(unit_) == noErr;
}

int32_t RemoteIoAudioDevice::openInputBeside(uint32_t, uint32_t maxChannels, CaptureCallback callback, void* context) {
    if (unit_ == nullptr || render_ == nullptr) return TEMBRA_ERR_STATE;
    if (duplexChannels_.load() > 0) return TEMBRA_OK;  // idempotent
    duplexMaxChannels_ = maxChannels < 1 ? 1 : (maxChannels > 2 ? 2 : maxChannels);
    capture_ = callback;
    captureContext_ = context;
    if (!configureDuplexLocked(true)) {
        capture_ = nullptr;
        duplexChannels_.store(0);
        static_cast<void>(configureDuplexLocked(false));  // back to output-only, whatever happened
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

void RemoteIoAudioDevice::closeInput() {
    if (unit_ == nullptr || duplexChannels_.load() == 0) return;
    capture_ = nullptr;
    static_cast<void>(configureDuplexLocked(false));
}

uint32_t RemoteIoAudioDevice::sampleRate() const { return sampleRate_.load(); }

bool RemoteIoAudioDevice::deviceLost() const { return false; }

int64_t RemoteIoAudioDevice::outputLatencyNanos() const { return reportedLatencyNanos_.load(); }

void RemoteIoAudioDevice::setReportedLatencyNanos(int64_t nanos) { reportedLatencyNanos_.store(nanos); }

OSStatus RemoteIoAudioDevice::inputProc(void* refCon, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* timestamp, UInt32 bus,
                                        UInt32 frames, AudioBufferList*) {
    // Audio thread: render the captured frames into the pre-allocated buffer and hand them on (Constitution I).
    auto* self = static_cast<RemoteIoAudioDevice*>(refCon);
    if (frames > self->inputBuffer_.size()) frames = static_cast<UInt32>(self->inputBuffer_.size());
    self->inputList_.mBuffers[0].mDataByteSize = frames * sizeof(float);
    self->inputList_.mBuffers[0].mData = self->inputBuffer_.data();
    const OSStatus status = AudioUnitRender(self->unit_, flags, timestamp, bus, frames, &self->inputList_);
    if (status == noErr && self->input_ != nullptr) self->input_(self->context_, self->inputBuffer_.data(), frames);
    return status;
}

OSStatus RemoteIoAudioDevice::renderProc(void* refCon, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* timestamp, UInt32, UInt32 frames,
                                         AudioBufferList* ioData) {
    // Audio thread: the engine renders interleaved stereo into the pre-allocated scratch, split here into the unit's
    // planar buffers (or copied when the unit hands over one interleaved buffer). No allocation (Constitution I).
    auto* self = static_cast<RemoteIoAudioDevice*>(refCon);
    if (ioData == nullptr || ioData->mNumberBuffers == 0) return noErr;
    if (frames * 2 > self->outputScratch_.size()) frames = static_cast<UInt32>(self->outputScratch_.size() / 2);
    // Feature 010: pull the duplex input for the same frames first, interleave it and hand it to the capture callback.
    const uint32_t duplex = self->duplexChannels_.load(std::memory_order_acquire);
    if (duplex > 0 && self->capture_ != nullptr && !self->duplexListStorage_.empty()) {
        auto* list = reinterpret_cast<AudioBufferList*>(self->duplexListStorage_.data());
        list->mNumberBuffers = duplex;
        for (uint32_t c = 0; c < duplex; ++c) {
            list->mBuffers[c].mNumberChannels = 1;
            list->mBuffers[c].mDataByteSize = frames * sizeof(float);
            list->mBuffers[c].mData = self->duplexPlanar_[c].data();
        }
        const OSStatus status = AudioUnitRender(self->unit_, flags, timestamp, kInputBus, frames, list);
        float* interleaved = self->duplexScratch_.data();
        if (status == noErr) {
            for (UInt32 i = 0; i < frames; ++i) {
                for (uint32_t c = 0; c < duplex; ++c) interleaved[i * duplex + c] = self->duplexPlanar_[c][i];
            }
            self->capture_(self->captureContext_, interleaved, duplex, frames, 0);
        } else {
            for (size_t i = 0; i < static_cast<size_t>(frames) * duplex; ++i) interleaved[i] = 0.0f;
            self->capture_(self->captureContext_, interleaved, duplex, frames, frames);
        }
    }
    float* scratch = self->outputScratch_.data();
    if (self->render_ != nullptr) {
        self->render_(self->context_, scratch, frames);
    } else {
        for (UInt32 i = 0; i < frames * 2; ++i) scratch[i] = 0.0f;
    }
    if (ioData->mNumberBuffers >= 2) {
        float* left = static_cast<float*>(ioData->mBuffers[0].mData);
        float* right = static_cast<float*>(ioData->mBuffers[1].mData);
        for (UInt32 i = 0; i < frames; ++i) {
            left[i] = scratch[2 * i];
            right[i] = scratch[2 * i + 1];
        }
    } else {
        float* out = static_cast<float*>(ioData->mBuffers[0].mData);
        const UInt32 channels = ioData->mBuffers[0].mNumberChannels;
        for (UInt32 i = 0; i < frames; ++i) {
            for (UInt32 c = 0; c < channels; ++c) out[i * channels + c] = scratch[2 * i + (c < 2 ? c : 1)];
        }
    }
    return noErr;
}

}  // namespace tembra
