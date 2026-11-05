![Dressroom Advanced — Your lockscreen, your way!](docs/assets/banner.png)

# Dressroom Advanced

An LSPosed API 102 module that adds more control to Samsung's lockscreen editor. Clock and widget settings live inside Dressroom, with live previews and the normal Apply/cancel workflow.

**Current alpha: 0.19.0.** Built and tested on selected One UI 8.5 and 9.0 packages. See [validation](docs/VALIDATION.md) for what has actually been checked.

## Features

- Clock typography, manual stretch, size, position, shadows and blur controls.
- Independent date/weather visibility, including a weather-only layout.
- Wallpaper subject depth with Samsung Object Capture, automatic detection and crop/zoom tracking.
- Widget scaling, placement, custom grid options and independent depth settings.
- Separate portrait/landscape profiles on supported large-screen layouts.
- Now Bar extras, including a retained charging card and custom text card.
- Fullscreen media artwork on the reference One UI 9 provider, with native song-change motion and coordinated clock/widget transitions.

Features depend on the installed Samsung packages. Device-wide blur forcing is not included.

## Compatibility

| Tested setup | Status |
| --- | --- |
| One UI 9 · Dressroom 3.0.10.38 · AOD 8.9.66.1 | Main reference; editor, depth, widgets and media paths tested on the phone |
| One UI 8.5 custom ROM on P613 · Dressroom 2.9.60.50 · Clock Style 8.9.35.60 | Core clock controls, widgets, depth, orientation profiles and selected Extras tested; fullscreen media remains unavailable |
| Other 8.5 AOD builds, One UI 8.0, foldables and other models | Not certified; inspected declarations alone do not establish device support |

Compatibility is per feature and package/build combination. The One UI version alone is not enough. The tablet is running a custom ROM, so its results do not establish stock tablet or A-series compatibility. See the [adapter notes](docs/compatibility/IMPLEMENTATION.md) and [compatibility audit](docs/compatibility/README.md).

## Use

1. Install an APK signed with the same key as your existing module, or build and sign your own.
2. Enable the module in LSPosed for Dressroom (`com.samsung.android.app.dressroom`), SystemUI (`com.android.systemui`), and your clock provider: AOD (`com.samsung.android.app.aodservice`) or Clock Style (`com.samsung.android.app.clockpack`).
3. Reboot, then open Wallpaper and style's lockscreen editor.
4. Tap the clock for its **Advanced** tab, or use the bottom-toolbar **Advanced** button for **Widgets** and **Extras**.
5. Use Samsung's Apply/Done action to save. Cancelling keeps the previously committed settings.

This is an alpha. Some native rendering paths remain device-specific. Known deferred media polish includes widgets disappearing immediately on player opening and the fullscreen song-change shimmer needing tuning. Check [current validation](docs/VALIDATION.md) before reporting a regression.

## Build

Requires JDK 17+, Android SDK platform 36 and build-tools 36.0.0.

```powershell
./build-local.ps1 -Unsigned
```

Pass `-Sdk`, `-ApiAar` or `-BuildDir` for other local paths. Version and SDK values come from [app/build.gradle.kts](app/build.gradle.kts). The CLI generates its build manifest from those values. Signed builds produce `artifacts/current/dressroom-advanced-<version>.apk`; unsigned builds use the `-unsigned.apk` suffix.

Unsigned builds compile and package without private credentials. To install a contributor build, sign it with your own key; see [Contributing](CONTRIBUTING.md). The maintainer's signed update command is `./build-local.ps1`, using the private `.signing/alpha-debug.jks` or `-Keystore`. A missing key stops a signed build; it never silently generates a replacement. A separately signed APK cannot update an existing installation signed with another key.

## Test

```powershell
./test-local.ps1 -Suite Jvm -SkipBuild
./test-local.ps1 -Suite Android -Adb 'C:/Program Files/platform-tools/adb.exe' -SkipBuild
./test-native.ps1 -Adb 'C:/Program Files/platform-tools/adb.exe'
```

The Android suite needs the connected rooted Samsung test phone. The native suite temporarily installs and removes a dedicated instrumentation package. Neither command installs the module or restarts SystemUI. See [test coverage and fixture requirements](tests/README.md).

## Layout

| Path | Contents |
| --- | --- |
| `app/src/main/` | Runtime/editor source, resources and LSPosed metadata |
| `tests/` | JVM, Android, native and manual integration contracts |
| `scripts/` | Shared build tooling and repository checks |
| `docs/` | Architecture, current validation, research, release history and README assets |
| `artifacts/current/` | Current build and installed recovery baseline |
| `artifacts/releases/` | Historical APKs, including failed diagnostic builds |
| `.build/` | Disposable compilation/test output |
| `.signing/` | Private persistent signing key |

[Architecture](docs/ARCHITECTURE.md) explains renderer ownership and feature boundaries. [Archive map](docs/ARCHIVE.md) locates the historical scratch data and original path mappings. History preserves failed experiments as evidence; an old release note is not a current correctness claim.

## Repository

This directory is the Git root. Build output, signing material and raw device evidence are ignored. Samsung APKs and decompiled source dumps are not included. GitHub checks compile an unsigned APK and run the JVM contracts; they do not replace on-device rendering tests.

See [Contributing](CONTRIBUTING.md) for build/signing instructions and change guidelines. License selection is pending; no open-source license has been assigned yet.
