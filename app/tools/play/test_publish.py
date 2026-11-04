"""Tests for publish.py: `python3 -m unittest discover -s app/tools/play` from the repository root.

The Play flow runs against a scripted fake of Google's REST APIs; signing runs the real keytool and jarsigner of the
JDK on PATH against a throwaway keystore served by a fake Secret Manager.
"""

from __future__ import annotations

import base64
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import urllib.error
import zipfile
from email.message import Message
from pathlib import Path
from unittest import mock

import publish
from publish import ApiError, Failure, Http, Play


class FakeResponse:
    def __init__(self, status: int, body: object, headers: dict | None = None):
        self.status = status
        self._raw = json.dumps(body).encode("utf-8") if body is not None else b""
        message = Message()
        for key, value in (headers or {}).items():
            message[key] = value
        self.headers = message

    def read(self) -> bytes:
        return self._raw

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeGoogle:
    """Answers each request from an ordered script of (method, url fragment, status, body, headers)."""

    def __init__(self, script: list[tuple]):
        self.script = list(script)
        self.calls: list[tuple[str, str, bytes]] = []

    def __call__(self, request, timeout=None):
        method, url = request.get_method(), request.full_url
        self.calls.append((method, url, request.data or b""))
        if not self.script:
            raise AssertionError(f"unexpected request {method} {url}")
        want_method, fragment, status, body, headers = self.script.pop(0)
        if method != want_method or fragment not in url:
            raise AssertionError(f"expected {want_method} …{fragment}…, got {method} {url}")
        if status >= 400:
            raw = json.dumps(body).encode("utf-8")
            raise urllib.error.HTTPError(url, status, "error", Message(), io.BytesIO(raw))
        return FakeResponse(status, body, headers)

    def body(self, index: int) -> dict:
        return json.loads(self.calls[index][2].decode("utf-8"))


def api_error(message: str) -> dict:
    return {"error": {"code": 400, "message": message, "status": "INVALID_ARGUMENT"}}


