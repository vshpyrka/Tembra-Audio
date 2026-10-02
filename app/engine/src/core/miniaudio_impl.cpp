// The one translation unit that compiles miniaudio's implementation (research R16). The engine uses only its
// resampler (SoundResampler, research R4): no device I/O, decoding, encoding or generation. CMakeLists.txt defines the
// same four switches for every engine source; they are repeated here so this unit states what it builds.
#ifndef MA_NO_DEVICE_IO
#define MA_NO_DEVICE_IO
#endif
#ifndef MA_NO_DECODING
#define MA_NO_DECODING
#endif
#ifndef MA_NO_ENCODING
#define MA_NO_ENCODING
#endif
#ifndef MA_NO_GENERATION
#define MA_NO_GENERATION
#endif
// dr_wav is vendored separately at the version we pin (third_party_check.cpp); miniaudio's bundled decoders stay out.
#define MA_NO_WAV
#define MA_NO_FLAC
#define MA_NO_MP3
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
