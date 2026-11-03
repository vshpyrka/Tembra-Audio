# Publishing Tembra Audio to Google Play closed testing

Every push to `main` that changes the Android app becomes a release on the Google Play **closed testing** track: a
merged pull request, a Renovate merge or a direct push. GitHub Actions tests and builds the release bundle, signs it
with the upload key and hands it to the Google Play Developer API; Google reviews the release and then offers it to
the testers on your list. The pipeline cannot release to production, and it refuses open testing.

This page explains how that works, why the keys and credentials are held the way they are, the one-time setup, and
what to do when a run fails. The pieces live in:

| File | What it does |
|---|---|
| `.github/workflows/play-closed-testing.yml` | The workflow: a `build` job and a `publish` job |
| `app/tools/play/publish.py` | Signs the bundle with the upload key and calls the Play Developer API (Python standard library only) |
| `app/tools/play/test_publish.py` | Its tests: `python3 -m unittest discover -s app/tools/play` |
| `app/tools/play/setup-wizard.sh` | Walks you through the one-time setup, stage by stage |
| `app/androidApp/build.gradle.kts` | Takes the version code from `-Ptembra.versionCode`, limits the ABIs, ships the engine's symbols |

## From a merge to the testers

1. **`build` job** (Ubuntu runner, about 10 minutes with a warm cache). Checks out `main`, counts its commits for the
   version code, runs `checkAndroidHost` and `:androidApp:bundleRelease` with the same zero-warnings gates as CI,
   and keeps the bundle as a short-lived artifact. The bundle is **unsigned**: this job holds no credentials of any
   kind, so the hundreds of build dependencies it executes never see a key or a token.
2. **`publish` job** (a few minutes). Never runs Gradle. It asks GitHub for an OIDC token, exchanges it with Google
   Cloud for a 30-minute access token of the publishing service account, reads the upload key from Secret Manager into
   a private temporary folder, signs the bundle with the JDK's `jarsigner`, checks that the signer is the registered
   upload certificate and deletes the key. It then opens a Play edit, uploads the bundle, puts it on the closed
   testing track with release notes taken from the merge, and commits.
3. **Google Play** reviews the release. Once it passes, testers who opted in get the update through the Play Store
   like any other app update.

The release notes ("What's new") are the pull request title for a pull request merge, the merge subject and the five
newest commit subjects of the merged branch for a local `git merge --no-ff`, and the commit subject otherwise. They end
with the build number and the short commit hash, and are cut to Play's 500-character limit with that last line kept.

