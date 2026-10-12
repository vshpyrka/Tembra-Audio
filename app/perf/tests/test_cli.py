"""The `tracepulse` command line (contracts/cli.md): every subcommand parses with
its documented flags, exit statuses are 0/2/3/4/5, `run` needs `--label`, and
`--json` prints one object."""

import json

import pytest

from tracepulse.__main__ import (
    EXIT_FAILED, EXIT_OK, EXIT_PUBLISH, EXIT_REFUSED, EXIT_USAGE, build_parser, main,
)


@pytest.mark.parametrize("argv", [
    ["devices"],
    ["doctor", "--device", "pixel6"],
    ["run", "--device", "pixel6", "--label", "007", "--rounds", "5", "--journeys", "startup,tuner",
     "--iterations", "20", "--no-publish", "--keep-build", "--size"],
    ["run", "--device", "pixel6", "--label", "007", "--no-build", "--apk", "a.apk", "--test-apk", "b.apk"],
    ["run", "--device", "iphone14", "--label", "007", "--no-build", "--xctestrun", "x.xctestrun"],
    ["run", "--device", "pixel6", "--label", "release-baseline", "--build-type", "release"],
    ["run", "--device", "iphone14", "--label", "release-baseline", "--configuration", "Release"],
    ["run", "--simulators", "--label", "007", "--rounds", "2"],
    ["size", "--device", "iphone14"],
    ["publish"],
    ["publish", "--device", "pixel6"],
    ["sync"],
    ["analyze"],
    ["report", "--open", "--out", "x.html"],
    ["status"],
    ["inject", "R1"],
    ["revert", "R1"],
    ["revert", "--all"],
    ["--json", "status"],
])
def test_every_documented_invocation_parses(argv):
    args = build_parser().parse_args(argv)
    assert args.command


def test_exit_status_constants_match_the_contract():
    assert (EXIT_OK, EXIT_REFUSED, EXIT_FAILED, EXIT_USAGE, EXIT_PUBLISH) == (0, 2, 3, 4, 5)


def test_run_without_label_exits_usage(capsys):
    code = main(["run", "--device", "pixel6"])
    assert code == EXIT_USAGE
    assert "--label" in capsys.readouterr().err


def test_run_without_device_or_simulators_exits_usage(capsys):
    code = main(["run", "--label", "007", "--no-build", "--apk", "a", "--test-apk", "b"])
    assert code in (EXIT_USAGE,)


def test_unknown_subcommand_exits_usage():
    with pytest.raises(SystemExit) as exc:
        main(["frobnicate"])
    assert exc.value.code == 2 or exc.value.code == EXIT_USAGE


def test_bad_config_exits_usage(tmp_path, capsys):
    bad = tmp_path / "tracepulse.yml"
    bad.write_text("ab_mode: {}\n")
    code = main(["-c", str(bad), "status"])
    assert code == EXIT_USAGE
    assert "ab_mode" in capsys.readouterr().err


def test_json_status_prints_one_object(tmp_path, capsys, monkeypatch):
    monkeypatch.setattr("tracepulse.__main__.PERF_DIR", str(tmp_path))
    code = main(["--json", "status"])
    assert code == EXIT_OK
    out = capsys.readouterr().out
    data = json.loads(out)
    assert data["command"] == "status" and "rounds" in data and "unpublished" in data
