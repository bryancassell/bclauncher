# Toolchain

How to build and check BC Launcher, and what CI runs.

## Building and checking a change

Gradle runs on JDK 25, pinned in `gradle/gradle-daemon-jvm.properties`, and downloads it if it's missing. CI relies on the same file.

`./gradlew build` checks a change. It runs:

- the unit tests in `app/src/test`, on Robolectric
- the coverage check, which fails when a class has under 80% line coverage. It skips the components and aggregation classes Hilt generates, and `*Previews.kt` files under `ui/`.
- lint
- Spotless, which checks formatting with ktlint in the `android_studio` style set in `.editorconfig`.

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

## CI

`.github/workflows/ci.yml` runs on every PR and on pushes to `main`:

- **Build:** `./gradlew build -Proborazzi.test.verify=true`, which also checks screenshots.
- **Instrumented tests:** `./gradlew connectedDebugAndroidTest` on a Google TV API 36 emulator. Gradle managed devices don't support TV images in AGP 9.4.1, so `android-emulator-runner` starts the emulator.

`.github/workflows/doc-growth.yml` runs **Doc growth**, which fails when a PR gives ARCHITECTURE.md or PRD.md more words (`wc -w`) than `main` has, unless the PR has the `grows-docs` label. Adding or removing a label reruns it.

A ruleset on `main` requires a PR and all three checks to pass, and blocks force pushes and deleting the branch. Nobody can bypass it. To merge past a CI failure that isn't the PR's fault, turn the ruleset off in Settings → Rules, then turn it back on.