EDIT = ("POST", "/edits", 200, {"id": "e1"}, None)
TRACKS = ("GET", "/edits/e1/tracks", 200, {"tracks": [{"track": "alpha"}, {"track": "beta"}, {"track": "production"}]}, None)
UPLOAD_START = ("POST", "/resumable/upload/", 200, {}, {"Location": "https://upload.example/session1"})
UPLOAD_PUT = ("PUT", "upload.example/session1", 200, {"versionCode": 812, "sha256": "ab"}, None)
DELETE = ("DELETE", "/edits/e1", 204, None, None)


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.bundle = self.tmp / "app.aab"
        self.bundle.write_bytes(b"bundle-bytes")
        patcher = mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "", "GITHUB_STEP_SUMMARY": ""})
        patcher.start()
        self.addCleanup(patcher.stop)

    def play(self, script):
        fake = FakeGoogle(script)
        return Play(Http("token", opener=fake), "com.tembraaudio"), fake

    def test_releases_on_the_closed_track_and_commits(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 200, {}, None),
            ("POST", "/edits/e1:commit", 200, {"id": "e1"}, None),
        ])
        notes = [{"language": "en-US", "text": "Recording"}]
        result = publish.publish(play, "alpha", self.bundle, "completed", notes)
        self.assertEqual(result["versionCode"], 812)
        self.assertTrue(result["committed"])
        self.assertEqual(
            fake.body(4),
            {"track": "alpha", "releases": [{"versionCodes": ["812"], "status": "completed", "releaseNotes": notes}]},
        )
        self.assertEqual(fake.calls[3][2], b"bundle-bytes")
        self.assertIn("changesInReviewBehavior=CANCEL_IN_REVIEW_AND_SUBMIT", fake.calls[5][1])
        self.assertNotIn("changesNotSentForReview", fake.calls[5][1])
        self.assertTrue(all(call[1].startswith("https://") for call in fake.calls))

    def test_the_internal_track_answers_to_both_of_its_names(self):
        play, fake = self.play([
            EDIT,
            ("GET", "/edits/e1/tracks", 200, {"tracks": [{"track": "qa"}, {"track": "alpha"}]}, None),
            UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/qa", 200, {}, None),
            ("POST", "/edits/e1:commit", 200, {}, None),
        ])
        result = publish.publish(play, "internal", self.bundle, "completed", [])
        self.assertEqual(result["track"], "qa")
        self.assertEqual(fake.body(4)["track"], "qa")

    def test_the_release_is_named_as_asked(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 200, {}, None),
            ("POST", "/edits/e1:commit", 200, {}, None),
        ])
        publish.publish(play, "alpha", self.bundle, "completed", [], release_name="0.1.0 (812)")
        self.assertEqual(fake.body(4)["releases"][0]["name"], "0.1.0 (812)")

    def test_refuses_production_and_open_testing_before_any_request(self):
        for track in ("production", "beta", "wear:production"):
            play, fake = self.play([])
            with self.assertRaises(Failure):
                publish.publish(play, track, self.bundle, "completed", [])
            self.assertEqual(fake.calls, [])

    def test_unknown_track_lists_the_real_ones_and_discards_the_edit(self):
        play, fake = self.play([EDIT, TRACKS, DELETE])
        with self.assertRaises(Failure) as raised:
            publish.publish(play, "closed-beta", self.bundle, "completed", [])
        self.assertIn("alpha, beta, production", str(raised.exception))
        self.assertEqual(fake.calls[-1][0], "DELETE")

    def test_a_draft_app_is_left_alone_until_its_first_review(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 400, api_error("Only releases with status draft may be created on draft app."), None),
            DELETE,
        ])
        result = publish.publish(play, "alpha", self.bundle, "completed", [])
        self.assertFalse(result["committed"])
        self.assertIn("first release", result["skipped"])
        self.assertFalse(any(":commit" in call[1] for call in fake.calls))
        self.assertEqual(fake.calls[-1][0], "DELETE")

    def test_a_draft_release_never_cancels_a_review(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 200, {}, None),
            ("POST", "/edits/e1:commit", 200, {}, None),
        ])
        result = publish.publish(play, "alpha", self.bundle, "draft", [])
        self.assertTrue(result["committed"])
        self.assertEqual(fake.body(4)["releases"][0]["status"], "draft")
        self.assertIn("changesInReviewBehavior=ERROR_IF_IN_REVIEW", fake.calls[-1][1])

    def test_commit_falls_back_to_changes_not_sent_for_review(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 200, {}, None),
            ("POST", "/edits/e1:commit", 400, api_error(
                "Changes cannot be sent for review automatically. Please set the query parameter "
                "changesNotSentForReview to true."), None),
            ("POST", "/edits/e1:commit", 200, {}, None),
        ])
        result = publish.publish(play, "alpha", self.bundle, "completed", [])
        self.assertFalse(result["sentForReview"])
        self.assertIn("changesNotSentForReview=true", fake.calls[-1][1])
        self.assertIn("changesInReviewBehavior=ERROR_IF_IN_REVIEW", fake.calls[-1][1])

    def test_other_errors_fail_and_discard_the_edit(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START,
            ("PUT", "upload.example/session1", 403, api_error("APK specifies a version code that has already been used."), None),
            DELETE,
        ])
        with self.assertRaises(ApiError) as raised:
            publish.publish(play, "alpha", self.bundle, "completed", [])
        self.assertIn("already been used", str(raised.exception))
        self.assertEqual(fake.calls[-1][0], "DELETE")

    def test_validate_only_never_commits(self):
        play, fake = self.play([
            EDIT, TRACKS, UPLOAD_START, UPLOAD_PUT,
            ("PUT", "/edits/e1/tracks/alpha", 200, {}, None),
            ("POST", "/edits/e1:validate", 200, {}, None),
            DELETE,
        ])
        result = publish.publish(play, "alpha", self.bundle, "completed", [], validate_only=True)
        self.assertFalse(result["committed"])
        self.assertFalse(any(":commit" in call[1] for call in fake.calls))

    def test_a_network_error_is_reported_without_a_traceback(self):
        def unreachable(request, timeout=None):
            raise urllib.error.URLError("connection reset")

        with mock.patch.dict(os.environ, {publish.TOKEN_ENV: "token"}), \
                mock.patch("urllib.request.urlopen", unreachable), \
                mock.patch("sys.stderr", new_callable=io.StringIO) as stderr:
            status = publish.main(["publish", "--package", "com.tembraaudio", "--track", "alpha",
                                   "--bundle", str(self.bundle)])
        self.assertEqual(status, 1)
        self.assertIn("network error", stderr.getvalue())

    def test_release_notes_are_cut_to_play_limit(self):
        notes_file = self.tmp / "notes.txt"
        notes_file.write_text("x" * 600, encoding="utf-8")
        notes = publish.release_notes(notes_file, "en-US")
        self.assertEqual(len(notes[0]["text"]), 500)
        self.assertEqual(publish.release_notes(self.tmp / "missing.txt", "en-US"), [])