Pushes that only touch documentation, the iOS app, the performance pipeline or other tools do not start the
workflow, because they cannot change what Android users install. Run it by hand when you want a release anyway
(see [Day to day](#day-to-day)).

### Version numbers

Play refuses a version code it has already seen, so each upload needs a larger one. The workflow passes the number of
commits on `main` (`git rev-list --count HEAD`, 364 on 2026-11-03), which only grows as `main` moves forward and maps
each Play build back to one commit. `versionName` becomes `0.1.0 (364)`; change the `0.1.0` in
`app/androidApp/build.gradle.kts` by hand when a release deserves a new name. Local builds keep version code 1.

## Keys and credentials

Google's current guidance shapes every choice below. The aim is that no long-lived secret sits in GitHub, that the
key which users' devices trust never exists outside Google, and that a leak of anything the pipeline holds can be
undone.

### The app signing key stays at Google

Google Play signs the APKs it delivers with the **app signing key**, and Android only installs an update signed by the
same key, so losing or leaking it would end the app. With [Play App Signing][play-app-signing], which every app
created after August 2021 uses, Google generates that key and keeps it in its own key management service; nobody can
download it. Keep the default **Google-generated key** when Play Console offers the choice during the first upload.
Supplying your own key only makes sense if the same key must also sign builds for another store.

A Google-generated key also brings Play's **quantum-ready hybrid signing**: Google enrols new apps automatically, and
devices on Android 17 and higher then verify the app with a hybrid RSA 4096 and ML-DSA-65 signature, while older
devices use the classical one.

### The upload key is resettable and lives in Secret Manager

You sign what you upload with a separate **upload key**; Google checks that signature to know the bundle came from
you, then re-signs for delivery. Play requires an RSA key of at least 2048 bits in a Java keystore and recommends a
different key from the app signing key. If the upload key is lost or leaked, Play Console can
[reset it][upload-key-reset], and the app carries on: that is why the upload key, not the app signing key, is the one
a CI system may hold.

The wizard generates it on your Mac: RSA 4096 in a PKCS12 keystore, valid until 2054, with a random 40-character
password. Both go straight into Google Cloud **Secret Manager** as two secrets, and the local copies are deleted.
Apart from you as the project's owner, the only identity allowed to read them is the publishing service account,
which only the publishing workflow on `main` can act as (next section). An offline backup in a password manager is optional: a lost upload key costs an
upload key reset, not the app.

Before every upload, `publish.py` compares the signer's SHA-256 fingerprint with the repository variable
`PLAY_UPLOAD_CERT_SHA256`, so a wrong or swapped key fails in the workflow instead of at Google.

### GitHub reaches Google Cloud without a stored key

A service account JSON key in a GitHub secret would be a password that never expires. Instead, the workflow uses
[Workload Identity Federation][wif] through Google's own `google-github-actions/auth` action, the method Google
recommends over keys: GitHub signs a short-lived OIDC token describing the run, and Google Cloud exchanges it for an
access token of the `play-publisher` service account. Google Cloud admits the token only when **all** of these hold
(the provider's attribute condition):

- the repository's numeric ID is this repository's, and the owner's numeric ID is yours. Numeric IDs are never
  reused, so a deleted and re-created repository or account of the same name cannot take over;
- the ref is `refs/heads/main`;
- the workflow file is `.github/workflows/play-closed-testing.yml` on `main`.

A pull request, a branch push or any other workflow is turned away, even one that copies this workflow's steps. The
repository variables the workflow reads (`GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_PLAY_SERVICE_ACCOUNT`, the secret
names, the fingerprint) only name resources, so they are variables, not secrets.

This also matters while the repository is private: on GitHub Free a private repository has no environments, which
could otherwise limit a secret to `main`, so a repository secret is readable by a workflow on any branch pushed by
anyone with write access, including Renovate's token.

### The service account can only release to testing

In Play Console the service account gets one app permission, **Release apps to testing tracks**, and no account
permission. It cannot touch production, the store listing, pricing or other apps. That permission covers open
testing too, so `publish.py` refuses the `production` and `beta` (open testing) tracks before making any request. In Google Cloud it holds no project
role, only read access to the two secrets.

### What this does not protect against

Anything that can put code on `main` can release it to testers: you, a merged pull request, and Renovate, which
merges its own green pull requests with your token. Production stays out of reach whatever lands on `main`, because
the service account has no permission for it. Open testing does not: the refusal of the `beta` track is a check in
`publish.py`, and a change on `main` could remove it, so review changes to the workflow and the script as carefully
as release code. Keep the Renovate token's expiry short, and look at what Renovate merged when a release surprises
you.

### Options considered

| Option | Why not |
|---|---|
| Keystore and passwords in GitHub repository secrets | The common setup. Without environments (private repository on GitHub Free) any workflow on any branch could read them, and they never expire. |
| Upload key in Cloud KMS on a hardware security module | The private key would never leave the HSM. But Cloud KMS stores no certificates, `jarsigner` through Google's PKCS #11 library needs one, and no Google documentation covers signing app bundles this way (checked 2026-11-03); Play itself describes the upload key as a Java keystore. Because the upload key is resettable, the extra protection is small for the added moving parts. It stays a possible upgrade. |
| A service account JSON key | Google Cloud and the `auth` action both recommend Workload Identity Federation over keys. |
| A third-party upload action or Gradle Play Publisher | Each is community code that would receive the access token; the Play Developer API is the official interface, and `publish.py` calls it directly with no dependency outside Python's standard library. |

## What Play requires of this app

Checked against the release bundle built on 2026-11-03:

| Requirement | State |
|---|---|
| Target API level 36 for new apps and updates (since 31 August 2026) | `targetSdk = 36` |
| 16 KB memory pages for native code | Every `.so` in the bundle has 16 KB-aligned `LOAD` segments (NDK 29) |
| No device gets an app that cannot run | The bundle carries `arm64-v8a` and `x86_64` only, the engine's two ABIs, so Play offers the app to 64-bit devices only. Before this change the androidx libraries added `armeabi-v7a` and `x86` folders without the engine. |
| Crash reports Play can read | R8's mapping file and the native symbol tables (`SYMBOL_TABLE`) ride in the bundle |
| A privacy policy | Needed: the app records from the microphone |
| Foreground service declaration | Media playback now, microphone once recording (feature 010) is on `main`; each needs a short video |

## One-time setup

Run the wizard from the repository root on your Mac. It opens each page, says what to click, runs the Google Cloud
and GitHub commands itself, and remembers non-secret values in `~/.config/tembra-audio/play-setup.env`. Stop it with
Ctrl-C at any point; a re-run walks the stages from the start, and each stage notices what already exists (the
project, the trust, the key, the variables, a downloaded first bundle) and keeps it, so you only press Enter through
the finished ones:

```sh
app/tools/play/setup-wizard.sh
```

It needs `gcloud` (`brew install --cask gcloud-cli`), `gh` signed in, a JDK's `keytool`, and `openssl`. Merge the
pull request that adds the workflow before stage 14, which starts the workflow on `main`.

| # | Stage | You do | The wizard does |
|---|---|---|---|
| 1 | Tools | Install anything missing | Checks the tools, runs `gcloud auth login` |
| 2 | Play Console developer account | Register (US$25 once), verify your identity, turn on 2-Step Verification | Opens the pages |
| 3 | Create the app | Create "Tembra Audio": app, free, default language | |
| 4 | Google Cloud project | Choose a project ID | Creates the project |
| 5 | Billing | Link a billing account | Waits until billing shows as enabled |
| 6 | APIs and service account | | Enables the Play Developer, IAM, IAM Credentials, STS and Secret Manager APIs; creates `play-publisher` with no keys and no project role |
| 7 | Keyless trust | | Creates the Workload Identity pool `github` and provider `tembra-audio` with the attribute condition; lets that repository act as the service account |
| 8 | Upload key | Confirm | Generates the key, stores it and its password in Secret Manager, grants the service account read access, saves the public certificate to `~/.config/tembra-audio/upload_certificate.pem` |
| 9 | Repository variables | | Sets the six variables with `gh variable set` |
| 10 | Service account in Play Console | Invite it with **Release apps to testing tracks** on Tembra Audio only | Prints the e-mail address |
| 11 | App content | Privacy policy, app access, ads, content rating, target audience, data safety, foreground services | Lists each declaration |
| 12 | Store listing | Descriptions, icon, feature graphic, screenshots, category Music & Audio | Lists the assets |
| 13 | Closed testing track | Add testers to the **Alpha** track, pick countries | |
| 14 | First signed bundle | | Runs the workflow with `publish` off, waits, downloads the signed bundle |
| 15 | First release by hand | Upload that bundle to closed testing, keep the Google-generated key, send for review | Prints the upload fingerprint to compare |

Stage 2 can take days while Google verifies a new account; stop the wizard there and run it again afterwards.

**Why the first upload is manual.** The Play Developer API cannot register a new app, and the first bundle you upload
is what Play App Signing records as the upload key. After it, the API can do the rest.

**Testers.** Closed testing reaches the people on the track's tester lists (e-mail lists or Google Groups). Each
tester opens the opt-in link from Closed testing → Alpha → Testers, joins, and installs from the Play Store. A
personal developer account created after 13 November 2023 needs 12 testers opted in for 14 days in a row before it
can [apply for production access][testing-requirements]. Closed testing itself has no such threshold.

**Track names.** Play Console's default closed testing track is **Alpha**, and its API name is `alpha`; the API's
`beta` is open testing. If you create a custom closed track, set the `PLAY_TRACK` variable to its name. A wrong name
fails with the list of the app's real tracks.

## Day to day

- **Merge to `main`.** The release appears on the track, goes to review, then reaches the testers. If a newer release
  arrives while an older one is still in review, Play cancels the older review and reviews the newer one (Play's
  default for API commits), so the newest `main` wins. A burst of merges, such as Renovate's Monday batch, therefore
  ends with one review of the last merge.
- **Before the first review passes**, Play treats the app as a draft and accepts no automatic release; runs in that
  window upload nothing, say so in a warning, and leave your first release untouched.
- **Release by hand.** `gh workflow run play-closed-testing.yml -f publish=true` releases the current `main`, for
  example after a documentation-only merge. With `-f publish=false` the run builds and signs only and keeps the signed
  bundle as an artifact for three days.
- **Pause publishing.** Delete the `GCP_PLAY_SERVICE_ACCOUNT` variable: merges then only build and test. Or disable
  the workflow: `gh workflow disable play-closed-testing.yml`.
- **Hold releases for review in Play Console.** Set `PLAY_RELEASE_STATUS` to `draft`: each run then saves a draft
  release that you roll out by hand. A draft never cancels a review: while something is in review, such a run fails
  with Play's message instead.
- **Cost.** Play charges nothing per release. Secret Manager stays inside its free tier (two secret versions, two
  reads per release), and Workload Identity Federation and the Play Developer API are free. GitHub bills a private
  repository's Linux minutes at about $0.006 each; a release should take 10 to 15 (CI's Android job takes 8 to 9 on
  `main`), well under $0.10.

| Variable | Set by | Meaning |
|---|---|---|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | wizard | `projects/<number>/locations/global/workloadIdentityPools/github/providers/tembra-audio` |
| `GCP_PLAY_SERVICE_ACCOUNT` | wizard | `play-publisher@<project>.iam.gserviceaccount.com`; while it is unset the publish job is skipped |
| `PLAY_UPLOAD_KEYSTORE_SECRET` | wizard | `projects/<project>/secrets/play-upload-keystore` |
| `PLAY_UPLOAD_PASSWORD_SECRET` | wizard | `projects/<project>/secrets/play-upload-keystore-password` |
| `PLAY_UPLOAD_CERT_SHA256` | wizard | The upload certificate's SHA-256 fingerprint |
| `PLAY_TRACK` | wizard | `alpha` |
| `PLAY_RELEASE_STATUS` | optional | `completed` (default) or `draft` |
| `PLAY_NOTES_LANGUAGE` | optional | Language of the release notes, `en-US` by default |

## When a run fails

| Where | Message | Cause and fix |
|---|---|---|
| Authenticate | `unauthorized_client` or "rejected by the attribute condition" | The run was not the workflow file on `main` of this repository: a renamed workflow file or a renamed repository. Run the wizard again; stage 7 rewrites the condition with the current names. |
| Authenticate | `iam.serviceAccounts.getAccessToken` denied | The repository cannot act as the service account: run the wizard again (stage 7 restores the binding). |
| Sign | Secret Manager `PERMISSION_DENIED` | The service account lost read access to a secret: run the wizard again (stage 8 restores it). |
| Sign | "not the registered upload certificate" | The key in Secret Manager and `PLAY_UPLOAD_CERT_SHA256` disagree. Compare both with Play Console → App integrity → Play app signing → Upload key certificate. |
| Publish | "Package not found" or 404 | The first bundle has not been uploaded by hand yet (wizard stages 14 and 15). |
| Publish | 403 "The caller does not have permission" | The service account is missing from Play Console's Users and permissions, or lacks **Release apps to testing tracks** for Tembra Audio. |
| Publish | "the app has no track …" | `PLAY_TRACK` does not name one of the app's tracks; the message lists them. |
| Sign or publish | "network error talking to Google" | A connection dropped. Re-run the failed jobs; an uncommitted upload leaves nothing behind. |
| Publish | "version code … has already been used" | This commit is already on Play, typically a re-run after a successful upload. Nothing to do. |
| Publish | Warning: "Play still treats the app as a draft" | The first release, uploaded by hand, has not been reviewed yet. The run released nothing on purpose; merges after the review release directly. |
| Publish | Warning: "could not send this release for review automatically" | Play needs you to send changes for review, for example after a rejection. Open Publishing overview and click **Send changes for review**. |

Google Play can still reject a release in review for policy reasons; Play Console's inbox and Publishing overview say
why, and the next merge after the fix releases again.

## If the upload key is lost or leaked

The app signing key is unaffected, so the app and its users are safe; only uploads stop until the reset.

1. Generate a new key and certificate, as wizard stage 8 does: `keytool -genkeypair -keystore upload.p12
   -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 -validity 10000`, then
   `keytool -exportcert -rfc -keystore upload.p12 -alias upload -file upload_certificate.pem`.
2. Play Console → Test and release → App integrity → Play app signing → **Request upload key reset**, give the reason
   and upload `upload_certificate.pem`. Google tells you when the new key takes effect.
3. Then store the new key and its password as new versions of the two secrets
   (`gcloud secrets versions add play-upload-keystore --data-file=upload.p12`, and the same for the password), update
   `PLAY_UPLOAD_CERT_SHA256`, delete the local files, and disable the old secret versions.

[play-app-signing]: https://support.google.com/googleplay/android-developer/answer/9842756
[upload-key-reset]: https://developer.android.com/studio/publish/app-signing#reset_upload_key
[wif]: https://github.com/google-github-actions/auth#workload-identity-federation-through-a-service-account
[testing-requirements]: https://support.google.com/googleplay/android-developer/answer/14151465
