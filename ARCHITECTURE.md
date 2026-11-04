# Architecture

Decisions and conventions that more than one part of BC Launcher follows, with their reasons.

## Contents

- [Decisions](#decisions)
- [Platform versions](#platform-versions)
- [UI toolkit](#ui-toolkit)
- [Layers and packages](#layers-and-packages)
- [Dependency injection](#dependency-injection)
- [Stored data](#stored-data)
- [Tests](#tests)
- [Home button](#home-button)

## Decisions

| Section | Decision |
|---|---|
| [Platform versions](#platform-versions) | minSdk 29, so every Google TV device can install the app. compileSdk and targetSdk 37. |
| [UI toolkit](#ui-toolkit) | Compose for TV, with components from `androidx.tv.material3`. |
| [Layers and packages](#layers-and-packages) | A UI layer and a data layer in one `app` module, with no domain layer yet. |
| [Dependency injection](#dependency-injection) | Hilt, with KSP. |
| [Stored data](#stored-data) | Typed DataStore as JSON with kotlinx.serialization, included in Auto Backup. |
| [Tests](#tests) | Local Robolectric tests, as a 1080p TV on API 29 and 37. |
| [Home button](#home-button) | Google TV's launcher stays Android's Home app, so an accessibility service in its own process takes the Home key. |

## Platform versions

minSdk is 29 (Android TV 10), the version Google TV first shipped on, so the app runs on every Google TV device, even ones that no longer get updates. compileSdk and targetSdk are 37, the newest API level. Code that uses a newer API checks `Build.VERSION.SDK_INT` first (#1).

## UI toolkit

The UI uses Compose for TV. `MaterialTheme`, `Surface`, `Text` and other Material components come from `androidx.tv.material3`, not `androidx.compose.material3`, because the TV versions are built for D-pad focus and viewing from across a room. Android recommends Compose for TV and has deprecated Leanback (#1).

## Layers and packages

The code follows Android's [Guide to app architecture](https://developer.android.com/topic/architecture):

- The UI layer is Compose screens and their ViewModels, in `ui/<screen>`. A ViewModel exposes its screen's state as a `StateFlow`. A screen starts other apps' activities itself, with an intent from the data layer, and tells its ViewModel only when one fails, since Android's [UI events guide](https://developer.android.com/topic/architecture/ui-layer/events) treats navigation as UI logic (#5).
- The data layer is repositories, in `data`.
- A domain layer waits until two ViewModels need the same logic.

Everything is in one `app` module, since the app is small (#1).

## Dependency injection

Hilt provides dependencies, with KSP generating its code. It's Jetpack's recommended library, and a missing binding fails the build, not the running app (#1).

Blocking calls run on the dispatcher injected as `@IoDispatcher`, so tests can pass a test dispatcher (#4).

## Stored data

BC Launcher stores data, such as the app order, with typed DataStore: one `@Serializable` class per file, in JSON via kotlinx.serialization. Unlike Preferences DataStore, it keeps a list in order. Each file has one `DataStore`, a Hilt singleton, since two in one process can't share a file. Only the main process uses them: `:home_button` would need `MultiProcessDataStoreFactory`. Auto Backup includes the files, so a reinstall or a new device gets them back (docs/toolchain.md, Checking backup and restore) (#8).

## Tests

Compose UI and screenshot tests run on Robolectric in `app/src/test`, so `./gradlew build` runs them without an emulator. They run as a 1080p TV on API 29 and 37, both ends of the supported range. Roborazzi screenshot tests fail when a screen differs from its baseline in `app/src/test/screenshots`, so visible changes show in the PR's diff (#1). Robolectric draws text differently on each OS, so only CI records and checks them, on Linux (#2).

Compose previews go in a `*Previews.kt` file under `ui`, which the 80% coverage check skips, since no test runs them (#2).

Tests that start an `@AndroidEntryPoint` activity run on `HiltTestApplication`, where `@TestInstallIn` modules replace platform sources such as `PackageSource` with fakes (#4).

## Home button

Google TV's launcher stays Android's Home app whatever the user chooses as the default. Its Home filter has priority 2, which only a system app can have, and Android goes by the default only between candidates of the same priority. So a `CATEGORY_HOME` intent opens Google TV, and BC Launcher declares no Home filter. Instead, `HomeButtonService`, an accessibility service the user turns on, takes the remote's Home key, hands short presses on to Android, and starts `MainActivity` in place of Google TV's home. It also does that when it connects, and for 1.5 s after a Home press, if Google TV's home comes to the front, so an entry that opens Google TV's home (#6) has to allow for it. It runs in its own process, `:home_button`, which shares no memory with the home screen (#10).