@unittest.skipUnless(shutil.which("keytool") and shutil.which("jarsigner"), "needs a JDK on PATH")
class SignTest(unittest.TestCase):
    PASSWORD = "correct-horse-battery-staple"

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.keystore = self.tmp / "upload.p12"
        self.keytool(
            "-genkeypair", "-keystore", str(self.keystore), "-storetype", "PKCS12", "-storepass", self.PASSWORD,
            "-alias", "upload", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=Tembra Audio upload key (test)",
        )
        listing = self.keytool("-list", "-v", "-keystore", str(self.keystore), "-storepass", self.PASSWORD)
        self.fingerprint = publish.normalise_fingerprint(
            next(line.split("SHA256:")[1] for line in listing.splitlines() if "SHA256:" in line)
        )
        self.bundle = self.tmp / "app.aab"
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("BundleConfig.pb", b"\x00")
            archive.writestr("base/manifest/AndroidManifest.xml", b"\x01")
        patcher = mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "", "RUNNER_TEMP": str(self.tmp)})
        patcher.start()
        self.addCleanup(patcher.stop)

    def keytool(self, *args: str) -> str:
        return subprocess.run(["keytool", *args], capture_output=True, text=True, check=True).stdout

    def secret_manager(self, password: str | None = None):
        keystore = self.keystore.read_bytes()
        secret = (password or self.PASSWORD).encode("utf-8")

        def payload(data: bytes) -> dict:
            return {"payload": {"data": base64.b64encode(data).decode(), "dataCrc32c": str(publish._crc32c(data))}}

        return FakeGoogle([
            ("GET", "projects/p/secrets/keystore/versions/latest:access", 200, payload(keystore), None),
            ("GET", "projects/p/secrets/password/versions/3:access", 200, payload(secret + b"\n"), None),
        ])

    def sign(self, fake, expected: str, bundle: Path | None = None) -> Path:
        out = self.tmp / "signed" / "app.aab"
        publish.sign_bundle(
            Http("token", opener=fake), bundle or self.bundle, out,
            "projects/p/secrets/keystore", "projects/p/secrets/password/versions/3", expected,
        )
        return out

    def test_signs_with_the_upload_key_and_removes_the_key(self):
        out = self.sign(self.secret_manager(), self.fingerprint.replace(":", "").lower())
        self.assertEqual(publish.signer_sha256(out), [self.fingerprint])
        self.assertEqual(sorted(p.name for p in self.tmp.iterdir() if p.name.startswith("upload-key-")), [])

    def test_a_different_certificate_fails_and_leaves_no_signed_bundle(self):
        with self.assertRaises(Failure) as raised:
            self.sign(self.secret_manager(), "AB" * 32)
        self.assertIn("not the registered upload certificate", str(raised.exception))
        self.assertFalse((self.tmp / "signed" / "app.aab").exists())

    def test_a_wrong_password_fails(self):
        with self.assertRaises(Failure):
            self.sign(self.secret_manager(password="wrong"), self.fingerprint)

    def test_an_already_signed_bundle_is_refused(self):
        signed = self.sign(self.secret_manager(), self.fingerprint)
        with self.assertRaises(Failure) as raised:
            self.sign(self.secret_manager(), self.fingerprint, bundle=signed)
        self.assertIn("already signed", str(raised.exception))

    def test_a_damaged_secret_is_detected(self):
        fake = FakeGoogle([("GET", ":access", 200, {"payload": {"data": base64.b64encode(b"abc").decode(), "dataCrc32c": "1"}}, None)])
        with self.assertRaises(Failure):
            publish.access_secret(Http("token", opener=fake), "projects/p/secrets/keystore")

    def test_crc32c_matches_the_standard_check_value(self):
        self.assertEqual(publish._crc32c(b"123456789"), 0xE3069283)


if __name__ == "__main__":
    unittest.main()
