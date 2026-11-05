# Codebase cleanup — 0.14.1

VersionCode 106. Built and signature-verified; not installed. The user-confirmed on-phone baseline remains 0.14.0.

Removed repeated clock/widget/shadow success messages, hardcoded build strings, the delayed date-state diagnostic callback, an unused depth-description helper, and a sampling wrapper used only by an older integration probe. Error/capability reporting remains. Rendering ownership, hook signatures, animation logic and saved settings are unchanged. Java packages were preserved, and EditorConfig/final-newline cleanup avoid a broad renderer reformat/refactor.

Docs, release history and APKs now have dedicated folders. Tests are grouped by execution environment and have maintained JVM, Android and native-glyph runners. Native fixtures no longer include failed headless font-initialization experiments. Production packaging only includes `app/src/main/`.

The update key was copied byte-for-byte to private `.signing/alpha-debug.jks`. A missing key stops a build instead of generating a replacement. CLI version and SDK values come from Gradle, with a generated packaging manifest and versioned artifact output.

All 30,039 historical scratch files are archived outside the project in a 953.9 MiB ZIP, with SHA-256 verification against decompressed contents before loose copies were removed. Curated regression evidence and native glyph fixtures remain maintained. All 106 APKs are indexed with checksums/status labels. Early exports, legacy builds and JVM crash logs are preserved; original analysis APKs and the user's conversation file remain untouched.

Default build, matching signing certificate, eight JVM suites, thirteen Android suites and eight native checks pass. The layout checker covers script syntax and local links. See [validation](../VALIDATION.md) and [archive map](../ARCHIVE.md).
