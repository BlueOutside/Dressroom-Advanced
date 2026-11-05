# Contributing

Start with [the architecture map](docs/ARCHITECTURE.md). The editor, AOD provider and SystemUI do not share a view tree or an Apply lifecycle. A change that looks right in Dressroom can still be wrong after saving or waking the phone.

## Local build

Use JDK 17+, Android SDK platform 36 and build-tools 36.0.0. From the repository root:

```powershell
./build-local.ps1 -Unsigned
./test-local.ps1 -Suite Jvm -SkipBuild
./scripts/check-layout.ps1
./scripts/check-repository.ps1
```

The build script resolves API 102 from the Gradle cache or Maven Central. Pass `-ApiAar` for an existing local copy. It does not require Gradle or a private signing key. Unsigned APKs cannot be installed directly. Android Studio can also open the Gradle project; there is currently no checked-in Gradle wrapper.

For an installable personal build, generate your own development key once. Keep it for subsequent updates of your own build:

```powershell
New-Item -ItemType Directory -Force .signing
keytool -genkeypair -keystore .signing/contributor.jks -alias alpha -keyalg RSA -keysize 3072 -validity 3650 -storepass android -keypass android -dname 'CN=Local Development'
./build-local.ps1 -Keystore .signing/contributor.jks
```

These are development credentials, not a release-key policy. Your key cannot update an APK signed by someone else. Do not overwrite the maintainer's key or uninstall an existing installation without backing up its data. Official update builds keep their existing private signing identity.

## Changes and tests

- Keep new hook lookups scoped to the owning process/class loader. Report unsupported capabilities once and let unrelated features work.
- Keep per-frame code free of bitmap readback, reflection discovery and large allocations. Reuse geometry only while its ownership and transform are unchanged.
- Keep editor draft state separate from committed state. Opening a panel must not be required to restore settings.
- Put tests in `tests/`, not the module source tree. Add meaningful state/geometry contracts when behavior changes; see [the suite guide](tests/README.md).
- Explain unusual native ownership or timing in a short comment. A comment should say why the code exists, not narrate each assignment.
- Include the affected One UI/app versions and what you actually checked in a pull request. JVM tests alone cannot verify a Samsung animation.

The GitHub workflow builds an unsigned APK and runs JVM checks. Rooted-device and Samsung-native suites remain explicit local steps. It never installs the module or reloads SystemUI.

## Before sharing

Run `scripts/check-repository.ps1` and inspect `git diff --cached`. Keep signing keys, APKs, preference backups, decompiled Samsung sources, screenshots and raw device logs out of commits. Local evidence in `docs/validation/` is deliberately ignored; share a redacted excerpt when it is needed to explain a bug.

License selection is pending. Do not add third-party code or assets without preserving their terms and attribution. Samsung APKs and SDK implementations are not distributed in this repository.
