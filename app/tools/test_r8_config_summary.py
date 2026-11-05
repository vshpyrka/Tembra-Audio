"""Tests for tools/r8-config-summary.py: python3 -m unittest discover -s tools -p 'test_r8_config_summary.py'

The reports are encoded here by hand in R8's keep_radius.proto layout, so the decoder, the scores and the rule
origins are checked without a build. Field 0 values are left out the way proto3 leaves them out.
"""
import importlib.util
import os
import unittest

_SPEC = importlib.util.spec_from_file_location(
    "r8_config_summary", os.path.join(os.path.dirname(__file__), "r8-config-summary.py"))
summary = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(summary)


def _varint(n):
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        out.append(byte | (0x80 if n else 0))
        if not n:
            return bytes(out)


def _field(number, value):
    """An int as a varint, bytes or str as length-delimited; a zero int is omitted like proto3 does."""
    if isinstance(value, int):
        return b"" if value == 0 else _varint(number << 3) + _varint(value)
    data = value.encode("utf-8") if isinstance(value, str) else value
    return _varint(number << 3 | 2) + _varint(len(data)) + data


def _packed(number, values):
    return _field(number, b"".join(_varint(v) for v in values)) if values else b""


def _msg(*parts):
    return b"".join(parts)


APP_FILE = "/elsewhere/app/androidApp/src/main/keepRules/tembra.keep"
AGP_FILE = "Android Gradle plugin 9.3.3 (extracted file: /x/default_proguard_files/global/proguard-android-optimize.txt)"


def _report(global_sources=()):
    """20 live items; an app rule keeps two classes outright, a library rule one method from optimization only."""
    files = [
        _msg(_field(2, APP_FILE)),  # id 0
        _msg(_field(1, 1), _field(2, "/cache/lib/proguard.txt"), _field(3, _msg(_field(1, 5)))),
        _msg(_field(1, 2), _field(2, AGP_FILE)),
    ]
    mavens = [_msg(_field(1, 5), _field(2, "org.example"), _field(3, "lib"), _field(4, "1.0"))]
    constraints = [
        _msg(_packed(2, [summary.DONT_OBFUSCATE, summary.DONT_OPTIMIZE, summary.DONT_SHRINK])),  # id 0
        _msg(_field(1, 1), _packed(2, [summary.DONT_OPTIMIZE])),
    ]
    rules = [
        _msg(_field(2, "-keep class a.B { *; }"), _field(4, _msg(_field(2, 3))),
             _field(5, _msg(_packed(2, [0, 1])))),  # id 0, constraints 0, app file line 3
        _msg(_field(1, 1), _field(2, "-keepclassmembers class * {\n  void run();\n}"), _field(3, 1),
             _field(4, _msg(_field(1, 1), _field(2, 7))), _field(5, _msg(_packed(4, [2])))),
        _msg(_field(1, 2), _field(2, "-keepclassmembers enum * { values(); }"), _field(3, 1),
             _field(4, _msg(_field(1, 2), _field(2, 45)))),  # matches nothing
    ]
    kept = (
        [_field(summary.KEPT_CLASS_TABLE, _msg(_field(1, i), _packed(4, [0]))) for i in (0, 1)]
        + [_field(summary.KEPT_METHOD_TABLE, _msg(_field(1, 2), _packed(4, [1])))]
    )
    globals_ = [_field(summary.GLOBAL_RULE_TABLE, _msg(_field(1, i), _field(2, s)))
                for i, s in enumerate(global_sources)]
    return b"".join(
        [_field(summary.FILE_ORIGIN_TABLE, f) for f in files]
        + [_field(summary.MAVEN_COORDINATE_TABLE, m) for m in mavens]
        + kept
        + [_field(summary.KEEP_CONSTRAINTS_TABLE, c) for c in constraints]
        + [_field(summary.KEEP_RULE_TABLE, r) for r in rules]
        + globals_
        + [_field(summary.BUILD_INFO, _msg(_field(4, 10), _field(6, 10)))]
    )


class SummarizeTest(unittest.TestCase):
    def test_scores_count_items_each_constraint_blocks(self):
        text = summary.summarize(_report())
        # optimization: 3 of 20 blocked; obfuscation and shrinking: the app rule's 2 classes.
        self.assertIn("| 85.00 % | 90.00 % | 90.00 % |", text)
        self.assertIn("20 live classes", text)
        self.assertIn("3 keep rules, 1 of them in the app's own files; 0 global options", text)

    def test_rule_origins_and_order(self):
        text = summary.summarize(_report(), top=3)
        lines = text.splitlines()
        app_section = lines.index("### Keep rules in the app's own files")
        self.assertIn(f"| `-keep class a.B {{ *; }}` | {APP_FILE}:3 | 2 | 0 | 0 |", lines[app_section + 4])
        top = lines.index("### The 3 keep rules that keep the most")
        self.assertTrue(lines[top + 4].startswith("| `-keep class a.B"))
        self.assertEqual("| `-keepclassmembers class * { …` | org.example:lib:1.0 | 0 | 0 | 1 |", lines[top + 5])
        self.assertEqual("| `-keepclassmembers enum * { values(); }` | AGP defaults | 0 | 0 | 0 |", lines[top + 6])

    def test_global_option_zeroes_its_score_and_is_listed(self):
        text = summary.summarize(_report(global_sources=["-dontobfuscate"]))
        self.assertIn("| 85.00 % | 0.00 % | 90.00 % |", text)
        self.assertIn("- `-dontobfuscate`", text)

    def test_cell_escapes_pipes_and_backticks_and_shortens(self):
        self.assertEqual("-keep class `a` …".replace("`", "'"), summary._cell("-keep class `a`\n{ *; }"))
        self.assertEqual("a\\|b", summary._cell("a|b"))
        self.assertEqual("x" * 100 + " …", summary._cell("x" * 150))


if __name__ == "__main__":
    unittest.main()
