"""Deterministic synthetic Perfetto trace fixture generator.

Builds a minimal, self-contained trace: one process track, one main-thread
track (tid == pid), and four `track_event` BEGIN/END slice pairs on that
thread — three `Choreographer#doFrame` (5 ms, 7 ms, 9 ms) and one `inflate`
(2 ms) — with deterministic non-overlapping timestamps starting at
1_000_000 ns. No wall-clock reads, no randomness, no map<> fields, so
`build_trace()` is byte-identical across runs and processes (see
tests/test_traces.py::test_gen_trace_is_deterministic).

Proto classes come from the perfetto pip package's bundled protos (verified
against perfetto==0.57.2 / protobuf==6.33.6):
    from perfetto.protos.perfetto.trace.perfetto_trace_pb2 import Trace, TracePacket, TrackEvent

Regenerate the committed fixture with:
    cd cli && .venv/bin/python tests/fixtures/gen_trace.py
"""

import os

from perfetto.protos.perfetto.trace.perfetto_trace_pb2 import Trace, TracePacket, TrackEvent


PROCESS_PID = 1234
PROCESS_NAME = "com.google.samples.apps.nowinandroid.demo"
PROCESS_TRACK_UUID = 1
THREAD_TRACK_UUID = 2
SEQUENCE_ID = 1
START_TS_NS = 1_000_000

# (slice name, duration_ms) emitted back-to-back in this order, non-overlapping,
# starting at START_TS_NS. No interning: each BEGIN carries an explicit name.
SLICES = [
    ("Choreographer#doFrame", 5),
    ("Choreographer#doFrame", 7),
    ("Choreographer#doFrame", 9),
    ("inflate", 2),
]


def build_trace() -> bytes:
    """Build the synthetic trace and return its serialized bytes.

    Pure function: no I/O, no clock reads, no randomness. `deterministic=True`
    pins canonical field/map ordering so repeated calls (and repeated
    `gen_trace.py` invocations, possibly in different processes) always
    produce identical bytes.
    """
    trace = Trace()

    process_packet = trace.packet.add()
    process_packet.track_descriptor.uuid = PROCESS_TRACK_UUID
    process_packet.track_descriptor.process.pid = PROCESS_PID
    process_packet.track_descriptor.process.process_name = PROCESS_NAME

    thread_packet = trace.packet.add()
    thread_packet.track_descriptor.uuid = THREAD_TRACK_UUID
    thread_packet.track_descriptor.parent_uuid = PROCESS_TRACK_UUID
    thread_packet.track_descriptor.thread.pid = PROCESS_PID
    thread_packet.track_descriptor.thread.tid = PROCESS_PID  # tid == pid -> main thread
    thread_packet.track_descriptor.thread.thread_name = "main"

    ts = START_TS_NS
    for i, (name, dur_ms) in enumerate(SLICES):
        dur_ns = dur_ms * 1_000_000

        begin = trace.packet.add()
        begin.timestamp = ts
        begin.trusted_packet_sequence_id = SEQUENCE_ID
        if i == 0:
            # First packet on the sequence: mark incremental state cleared so
            # the sequence is self-contained (no prior packets to depend on).
            begin.sequence_flags = TracePacket.SEQ_INCREMENTAL_STATE_CLEARED
        begin.track_event.type = TrackEvent.TYPE_SLICE_BEGIN
        begin.track_event.track_uuid = THREAD_TRACK_UUID
        begin.track_event.name = name

        ts += dur_ns

        end = trace.packet.add()
        end.timestamp = ts
        end.trusted_packet_sequence_id = SEQUENCE_ID
        end.track_event.type = TrackEvent.TYPE_SLICE_END
        end.track_event.track_uuid = THREAD_TRACK_UUID

    return trace.SerializeToString(deterministic=True)


def generate(output_path: str) -> None:
    """Build the synthetic trace and write it to `output_path`."""
    with open(output_path, "wb") as f:
        f.write(build_trace())


def main() -> None:
    fixture_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture.perfetto-trace")
    generate(fixture_path)
    print(f"wrote {fixture_path}")


if __name__ == "__main__":
    main()
