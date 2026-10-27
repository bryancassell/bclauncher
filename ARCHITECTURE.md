# Architecture

Decisions and conventions that more than one part of BC Launcher follows, with their reasons.

## Contents

- [Decisions](#decisions)
- [Platform versions](#platform-versions)
- [UI toolkit](#ui-toolkit)
- [Layers and packages](#layers-and-packages)
- [Dependency injection](#dependency-injection)
- [Tests](#tests)

## Decisions

| Section | Decision |
|---|---|
| [Platform versions](#platform-versions) | minSdk 29, so every Google TV device can install the app. compileSdk and targetSdk 37. |
| [UI toolkit](#ui-toolkit) | Compose for TV, with components from `androidx.tv.material3`. |
| [Layers and packages](#layers-and-packages) | A UI layer and a data layer in one `app` module, with no domain layer yet. |
| [Dependency injection](#dependency-injection) | Hilt, with KSP. |
| [Tests](#tests) | Local Robolectric tests, as a 1080p TV on API 29 and 37. |

## Platform versions

minSdk is 29 (Android TV 10), the version Google TV first shipped on, so the app runs on every Google TV device, even ones that no longer get updates. compileSdk and targetSdk are 37, the newest API level. Code that uses a newer API checks `Build.VERSION.SDK_INT` first (#1).

## UI toolkit

The UI uses Compose for TV. `MaterialTheme`, `Surface`, `Text` and other Material components come from `androidx.tv.material3`, not `androidx.compose.material3`, because the TV versions are built for D-pad focus and viewing from across a room. Android recommends Compose for TV and has deprecated Leanback (#1).

## Layers and packages

The code follows Android's [Guide to app architecture](https://developer.android.com/topic/architecture):

- The UI layer is Compose screens and their ViewModels, in `ui/<screen>`. A ViewModel exposes its screen's state as a `StateFlow`.
- The data layer is repositories, in `data`.
- A domain layer waits until two ViewModels need the same logic.

Everything is in one `app` module, since the app is small (#1).

## Dependency injection

Hilt provides dependencies, with KSP generating its code. It's Jetpack's recommended library, and a missing binding fails the build, not the running app (#1).

## Tests

Compose UI and screenshot tests run on Robolectric in `app/src/test`, so `./gradlew build` runs them without an emulator. They run as a 1080p TV on API 29 and 37, both ends of the supported range. Roborazzi screenshot tests fail when a screen differs from its baseline in `app/src/test/screenshots`, so visible changes show in the PR's diff (#1). Robolectric draws text differently on each OS, so only CI records and checks them, on Linux (#2).

Compose previews go in a `*Previews.kt` file, which the 80% coverage check skips, since no test runs them (#2).
