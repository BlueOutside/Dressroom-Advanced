# 0.15.0 alpha: initial version bindings

The previous code assumed one Dressroom/AOD build throughout the editor and core renderer. This build introduces shared bindings for native tab APIs, model/renderer ownership, weather/date edits, position/size operations and manual font rendering.

The first 8.5 targets are Dressroom 2.9.60.50/.51, AOD 8.9.32.95 and the P613 port's Clock Style 8.9.35.60. The tablet's Clock Style package has its own renderer and shadow methods and a separate scoped height normalizer. It is included as a read-only settings consumer and module scope; Samsung packages are not replaced.

The 8.5 settings commit follows the normal native save coroutine, preserving one frozen snapshot per suspended Apply. Generic completion callbacks used for skipped saves do not commit module settings. Core installation now rolls back its owned hooks on failure and distinguishes installation in progress from ready.

This is not full 8.5 support: depth/blur surfaces, widget customization, media/Now Bar and optional glyph optimizations remain reference-only. Those controls are not exposed as working in the 8.5 panel. See [the exact support matrix](../compatibility/IMPLEMENTATION.md).

Validation:

- Signed APK builds and verifies with v3 signing.
- Nine JVM suites pass: 117 checks, including 17 new adapter/save/rollback checks.
- Native declaration checks cover the selected editor roles in .50/.51 and the different Clock Style font/render/shadow/normalizer ABI.
- Repository/layout and generated inventory checks pass.
- The read-only Android binding probe passes against the P613's installed Dressroom .50 and Clock Style APKs. It resolves editor/save/date/tab roles and provider font/render/shadow/normalizer roles.
- The signed module is installed on the P613. Actual LSPosed/editor/Apply/lockscreen behavior still requires scope activation and the tablet test. Older Android/native results are not counted as fresh results for this build.

The working phone baseline remains 0.14.0; this build has not been installed on the phone.
