# Trial round (T086)

## iPhone 14

Not measured: the runner was refused on 2026-11-01, twice. Sessions `20261101T192615Z_010-recording` and
`20261101T193718Z_010-recording` (build-for-testing in the Perf configuration succeeded each time), rounds
`20261101T192951Z_010-recording` and `20261101T193730Z_010-recording`. Each round printed "the phone denied the runner as not
trusted" at attempts 1 and 2, then `refused: runner_trusted: the TembraPerfTests runner's profile is not trusted on the phone:
Settings -> General -> VPN & Device Management -> trust the developer profile, then rerun`. No journey ran, so there is no
record.json, no section data and no counters. The phone was connected and paired (devicectl). Owner action: trust the developer
profile on the phone (and unlock it), then rerun
`uv run tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish` from `app/perf`.
