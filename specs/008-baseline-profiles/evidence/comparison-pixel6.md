# Baseline profile comparison 20261012T165930Z_19021FDEE001AH

Device Pixel 6 Pro (19021FDEE001AH), build 232f0b62b682, states none, profile, 20 iterations per journey, journeys startup, tuner, metronome, stress. Change is `profile` vs `none`; confirmed means two-sided Mann–Whitney p < 0.01.

| metric | median none | median profile | change % | direction | confirmed |
|---|---|---|---|---|---|
| startup/startup.ttfd_ms | 184.5 | 151.3 | -18.0 | better | yes |
| startup/startup.ttid_ms | 184.5 | 151.3 | -18.0 | better | yes |
| tuner/journey.duration_ms | 1403.7 | 1425.3 | +1.5 | worse | no |
| tuner/memory.end_kb | 84256 | 84296 | +0.0 | worse | no |
| tuner/memory.peak_kb | 87408 | 86468 | -1.1 | better | no |
| tuner/section.tuner.frame.count | 18.0 | 17.0 | -5.6 | better | no |
| tuner/section.tuner.frame.sum_ms | 0.2 | 0.3 | +18.9 | worse | no |
| tuner/section.tuner.load.count | 1.0 | 1.0 | +0.0 | none | no |
| tuner/section.tuner.load.sum_ms | 70.7 | 72.6 | +2.8 | worse | no |
| tuner/section.tuner.modeSwitch.count | 2.0 | 2.0 | +0.0 | none | no |
| tuner/section.tuner.modeSwitch.sum_ms | 202.9 | 204.3 | +0.7 | worse | no |
| tuner/frames.p50_ms | 7.5 | 7.8 | +5.1 | worse | no |
| tuner/frames.p90_ms | 16.2 | 11.8 | -27.2 | better | yes |
| tuner/frames.p99_ms | 26.4 | 19.8 | -25.0 | better | yes |
| tuner/frames.overrun_p90_ms | 60.7 | 54.0 | -11.0 | better | no |
| metronome/journey.duration_ms | 1739.1 | 1802.9 | +3.7 | worse | no |
| metronome/memory.end_kb | 89798 | 87760 | -2.3 | better | no |
| metronome/memory.peak_kb | 94250 | 91982 | -2.4 | better | no |
| metronome/section.metronome.load.count | 1.0 | 1.0 | +0.0 | none | no |
| metronome/section.metronome.load.sum_ms | 0.1 | 0.1 | +14.3 | worse | yes |
| metronome/section.metronome.tempoChange.count | 10.0 | 10.0 | +0.0 | none | no |
| metronome/section.metronome.tempoChange.sum_ms | 3.1 | 2.9 | -4.9 | better | no |
| metronome/frames.p50_ms | 6.0 | 6.6 | +9.8 | worse | no |
| metronome/frames.p90_ms | 10.9 | 9.9 | -9.3 | better | yes |
| metronome/frames.p99_ms | 29.0 | 20.9 | -27.7 | better | yes |
| metronome/frames.overrun_p90_ms | -1.7 | -3.4 | -98.6 | better | yes |
| stress/journey.duration_ms | 4528.3 | 4478.3 | -1.1 | better | no |
| stress/memory.end_kb | 90884 | 90636 | -0.3 | better | no |
| stress/memory.peak_kb | 99084 | 99252 | +0.2 | worse | no |
| stress/section.metronome.load.count | 2.0 | 2.0 | +0.0 | none | no |
| stress/section.metronome.load.sum_ms | 0.1 | 0.1 | +14.4 | worse | yes |
| stress/section.metronome.tempoChange.count | 0.0 | 0.0 | +0.0 | none | no |
| stress/section.metronome.tempoChange.sum_ms | 0.0 | 0.0 | +0.0 | none | no |
| stress/section.tuner.frame.count | 31.0 | 31.0 | +0.0 | none | no |
| stress/section.tuner.frame.sum_ms | 0.5 | 0.8 | +58.8 | worse | yes |
| stress/section.tuner.load.count | 1.0 | 1.0 | +0.0 | none | no |
| stress/section.tuner.load.sum_ms | 0.1 | 0.1 | +9.5 | worse | no |
| stress/section.tuner.modeSwitch.count | 6.0 | 6.0 | +0.0 | none | no |
| stress/section.tuner.modeSwitch.sum_ms | 279.9 | 277.9 | -0.7 | better | no |
| stress/frames.p50_ms | 6.3 | 6.5 | +3.4 | worse | no |
| stress/frames.p90_ms | 9.8 | 9.9 | +1.1 | worse | no |
| stress/frames.p99_ms | 25.2 | 22.0 | -13.0 | better | yes |
| stress/frames.overrun_p90_ms | 2.7 | -0.6 | -124.3 | better | no |

startup.ttid_ms: 184.5 → 151.3 ms (-18.0 %, confirmed)
