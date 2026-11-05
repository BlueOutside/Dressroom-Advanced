# Test suites

Tests are outside the production source tree and are not packaged in the module.

| Directory | Purpose | Command |
| --- | --- | --- |
| `jvm/` | Pure Java state, identity, reflection and scheduling contracts | `./test-local.ps1 -Suite Jvm` |
| `android/` | Android geometry, rendering, lifecycle and performance contracts | `./test-local.ps1 -Suite Android -Adb <adb-path>` |
| `native-glyph/` | Actual Samsung glyph cache and Android outline checks | `./test-native.ps1 -Adb <adb-path>` |
| `integration/` | Older manual provider/APK contracts; requires their documented fixtures | Not part of the default suites |
| `benchmarks/` | Isolated lookup/allocation experiments | Not an animation FPS test |

The Android suite runs three shared JVM cases plus platform checks: 18 suites, 473 checks. The eleven JVM suites total 152 checks; counts overlap and must not be added as distinct coverage. `suites.json` is the authoritative list. The adapter suite checks wrong-type name collisions, exact signatures, partial hook rollback and per-request save ownership. OrientationProfileProbe uses detached contexts and in-memory preferences to check layout isolation, migration without reusing the wrong baked mask, orientation retained through a late Apply and shared widget-depth policy without overwriting layout-specific scale. It does not rotate the device or write real profiles. EditorScrollProbe uses real nested Android views to verify scrolling through a viewport clipped by its parent, including the final row and repeated sheet resizing.

`integration/VersionBindingProbe.java` resolves the editor/provider bindings against APK paths on Android without installing hooks. It takes `editor.apk provider.apk editorVersionCode`. Resolution is an ABI check, not a test of preview rendering or native Apply durability.

Use `-SkipBuild` after a successful module build. Generated classes, DEX, test APKs and result files live under `.build/tests/`. The Android runner uses the connected rooted test phone's system UID, starts no SystemUI service, and changes no saved profiles. The native glyph runner temporarily installs its dedicated instrumentation package and removes it in `finally`; it does not restart SystemUI or install the module.

Manual integration tests may write temporary mask blobs or require specific Samsung APK revisions. Review their source before running them. They are retained as reference contracts, not counted as freshly validated merely because they compile.

`WidgetSizingProbe` is a focused Android integration check for nested native content containers and saved-to-preview host identity mapping. Run it through the same app_process harness as `VersionBindingProbe`; it creates detached framework views and does not allocate widget IDs or change the editor. `VersionBindingProbe` also accepts the reference editor/provider APK paths with version code 301038000 to validate the 9.0 widget bindings without installing those APKs.

The abandoned force-blur experiment, including its captured-info probe, is archived in [the research record](../docs/research/FORCED_BLUR_EXPERIMENT.md). `blur-backend/` remains a separate manual framework comparison Activity, not part of the module or default regression suites.

For 0.18.0, the legacy branch of VersionBindingProbe also validates Object Capture factory/capture/result signatures and the private crop drawable accessor. It does not initialize the detector or prove visual depth. Actual detection results must come from the editor process, which owns the preview image and native SDK context.

DeferredRefreshTest checks that cancellation cannot let an old callback consume a new lifecycle's refresh, including force coalescing, reentrancy and failures. RootLayoutWatchProbe checks listener cleanup on real Android views when a root is rebound, replaced or released.

SongRevealProbe loads the reference provider's installed shader asset and checks hardware-rendered pixels across the reveal. It also covers the posted image-bind handoff, software fallback, native image ownership, zoom geometry and the widget observer's 255f opacity input. It changes no settings and installs no hooks. It requires the reference AOD package/resources; it does not certify another provider's asset or a live music transition.

`integration/ProviderBindingProbe` accepts a provider APK path without a matching editor. It checks clock source/measurement, native blur owner traversal and widget declarations for the inspected providers. It never installs the APK or creates a Samsung service; it does not establish rendering or plugin compatibility.

The 8.5 media return cases in MediaReturnProbe check suspension until native completion, the final frame, close/reopen cancellation and missing/failed animator lookup. They use real Android animators with queued frame callbacks; the binding probes separately resolve each provider's native observer and animator fields.
