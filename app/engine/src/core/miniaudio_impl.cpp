// The one translation unit that compiles miniaudio's implementation (research R16 of feature 004; R5 of feature 009).
// The engine uses its resampler (SoundResampler) and, since feature 009, its bundled WAV, FLAC and MP3 decoders for the
// clip sources, the offline inspector and the overview builder: no device I/O, encoding or generation.
// CMakeLists.txt defines the same switches for every engine source; they are repeated here so this unit states what
// it builds.
#ifndef MA_NO_DEVICE_IO
#define MA_NO_DEVICE_IO
#endif
#ifndef MA_NO_ENCODING
#define MA_NO_ENCODING
#endif
#ifndef MA_NO_GENERATION
#define MA_NO_GENERATION
#endif
// miniaudio's decoders are its own ma_dr_wav / ma_dr_flac / ma_dr_mp3 copies (namespaced symbols), so they coexist
// with the dr_wav vendored separately at the version we pin (third_party_check.cpp).
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
