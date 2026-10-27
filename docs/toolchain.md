# Toolchain

How to build and check BC Launcher, and what CI runs.

## Building and checking a change

Gradle runs on JDK 25, pinned in `gradle/gradle-daemon-jvm.properties`, and downloads it if it's missing. CI relies on the same file.

`./gradlew build` checks a change. It runs:

- the unit tests in `app/src/test`, on Robolectric
- the coverage check, which fails when a class has under 80% line coverage. It skips the components and aggregation classes Hilt generates, and `*Previews.kt` files under `ui/`.
- lint
- Spotless, which checks formatting with ktlint in the `android_studio` style set in `.editorconfig`.
- the debug build, and the release build through R8, unsigned unless a keystore is set up ([Release builds](#release-builds)). An R8 error fails the build.

Other tasks:

- `./gradlew spotlessApply` formats the Kotlin and Gradle files. After editing `.editorconfig`, run `./gradlew --stop` and then `./gradlew spotlessCheck --rerun-tasks`. The Gradle daemon keeps reading the old file until it stops.
- `./gradlew jacocoDebugCoverageReport` writes the coverage report to `app/build/reports/jacoco/jacocoDebugCoverageReport/html/index.html`.
- `./gradlew connectedDebugAndroidTest` runs the instrumented tests in `app/src/androidTest` on a running emulator. Use a Google TV AVD on API 36, the newest Google TV image. With more than one device running, `ANDROID_SERIAL=<serial>` picks one.

## Screenshots

Only CI records and checks the baselines in `app/src/test/screenshots` (ARCHITECTURE.md, Tests), so a local `./gradlew build` doesn't compare them.

When CI's Build job fails on a screenshot, its `screenshot-comparisons` artifact has each changed screen beside its baseline.

To record baselines after a change to how a screen looks:

1. Merge `main` into the branch and push it, so the baselines match the merge commit CI checks. Then run `gh workflow run record-screenshots.yml --ref <branch>`.
2. When the run finishes (`gh run watch`), replace the baselines with what it recorded:
   `rm -rf app/src/test/screenshots && gh run download <run-id> -n screenshots -D app/src/test/screenshots`
3. Check the images in `git diff`, then commit them.

Record only to accept a change you meant to make, never to get a failing test to pass (CLAUDE.md, Testing Best Practices).

## Release builds

The release build runs R8 in full mode, which shrinks, optimizes and renames the code, and removes unused resources. Release builds are made locally. CI builds the release only unsigned, so the key stays on one machine.

### The keystore

Make it once, outside the repo, so it can't be committed or deleted by `git clean`:

```
keytool -genkeypair -v -keystore ~/.android/bclauncher-release.jks -alias bclauncher \
  -keyalg RSA -keysize 4096 -validity 10000
```

keytool asks for a password, which the key shares, since keytool makes PKCS12 keystores.

Keep a copy of the keystore and its password in a password manager. Every version must be signed with this key to install over the one before, and a lost key can't be regenerated, so the app would have to be uninstalled, losing its settings. Anyone with the key and password can sign an update that replaces the app.

### Signing

Gradle reads four values, each from an environment variable or else from `local.properties`, which git ignores:

| Name | Value |
|---|---|
| `BCLAUNCHER_KEYSTORE` | The keystore's absolute path |
| `BCLAUNCHER_KEYSTORE_PASSWORD` | Its password |
| `BCLAUNCHER_KEY_ALIAS` | `bclauncher` |
| `BCLAUNCHER_KEY_PASSWORD` | The same password |

Without `BCLAUNCHER_KEYSTORE`, the release builds unsigned, as `app-release-unsigned.apk`. With it, a missing value fails the build.

To build and install a signed release:

1. `./gradlew assembleRelease` writes `app/build/outputs/apk/release/app-release.apk`.
2. A debug build is signed with a different key, so remove it first with `adb uninstall io.github.bryancassell.bclauncher`. Then `./gradlew installRelease`.

To go back to debug builds, uninstall the release the same way. `connectedDebugAndroidTest` fails while a release is installed.

### Checking a release build

R8 removes and renames code that nothing seems to call, so code that's reached only by reflection can break in a release while every test passes on the debug build. After adding or updating AGP, a library, a keep rule or code that uses reflection:

1. Install a signed release on the Google TV emulator, as above. A throwaway keystore is fine for this.
2. Open BC Launcher from the TV's app list, and use what the change touched.
3. `adb logcat -d -b crash` prints nothing.

When something breaks, `app/build/outputs/mapping/release/usage.txt` lists the code R8 removed, and `retrace` in the SDK's `cmdline-tools` turns a renamed stack trace back into source names using `mapping.txt` from the same build. Keep rules go in `app/src/main/keepRules/*.keep`, each with a comment saying what needs it.

## CI

`.github/workflows/ci.yml` runs on every PR and on pushes to `main`:

- **Build:** `./gradlew build -Proborazzi.test.verify=true`, which also checks screenshots.
- **Instrumented tests:** `./gradlew connectedDebugAndroidTest` on a Google TV API 36 emulator. Gradle managed devices don't support TV images in AGP 9.4.1, so `android-emulator-runner` starts the emulator.

`.github/workflows/doc-growth.yml` runs **Doc growth**, which fails when a PR gives ARCHITECTURE.md or PRD.md more words (`wc -w`) than `main` has, unless the PR has the `grows-docs` label. Adding or removing a label reruns it.

A ruleset on `main` requires a PR and all three checks to pass, and blocks force pushes and deleting the branch. Nobody can bypass it. To merge past a CI failure that isn't the PR's fault, turn the ruleset off in Settings → Rules, then turn it back on.
