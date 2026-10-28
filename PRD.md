# Overview
This project is a minimal replacement home screen for Google TV devices that focuses on quick access to apps and settings.

# Key Features
- The main feature of the home screen will be easy access to all installed apps via a grid of icons.
- Apps will be launched by a single click on the app icon
- Options for an app will be accessed via a long click on the app icon
- The options for an app will include
  - Reordering app order on the home screen
  - Launching the app
  - Uninstalling the app
  - Force stopping the app
  - Launching into the app settings
- The home screen will include an option to launch into the official Google TV home screen
- The home screen will include an option to launch into device settings
- The home screen will not override the official launcher's support for bringing up an overlay when the user long presses the home button
- The home screen will allow the user to set it to be the target for when the user presses the home button

# Design decisions

| Decision | Reason | Issues |
|---|---|---|
| The app is called "BC Launcher" on the TV. | It's the readable form of the repo's name, `bclauncher`. | #1 |
| The app installs only on TVs: its manifest requires `android.software.leanback`. | It's a home screen for Google TV, with no use on a phone. | #1 |
| The home screen lists the apps that open from a TV's app list (`CATEGORY_LEANBACK_LAUNCHER`), except BC Launcher itself. | They're the apps built for a remote, and the ones Google TV shows. Opening BC Launcher from its own list would do nothing. | #4 |
| An app's tile shows its banner, or its icon when it has none. | Android TV requires every app to provide a 320 × 180 banner with its name in it. | #4 |
| Apps show as tiles, six to a row. If one doesn't open, a message says so and the grid reloads. | 124 dp tiles, a card width in Android's TV layout guide, fit 30 apps on screen. An update can remove an app's TV activity unannounced, and the reload drops its tile. | #5 |
| Back on the home screen does nothing. | A home screen has nothing behind it to go back to. | #5 |
| If the apps don't load, a Try again button loads them again. | Once BC Launcher is the Home button's target, nothing else would leave the message. | #5 |
