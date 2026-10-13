# Baseline profile comparison 20261012T171426Z_19021FDEE001AH

Device Pixel 6 Pro (19021FDEE001AH), build 294f1e860106 (dirty), states none, profile, full, 20 iterations per journey, journeys startup, tuner, metronome, stress. Change is `profile` vs `none`; confirmed means two-sided Mann–Whitney p < 0.01.

| metric | median none | median profile | median full | change % | direction | confirmed | gap closed % |
|---|---|---|---|---|---|---|---|
| startup/startup.ttfd_ms | 182.0 | 153.5 | 182.4 | -15.7 | better | yes | -6960 |
| startup/startup.ttid_ms | 182.0 | 153.5 | 182.4 | -15.7 | better | yes | -6960 |
| tuner/journey.duration_ms | 1420.3 | 1396.5 | 1349.1 | -1.7 | better | no | 33 |
| tuner/memory.end_kb | 82236 | 83356 | 82886 | +1.4 | worse | no | 172 |
| tuner/memory.peak_kb | 85598 | 86462 | 86270 | +1.0 | worse | no | 129 |
| tuner/section.tuner.frame.count | 18.0 | 17.0 | 18.0 | -5.6 | better | no | — |
| tuner/section.tuner.frame.sum_ms | 0.3 | 0.3 | 0.2 | +21.3 | worse | no | -84 |
| tuner/section.tuner.load.count | 1.0 | 1.0 | 1.0 | +0.0 | none | no | — |
| tuner/section.tuner.load.sum_ms | 70.2 | 72.4 | 70.7 | +3.1 | worse | no | 417 |
| tuner/section.tuner.modeSwitch.count | 2.0 | 2.0 | 2.0 | +0.0 | none | no | — |
| tuner/section.tuner.modeSwitch.sum_ms | 204.3 | 202.8 | 204.0 | -0.8 | better | no | 485 |
| tuner/frames.p50_ms | 7.5 | 7.3 | 7.3 | -2.2 | better | no | 99 |
| tuner/frames.p90_ms | 16.2 | 11.5 | 11.3 | -29.3 | better | yes | 97 |
| tuner/frames.p99_ms | 26.5 | 19.4 | 18.8 | -26.6 | better | yes | 92 |
| tuner/frames.overrun_p90_ms | 61.1 | 53.7 | 52.0 | -12.2 | better | no | 82 |
| metronome/journey.duration_ms | 1773.2 | 1803.8 | 1816.1 | +1.7 | worse | no | 71 |
| metronome/memory.end_kb | 89822 | 86792 | 84930 | -3.4 | better | no | 62 |
| metronome/memory.peak_kb | 97416 | 92742 | 87486 | -4.8 | better | no | 47 |
| metronome/section.metronome.load.count | 1.0 | 1.0 | 1.0 | +0.0 | none | no | — |
| metronome/section.metronome.load.sum_ms | 0.1 | 0.1 | 0.0 | +5.1 | worse | no | -8 |
| metronome/section.metronome.tempoChange.count | 10.0 | 10.0 | 10.0 | +0.0 | none | no | — |
| metronome/section.metronome.tempoChange.sum_ms | 3.4 | 2.9 | 1.5 | -14.7 | better | yes | 26 |
| metronome/frames.p50_ms | 6.3 | 6.3 | 6.4 | -0.1 | better | no | -8 |
| metronome/frames.p90_ms | 11.2 | 9.3 | 9.3 | -16.6 | better | yes | 101 |
| metronome/frames.p99_ms | 28.1 | 20.1 | 18.8 | -28.6 | better | yes | 87 |
| metronome/frames.overrun_p90_ms | -1.6 | -3.9 | -4.4 | -135.0 | better | yes | 81 |
| stress/journey.duration_ms | 4515.8 | 4506.4 | 4416.1 | -0.2 | better | no | 9 |
| stress/memory.end_kb | 89894 | 90692 | 91350 | +0.9 | worse | no | 55 |
| stress/memory.peak_kb | 99408 | 100160 | 96334 | +0.8 | worse | no | -24 |
| stress/section.metronome.load.count | 2.0 | 2.0 | 2.0 | +0.0 | none | no | — |
| stress/section.metronome.load.sum_ms | 0.1 | 0.1 | 0.0 | +10.4 | worse | yes | -16 |
| stress/section.metronome.tempoChange.count | 0.0 | 0.0 | 0.0 | +0.0 | none | no | — |
| stress/section.metronome.tempoChange.sum_ms | 0.0 | 0.0 | 0.0 | +0.0 | none | no | — |
| stress/section.tuner.frame.count | 30.5 | 30.0 | 31.5 | -1.6 | better | no | -50 |
| stress/section.tuner.frame.sum_ms | 0.7 | 0.7 | 0.4 | +8.1 | worse | yes | -20 |
| stress/section.tuner.load.count | 1.0 | 1.0 | 1.0 | +0.0 | none | no | — |
| stress/section.tuner.load.sum_ms | 0.1 | 0.1 | 0.0 | +17.4 | worse | no | -25 |
| stress/section.tuner.modeSwitch.count | 6.0 | 6.0 | 6.0 | +0.0 | none | no | — |
| stress/section.tuner.modeSwitch.sum_ms | 279.9 | 278.0 | 272.2 | -0.7 | better | no | 25 |
| stress/frames.p50_ms | 6.4 | 6.8 | 6.7 | +6.1 | worse | no | 167 |
| stress/frames.p90_ms | 9.8 | 10.0 | 9.6 | +2.0 | worse | no | -104 |
| stress/frames.p99_ms | 25.2 | 22.7 | 22.1 | -10.0 | better | yes | 83 |
| stress/frames.overrun_p90_ms | 3.4 | -1.6 | -1.6 | -147.4 | better | yes | 101 |

startup.ttid_ms: 182.0 → 153.5 ms (-15.7 %, confirmed)
