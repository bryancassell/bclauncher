# Architecture

The technical decisions and conventions that more than one part of BC Launcher follows, each with its reason.

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
| [Tests](#tests) | Compose UI tests and screenshot tests run locally on Robolectric. Roborazzi takes the screenshots. |

## Platform versions

minSdk is 29 (Android TV 10), the version Google TV first shipped on, so the app runs on every Google TV device, including older TVs that no longer get updates. compileSdk and targetSdk are 37, the newest API level. Code that uses a newer API checks `Build.VERSION.SDK_INT` first (#1).

## UI toolkit

The UI is Jetpack Compose with Compose for TV. `MaterialTheme`, `Surface`, `Text` and other Material components come from `androidx.tv.material3`, not `androidx.compose.material3`, because the TV versions are built for D-pad focus and viewing from across a room. Android recommends Compose for TV for TV apps and has deprecated Leanback (#1).

## Layers and packages

The code follows Android's [Guide to app architecture](https://developer.android.com/topic/architecture):

- The UI layer is Compose screens and their ViewModels, in `ui/<screen>`. A ViewModel exposes its screen's state as a `StateFlow`.
- The data layer is repositories, in `data`.
- A domain layer waits until two ViewModels need the same logic.

Everything is in the one `app` module, because the app is small (#1).

## Dependency injection

Hilt provides dependencies, with KSP generating its code. It's Jetpack's recommended library, and a missing binding fails the build instead of the running app (#1).

## Tests

Compose UI tests and screenshot tests run on Robolectric in `app/src/test`, so `./gradlew build` runs them without an emulator. Screenshot tests use Roborazzi with `RobolectricDeviceQualifiers.Television1080p`, so they match a 1080p TV. Their baselines are in `app/src/test/screenshots`, and every test run compares the screens with them (#1).
