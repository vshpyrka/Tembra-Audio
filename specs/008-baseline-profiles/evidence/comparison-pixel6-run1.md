# Baseline profile comparison 20261012T164501Z_19021FDEE001AH

Device Pixel 6 Pro (19021FDEE001AH), build 7ba60bedc9ad, states none, profile, 20 iterations per journey, journeys startup, tuner, metronome, stress. Change is `profile` vs `none`; confirmed means two-sided Mann–Whitney p < 0.01.

| metric | median none | median profile | change % | direction | confirmed |
|---|---|---|---|---|---|
| startup/startup.ttfd_ms | 182.7 | 155.9 | -14.7 | better | yes |
| startup/startup.ttid_ms | 182.7 | 155.9 | -14.7 | better | yes |
| tuner/journey.duration_ms | 1393.2 | 1378.8 | -1.0 | better | no |
| tuner/memory.end_kb | 84398 | 82838 | -1.8 | better | no |
| tuner/memory.peak_kb | 86206 | 87434 | +1.4 | worse | no |
| tuner/section.tuner.frame.count | 17.0 | 17.0 | +0.0 | none | no |
| tuner/section.tuner.frame.sum_ms | 0.2 | 0.3 | +9.0 | worse | no |
| tuner/section.tuner.load.count | 1.0 | 1.0 | +0.0 | none | no |
| tuner/section.tuner.load.sum_ms | 70.1 | 70.7 | +0.8 | worse | no |
| tuner/section.tuner.modeSwitch.count | 2.0 | 2.0 | +0.0 | none | no |
| tuner/section.tuner.modeSwitch.sum_ms | 202.1 | 206.4 | +2.1 | worse | no |
| tuner/frames.p50_ms | 7.3 | 7.2 | -1.7 | better | no |
| tuner/frames.p90_ms | 16.2 | 11.9 | -26.7 | better | yes |
| tuner/frames.p99_ms | 28.5 | 20.3 | -28.9 | better | yes |
| tuner/frames.overrun_p90_ms | 61.4 | 53.2 | -13.4 | better | no |
| metronome/journey.duration_ms | 1793.9 | 1777.3 | -0.9 | better | no |
| metronome/memory.end_kb | 85360 | 86432 | +1.3 | worse | no |
| metronome/memory.peak_kb | 86044 | 89106 | +3.6 | worse | no |
| metronome/section.metronome.load.count | 1.0 | 1.0 | +0.0 | none | no |
| metronome/section.metronome.load.sum_ms | 0.1 | 0.1 | +4.5 | worse | no |
| metronome/section.metronome.tempoChange.count | 10.0 | 10.0 | +0.0 | none | no |
| metronome/section.metronome.tempoChange.sum_ms | 3.1 | 2.9 | -8.7 | better | yes |
| metronome/frames.p50_ms | 6.1 | 6.5 | +6.6 | worse | no |
| metronome/frames.p90_ms | 10.7 | 10.0 | -6.4 | better | no |
| metronome/frames.p99_ms | 28.0 | 20.0 | -28.8 | better | yes |
| metronome/frames.overrun_p90_ms | -1.4 | -3.6 | +157.8 | worse | yes |
| stress/journey.duration_ms | 4474.8 | 4534.9 | +1.3 | worse | no |
| stress/memory.end_kb | 90578 | 92570 | +2.2 | worse | no |
| stress/memory.peak_kb | 96970 | 103582 | +6.8 | worse | no |
| stress/section.metronome.load.count | 2.0 | 2.0 | +0.0 | none | no |
| stress/section.metronome.load.sum_ms | 0.1 | 0.1 | +7.4 | worse | no |
| stress/section.metronome.tempoChange.count | 0.0 | 0.0 | +0.0 | none | no |
| stress/section.metronome.tempoChange.sum_ms | 0.0 | 0.0 | +0.0 | none | no |
| stress/section.tuner.frame.count | 31.5 | 30.0 | -4.8 | better | no |
| stress/section.tuner.frame.sum_ms | 0.5 | 0.7 | +50.0 | worse | yes |
| stress/section.tuner.load.count | 1.0 | 1.0 | +0.0 | none | no |
| stress/section.tuner.load.sum_ms | 0.1 | 0.1 | +14.4 | worse | no |
| stress/section.tuner.modeSwitch.count | 6.0 | 6.0 | +0.0 | none | no |
| stress/section.tuner.modeSwitch.sum_ms | 378.4 | 277.9 | -26.6 | better | no |
| stress/frames.p50_ms | 6.4 | 7.1 | +10.2 | worse | yes |
| stress/frames.p90_ms | 10.1 | 10.0 | -0.1 | better | no |
| stress/frames.p99_ms | 25.8 | 22.5 | -12.7 | better | yes |
| stress/frames.overrun_p90_ms | 2.0 | -0.0 | -101.3 | better | no |

startup.ttid_ms: 182.7 → 155.9 ms (-14.7 %, confirmed)
