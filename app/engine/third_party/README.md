# Vendored third-party libraries

Header-only, permissively licensed libraries compiled into the engine core (`src/third_party_check.cpp`).
Each folder keeps the upstream licence file. Oboe (Android only) is not vendored; it comes from Google
Maven through Prefab in `:engine:android`.

| Library | Version | Licence | Upstream |
|---|---|---|---|
| miniaudio | 0.11.25 | MIT-0 / public domain (dual) | https://github.com/mackron/miniaudio (`miniaudio.h`, `LICENSE`) |
| dr_wav (dr_libs) | 0.14.5 (tag `wav-0.14.5`) | MIT-0 / public domain (dual) | https://github.com/mackron/dr_libs (`dr_wav.h`, `LICENSE`) |
| Signalsmith Stretch | 1.1.0 (with its `dsp/` headers) | MIT | https://github.com/Signalsmith-Audio/signalsmith-stretch (`signalsmith-stretch.h`, `dsp/`, `LICENSE.txt`) |

Update procedure: replace the files with the tagged upstream versions, bump the row here and in
`app/THIRD_PARTY_LICENSES.md`, and let `engine_info_test.cpp` confirm the version strings.
