# Tembra Audio performance pipeline (`tracepulse`)

The owner's trace-pulse pipeline adopted into the repository and extended for iOS, a
device-keyed history and an informational gate. Feature 007 (`specs/007-performance-pipeline/`).

## Provenance

Copied on 2026-10-08 from `/Volumes/External/Dev/trace-pulse` at commit
`34a84d801f3844c3baff611f2b6d087f5d4eac7f` (working tree with 29 uncommitted edits: the
golden report, store and CLI tests from the Pixel 6 Pro rounds), `cli/tracepulse/` and
`cli/tests/`. Dropped on copy: the `init` scaffolder (`scaffold_init`, `module/benchmarks-template/`)
and the NIA-specific journey configuration. Kept: the Skia step-fit detector, the
Mann-Whitney confirmation, the SQLite store, the report engine and the test vectors.

## Install

```
cd app/perf && uv sync
```

Python 3.12 through `uv` (`requires-python >=3.9,<3.14`); dependencies PyYAML, scipy, perfetto,
Jinja2, jsonschema; none ship in the app.
