#!/usr/bin/env python3
"""Signs Tembra Audio's release bundle with the upload key and publishes it to a Google Play testing track.

Runs in .github/workflows/play-closed-testing.yml; the setup and the reasons behind it are in
docs/google-play-publishing.md. Standard library only, so no third-party package ever sees the access token or the
upload key: the token comes from Google's own google-github-actions/auth (Workload Identity Federation, no stored
key), the requests go straight to Google's REST APIs, and the signature is made by the JDK's jarsigner.

Commands:
  sign     fetch the upload keystore and its password from Secret Manager into a private temporary directory, sign
           the bundle with jarsigner, check the signer is the registered upload certificate, delete the key
  tracks   list the app's tracks and their releases (the closed testing track's API name is among them)
  publish  upload a signed bundle and release it on a testing track (internal or closed)

Every command reads an OAuth 2.0 access token from $GOOGLE_OAUTH_ACCESS_TOKEN. `sign` needs the cloud-platform scope
(Secret Manager), `tracks` and `publish` the androidpublisher scope; the workflow asks for both in one token.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Callable, Optional

PLAY_API = "https://androidpublisher.googleapis.com"
SECRET_MANAGER_API = "https://secretmanager.googleapis.com"
TOKEN_ENV = "GOOGLE_OAUTH_ACCESS_TOKEN"
# jarsigner reads the keystore password from this variable (-storepass:env), never from its command line.
STOREPASS_ENV = "TEMBRA_PLAY_UPLOAD_STOREPASS"
UPLOAD_KEY_ALIAS = "upload"
# Production and open testing ("beta") are refused: this pipeline releases to internal or closed testing. The service
# account's Play permission covers every testing track, so this check is the one that keeps the open track out.
REFUSED_TRACKS = {"production", "beta"}
# Google's API documentation names the internal testing track "qa", while Play and most tools call it "internal".
TRACK_ALIASES = {"internal": "qa", "qa": "internal"}
# Play's limit on a release name.
RELEASE_NAME_LIMIT = 50
# Play's limit on one language's release notes.
RELEASE_NOTES_LIMIT = 500
# The bundle upload: Play processes the bundle before it answers, which can take minutes for a large one.
UPLOAD_TIMEOUT_SECONDS = 30 * 60
REQUEST_TIMEOUT_SECONDS = 120


class ApiError(Exception):
    """A Google API answered with an error status."""

    def __init__(self, method: str, url: str, status: int, message: str, body: str):
        super().__init__(f"{method} {url} -> HTTP {status}: {message}")
        self.status = status
        self.message = message
        self.body = body


class Failure(Exception):
    """A check of this script failed; the message says what to do."""


# ── Output ──────────────────────────────────────────────────────────────────────────────────────────────────────────


def log(message: str) -> None:
    print(message, flush=True)


def warning(message: str) -> None:
    # A GitHub Actions annotation in the workflow; plain text elsewhere.
    prefix = "::warning::" if os.environ.get("GITHUB_ACTIONS") == "true" else "warning: "
    print(prefix + message.replace("\n", " "), flush=True)


def write_step_summary(markdown: str) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as summary:
            summary.write(markdown + "\n")


# ── HTTP ────────────────────────────────────────────────────────────────────────────────────────────────────────────


def access_token() -> str:
    token = os.environ.get(TOKEN_ENV, "").strip()
    if not token:
        raise Failure(f"${TOKEN_ENV} is empty: the workflow's google-github-actions/auth step provides it")
    return token


def _error_message(body: str) -> str:
    try:
        error = json.loads(body).get("error", {})
    except (ValueError, AttributeError):
        return body.strip()[:500] or "(empty body)"
    if isinstance(error, dict):
        return str(error.get("message") or error.get("status") or body.strip()[:500])
    return str(error)


class Http:
    """A tiny JSON client over urllib with bearer authentication; `opener` is replaceable in tests."""

    def __init__(self, token: str, opener: Optional[Callable] = None):
        self.token = token
        self.opener = opener or urllib.request.urlopen

    def request(
        self,
        method: str,
        url: str,
        body: Optional[object] = None,
        data: Optional[bytes] = None,
        headers: Optional[dict] = None,
        timeout: float = REQUEST_TIMEOUT_SECONDS,
    ) -> tuple[int, dict, dict]:
        all_headers = {"Authorization": f"Bearer {self.token}"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            all_headers["Content-Type"] = "application/json; charset=UTF-8"
        if data is None and method in ("POST", "PUT"):
            data = b""
        all_headers.update(headers or {})
        request = urllib.request.Request(url, data=data, method=method, headers=all_headers)
        try:
            with self.opener(request, timeout=timeout) as response:
                raw = response.read().decode("utf-8")
                response_headers = {key.lower(): value for key, value in response.headers.items()}
                return response.status, (json.loads(raw) if raw.strip() else {}), response_headers
        except urllib.error.HTTPError as error:
            with error:
                raw = error.read().decode("utf-8", errors="replace")
            safe_url = url.split("?", 1)[0]
            raise ApiError(method, safe_url, error.code, _error_message(raw), raw) from None

    def json(self, method: str, url: str, body: Optional[object] = None) -> dict:
        return self.request(method, url, body=body)[1]


# ── Secret Manager ──────────────────────────────────────────────────────────────────────────────────────────────────


def _crc32c(data: bytes) -> int:
    # Castagnoli CRC, the checksum Secret Manager returns with every payload (no stdlib implementation exists).
    crc = 0xFFFFFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ (0x82F63B78 if crc & 1 else 0)
    return crc ^ 0xFFFFFFFF


def secret_version_name(secret: str) -> str:
    if not re.fullmatch(r"projects/[^/]+/secrets/[^/]+(/versions/[^/]+)?", secret):
        raise Failure(f"not a Secret Manager secret name: {secret!r} (expected projects/<project>/secrets/<name>)")
    return secret if "/versions/" in secret else secret + "/versions/latest"


def access_secret(http: Http, secret: str) -> bytes:
    name = secret_version_name(secret)
    response = http.json("GET", f"{SECRET_MANAGER_API}/v1/{name}:access")
    payload = response.get("payload", {})
    data = base64.b64decode(payload.get("data", ""))
    expected = payload.get("dataCrc32c")
    if expected is not None and int(expected) != _crc32c(data):
        raise Failure(f"{name}: the payload's CRC32C does not match; the secret arrived damaged")
    if not data:
        raise Failure(f"{name} is empty")
    return data


# ── Signing ─────────────────────────────────────────────────────────────────────────────────────────────────────────


def normalise_fingerprint(value: str) -> str:
    hex_digits = re.sub(r"[^0-9A-Fa-f]", "", value).upper()
    if len(hex_digits) != 64:
        raise Failure(f"not a SHA-256 certificate fingerprint: {value!r}")
    return ":".join(hex_digits[i : i + 2] for i in range(0, 64, 2))


def existing_signature_files(bundle: Path) -> list[str]:
    with zipfile.ZipFile(bundle) as archive:
        return [
            name
            for name in archive.namelist()
            if re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, flags=re.IGNORECASE)
        ]


def signer_sha256(bundle: Path, keytool: str = "keytool") -> list[str]:
    """SHA-256 fingerprints of the certificates that signed the bundle, as keytool prints them."""
    result = subprocess.run(
        [keytool, "-printcert", "-jarfile", str(bundle)], capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        raise Failure(f"keytool -printcert failed:\n{result.stdout}{result.stderr}")
    return [normalise_fingerprint(match) for match in re.findall(r"SHA256:\s*([0-9A-Fa-f:]{95})", result.stdout)]


def sign_bundle(
    http: Http,
    bundle: Path,
    out: Path,
    keystore_secret: str,
    password_secret: str,
    expected_sha256: str,
    jdk_bin: Optional[Path] = None,
) -> str:
    expected = normalise_fingerprint(expected_sha256)
    jarsigner = str(jdk_bin / "jarsigner") if jdk_bin else "jarsigner"
    keytool = str(jdk_bin / "keytool") if jdk_bin else "keytool"
    present = existing_signature_files(bundle)
    if present:
        raise Failure(
            f"{bundle} is already signed ({', '.join(present)}): build it without a signing config so the upload "
            "key is its only signer"
        )

    key_dir = Path(tempfile.mkdtemp(prefix="upload-key-", dir=os.environ.get("RUNNER_TEMP")))
    try:
        os.chmod(key_dir, 0o700)
        keystore = key_dir / "upload.p12"
        keystore.write_bytes(access_secret(http, keystore_secret))
        os.chmod(keystore, 0o600)
        password = access_secret(http, password_secret).decode("utf-8").strip()
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::add-mask::{password}", flush=True)
        env = dict(os.environ, **{STOREPASS_ENV: password})
        out.parent.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(
            [
                jarsigner,
                "-keystore", str(keystore),
                "-storetype", "PKCS12",
                "-storepass:env", STOREPASS_ENV,
                # The algorithms Android Gradle Plugin itself signs bundles with.
                "-sigalg", "SHA256withRSA",
                "-digestalg", "SHA-256",
                "-signedjar", str(out),
                str(bundle),
                UPLOAD_KEY_ALIAS,
            ],
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            raise Failure(f"jarsigner failed:\n{result.stdout}{result.stderr}")
    finally:
        shutil.rmtree(key_dir, ignore_errors=True)

    verify = subprocess.run(
        [jarsigner, "-verify", str(out)], capture_output=True, text=True, check=False
    )
    if verify.returncode != 0 or "jar verified" not in verify.stdout:
        raise Failure(f"jarsigner -verify rejected the signed bundle:\n{verify.stdout}{verify.stderr}")
    signers = signer_sha256(out, keytool)
    if signers != [expected]:
        out.unlink(missing_ok=True)
        raise Failure(
            "the bundle's signer is not the registered upload certificate: expected "
            f"{expected}, found {', '.join(signers) or 'none'}. Check PLAY_UPLOAD_CERT_SHA256 against Play Console → "
            "Test and release → App integrity → Play app signing → Upload key certificate"
        )
    return expected


# ── Google Play Developer API ───────────────────────────────────────────────────────────────────────────────────────


def check_testing_track(track: str) -> None:
    base = track.split(":")[-1]
    if base in REFUSED_TRACKS:
        raise Failure(
            f"refusing track {track!r}: this pipeline releases to internal or closed testing only (the API calls "
            "open testing 'beta'; internal testing is 'internal' and the default closed testing track 'alpha')"
        )


def resolve_track(track: str, names: list) -> str:
    if track in names:
        return track
    alias = TRACK_ALIASES.get(track)
    if alias in names:
        log(f"the app calls track {track!r} {alias!r}")
        return alias
    raise Failure(
        f"the app has no track {track!r}; its tracks are: {', '.join(sorted(n for n in names if n))}. Set "
        "the PLAY_TRACK repository variable to one of them"
    )


def release_notes(path: Optional[Path], language: str) -> list[dict]:
    if path is None or not path.is_file():
        return []
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if len(text) > RELEASE_NOTES_LIMIT:
        text = text[: RELEASE_NOTES_LIMIT - 1].rstrip() + "…"
    return [{"language": language, "text": text}]


def is_draft_app_error(error: ApiError) -> bool:
    # "Only releases with status draft may be created on draft app." until the app's first release is reviewed.
    return error.status == 400 and "draft app" in error.message.lower()


def is_network_error(error: BaseException) -> bool:
    # socket.timeout is TimeoutError from Python 3.10 on; named for the 3.9 that macOS ships.
    return isinstance(error, (urllib.error.URLError, socket.timeout, TimeoutError, ConnectionError))


def is_not_sent_for_review_error(error: ApiError) -> bool:
    # Play answers so when it cannot send the edit for review automatically (for example a rejected update waits).
    return error.status == 400 and "changesnotsentforreview" in error.message.replace(" ", "").lower()


class Play:
    def __init__(self, http: Http, package: str):
        self.http = http
        self.package = package
        self.base = f"{PLAY_API}/androidpublisher/v3/applications/{urllib.parse.quote(package)}"

    def insert_edit(self) -> str:
        return self.http.json("POST", f"{self.base}/edits", body={})["id"]

    def delete_edit(self, edit: str) -> None:
        self.http.request("DELETE", f"{self.base}/edits/{edit}")

    def tracks(self, edit: str) -> list[dict]:
        return self.http.json("GET", f"{self.base}/edits/{edit}/tracks").get("tracks", [])

    def upload_bundle(self, edit: str, bundle: Path) -> dict:
        size = bundle.stat().st_size
        start_url = (
            f"{PLAY_API}/resumable/upload/androidpublisher/v3/applications/"
            f"{urllib.parse.quote(self.package)}/edits/{edit}/bundles?uploadType=resumable"
        )
        _, _, headers = self.http.request(
            "POST",
            start_url,
            body={},
            headers={"X-Upload-Content-Type": "application/octet-stream", "X-Upload-Content-Length": str(size)},
        )
        session = headers.get("location")
        if not session:
            raise Failure("the resumable upload did not return a session URL")
        _, uploaded, _ = self.http.request(
            "PUT",
            session,
            data=bundle.read_bytes(),
            headers={"Content-Type": "application/octet-stream"},
            timeout=UPLOAD_TIMEOUT_SECONDS,
        )
        return uploaded

    def update_track(self, edit: str, track: str, release: dict) -> dict:
        return self.http.json(
            "PUT",
            f"{self.base}/edits/{edit}/tracks/{urllib.parse.quote(track, safe=':')}",
            body={"track": track, "releases": [release]},
        )

    def validate(self, edit: str) -> None:
        self.http.json("POST", f"{self.base}/edits/{edit}:validate")

    def commit(self, edit: str, replace_in_review: bool, not_sent_for_review: bool = False) -> dict:
        # replace_in_review: Play's default, a newer release cancels the review of an older one and is reviewed in its
        # place. Otherwise Play answers with an error while something is in review and nothing is cancelled.
        behaviour = "CANCEL_IN_REVIEW_AND_SUBMIT" if replace_in_review else "ERROR_IF_IN_REVIEW"
        query = {"changesInReviewBehavior": behaviour}
        if not_sent_for_review:
            query["changesNotSentForReview"] = "true"
        return self.http.json("POST", f"{self.base}/edits/{edit}:commit?{urllib.parse.urlencode(query)}")


def publish(
    play: Play,
    track: str,
    bundle: Path,
    status: str,
    notes: list[dict],
    validate_only: bool = False,
    release_name: Optional[str] = None,
) -> dict:
    check_testing_track(track)
    edit = play.insert_edit()
    committed = False
    try:
        track = resolve_track(track, [entry.get("track") for entry in play.tracks(edit)])
        uploaded = play.upload_bundle(edit, bundle)
        version_code = uploaded.get("versionCode")
        if version_code is None:
            raise Failure(f"the bundle upload returned no version code: {uploaded}")
        log(f"uploaded {bundle.name}: version code {version_code}, sha256 {uploaded.get('sha256', '?')}")

        release: dict = {"versionCodes": [str(version_code)], "status": status}
        if release_name:
            # Play's default is "<version code> (<version name>)", which repeats the build number of "0.1.0 (370)".
            release["name"] = release_name[:RELEASE_NAME_LIMIT]
        if notes:
            release["releaseNotes"] = notes
        try:
            play.update_track(edit, track, release)
        except ApiError as error:
            if not is_draft_app_error(error):
                raise
            # Until Google has reviewed the app's first release, which is uploaded by hand, the owner releases by hand:
            # this run leaves the track alone rather than touch the release that is waiting for review.
            warning(
                f"Play still treats the app as a draft (its first release has not been reviewed yet), so version code "
                f"{version_code} was not released. Merges after the first review release directly; to release this "
                "build later, run the workflow again by hand."
            )
            return {"versionCode": version_code, "track": track, "status": status, "committed": False,
                    "skipped": "the app's first release is not reviewed yet"}

        if validate_only:
            play.validate(edit)
            log(f"validated version code {version_code} on {track!r} ({status}); nothing was committed")
            return {"versionCode": version_code, "track": track, "status": status, "committed": False}

        # A completed release replaces one still in review (the newest main wins); a draft never cancels a review.
        replace_in_review = status == "completed"
        try:
            play.commit(edit, replace_in_review=replace_in_review)
            sent_for_review = status == "completed"
        except ApiError as error:
            if not is_not_sent_for_review_error(error):
                raise
            # Saved but not sent; never combined with cancelling a review, which Google does not document.
            play.commit(edit, replace_in_review=False, not_sent_for_review=True)
            sent_for_review = False
            warning(
                "Play could not send this release for review automatically. It is saved on the track: open Play "
                "Console → Publishing overview and click 'Send changes for review'."
            )
        committed = True
        log(f"committed version code {version_code} on {track!r} ({status})")
        return {
            "versionCode": version_code,
            "track": track,
            "status": status,
            "committed": True,
            "sentForReview": sent_for_review,
        }
    finally:
        if not committed:
            try:
                play.delete_edit(edit)
            except (ApiError, OSError):
                pass  # an uncommitted edit expires on its own; the original error matters more


# ── Command line ────────────────────────────────────────────────────────────────────────────────────────────────────


def command_sign(args: argparse.Namespace) -> None:
    http = Http(access_token())
    fingerprint = sign_bundle(
        http,
        Path(args.bundle),
        Path(args.out),
        args.keystore_secret,
        args.password_secret,
        args.expect_sha256,
        Path(args.jdk_bin) if args.jdk_bin else None,
    )
    log(f"signed {args.out} with the upload key (certificate SHA-256 {fingerprint})")


def command_tracks(args: argparse.Namespace) -> None:
    play = Play(Http(access_token()), args.package)
    edit = play.insert_edit()
    try:
        for entry in play.tracks(edit):
            releases = entry.get("releases", [])
            summary = "; ".join(
                f"{r.get('name', '?')} [{r.get('status', '?')}] codes {','.join(r.get('versionCodes', []))}"
                for r in releases
            )
            log(f"{entry.get('track')}: {summary or 'no releases'}")
    finally:
        play.delete_edit(edit)


def command_publish(args: argparse.Namespace) -> None:
    check_testing_track(args.track)
    play = Play(Http(access_token()), args.package)
    result = publish(
        play,
        args.track,
        Path(args.bundle),
        args.status,
        release_notes(Path(args.notes_file) if args.notes_file else None, args.notes_language),
        validate_only=args.validate_only,
        release_name=args.release_name,
    )
    if result.get("skipped"):
        state = f"not released: {result['skipped']}"
    elif not result["committed"]:
        state = "validated only"
    elif result["status"] == "draft":
        state = "saved as a draft"
    elif result.get("sentForReview"):
        state = "sent for review"
    else:
        state = "saved, waiting for 'Send changes for review'"
    write_step_summary(
        "### Google Play\n\n"
        f"| Package | Track | Version code | Status | Result |\n|---|---|---|---|---|\n"
        f"| `{play.package}` | `{result['track']}` | {result['versionCode']} | {result['status']} | {state} |\n"
    )


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    sign = commands.add_parser("sign", help="sign a bundle with the upload key kept in Secret Manager")
    sign.add_argument("--bundle", required=True, help="the unsigned .aab")
    sign.add_argument("--out", required=True, help="where to write the signed .aab")
    sign.add_argument("--keystore-secret", required=True, help="projects/<project>/secrets/<name>[/versions/<v>]")
    sign.add_argument("--password-secret", required=True, help="projects/<project>/secrets/<name>[/versions/<v>]")
    sign.add_argument("--expect-sha256", required=True, help="the upload certificate's SHA-256 fingerprint")
    sign.add_argument("--jdk-bin", help="directory holding jarsigner and keytool (default: PATH)")
    sign.set_defaults(run=command_sign)

    tracks = commands.add_parser("tracks", help="list the app's tracks")
    tracks.add_argument("--package", required=True)
    tracks.set_defaults(run=command_tracks)

    pub = commands.add_parser("publish", help="release a signed bundle on a testing track")
    pub.add_argument("--package", required=True)
    pub.add_argument("--track", required=True, help="'internal', or a closed testing track ('alpha' by default)")
    pub.add_argument("--bundle", required=True, help="the signed .aab")
    pub.add_argument("--status", choices=["completed", "draft"], default="completed")
    pub.add_argument("--notes-file", help="release notes ('What's new'), cut to 500 characters")
    pub.add_argument("--notes-language", default="en-US")
    pub.add_argument("--release-name", help="the name Play Console shows for the release, e.g. '0.1.0 (370)'")
    pub.add_argument("--validate-only", action="store_true", help="upload and validate, then discard the edit")
    pub.set_defaults(run=command_publish)

    args = parser.parse_args(argv)
    prefix = "::error::" if os.environ.get("GITHUB_ACTIONS") == "true" else "error: "
    try:
        args.run(args)
    except (Failure, ApiError) as error:
        print(prefix + str(error), file=sys.stderr, flush=True)
        return 1
    except OSError as error:
        if not is_network_error(error):
            raise
        print(f"{prefix}network error talking to Google: {error}. Re-run the job.", file=sys.stderr, flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
