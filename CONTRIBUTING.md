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

The [Build workflow](.github/workflows/check.yml) checks repository contents and the reflection inventory, builds an unsigned APK and runs JVM checks on pushes to `main` and `dev` and on pull requests. APKs and check results are available as workflow artifacts for seven days. They are unsigned and cannot update an official installation without the same signing key. Native-device checks remain local.

## Branches and pre-releases

- Target the `dev` branch for pull requests. `main` holds stable releases.
- Versioning uses `MAJOR * 10_000_000 + MINOR * 100_000 + PATCH * 1_000 + BUILD`, where `BUILD` is `1..998` for `X.Y.Z-dev.<N>` pre-releases and `999` for stable `X.Y.Z` builds (`20000999` for `2.0.0`). This ensures a `dev` build always upgrades cleanly to its matching stable release.
- Pushes to `dev` run [Dev Pre-release](.github/workflows/dev-prerelease.yml), which validates the repository, computes the next `vX.Y.Z-dev.<N>` tag and commit changelog, builds a signed APK, runs JVM checks, publishes a GitHub pre-release, and keeps the latest 10 `dev` pre-releases.
- Signed pre-releases in CI require four repository secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, and `SIGNING_KEY_PASSWORD`.

Workflow actions are pinned by commit. Dependabot proposes monthly updates; they are not merged automatically.

## Before sharing

Run `scripts/check-repository.ps1` and inspect `git diff --cached`. Keep signing keys, APKs, preference backups, decompiled Samsung sources, screenshots and raw device logs out of commits. Local evidence in `docs/validation/` is deliberately ignored; share a redacted excerpt when it is needed to explain a bug.

Contributions are distributed under the repository's [source-available license](LICENSE). Do not add third-party code or assets without preserving their terms and attribution. Samsung APKs and SDK implementations are not distributed in this repository.
