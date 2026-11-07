# Adapter implementation history: 0.15-0.16 alpha

This document records the initial adapter rollout. Subsequent 0.17-0.19 feature ports and device results are tracked in [current validation](../VALIDATION.md); the initial feature boundaries below are historical.

P613 update (2026-10-30): the user confirmed the Advanced controls work on 0.15.2, saving applies the tested settings to the lockscreen, and reopening Dressroom restores them. This validates the tested tablet core editor/save/lockscreen path; it does not establish unported-feature or AOD-phone compatibility.

This is an initial compatibility build, not complete One UI 8.5 support. JVM checks and APK declaration checks cannot certify Samsung's editor, compositor or Apply behavior on a device.

## Implemented boundaries

| Role | Working reference | Inspected 8.5 inputs |
| --- | --- | --- |
| Editor owner and model | Dressroom 3.0.10.38 | Dressroom 2.9.60.51; P613 port's 2.9.60.50 |
| Native tab/control UI | Top-level tab/listener classes and reference row tags | Nested TabLayout classes; native sub-tab styling and 8.5 label/switch/slider row tags (0.15.1) |
| Weather/date transactions | Native coroutine and idle coordinator | Synchronous weather operation, separate date-order coroutine and idle use case |
| Weather eligibility | ClockTypeData property | Native ClockManager query; the model property is absent (0.15.2) |
| Position/size operations | Native position repository | 8.5 repository argument order and method mapping |
| Manual stretch/font rendering | Reference AOD ABI | AOD 8.9.32.95 and Clock Style 8.9.35.60 core bindings |
| Shadows and date identity | Reference font identity | 8.5 font identity and native shadow update |
| Module settings after Apply | Existing reference completion hook | Snapshot attached to the normal save coroutine; suspend/resume and failure are separate outcomes |
| Widget toolbar, scale and placement | Existing reference implementation | 0.16.0 initial 8.5 port; native rotation retained; device behavior pending |
| Depth, blur surfaces, custom widget capacity, media and Now Bar | Existing reference implementations | Not ported/enabled in this build |

The 8.5 Advanced clock panel uses the existing feature engine and native resources. Depth/blur controls are unavailable there until the surface adapters are implemented. The bottom toolbar and scale/placement widget panel have an initial 8.5 port in 0.16.0; device validation is tracked separately. Saved settings are retained; unsupported features must not be advertised as working.

`EditorBinding` selects the inspected editor version and validates ownership, field types and method signatures before installing the panel hooks. `NativeTabs` handles the two SESL/Material ABIs. `NativeEdits` delegates to each editor's own operations. `ClockBinding` resolves stored font, font identity, render/shadow updates and the scoped height normalizer once per provider class.

Core editor/provider hooks use `HookTransaction` for reverse-order rollback. Installation has a separate in-progress guard; a failed attempt is not marked ready. Shared effect installers still own their independent hook lifetimes. This is not yet a transaction covering every feature installer in the project.

## Apply semantics

The 8.5 completion listener is also used by skipped saves, so it is not a commit signal. The module freezes its clock and widget settings on the normal `H$e` coroutine's initial invocation, retains them across suspension, commits only after a normal Unit return, and discards them on an exception. Each coroutine has its own identity-keyed snapshot; another request cannot consume it.

Native completion is not proof of durable Samsung provider storage. The inspected native profile writer can return without exposing a write result. Apply/reopen and provider readback still need target-device validation. No extra commit is attached to a generic callback or to a no-op profile store.

## P613 findings and limits

The connected SM-P613 reports One UI `80500`, Android API 36 and a custom ROM port. Its installed packages are:

| Artifact | Version | SHA-256 |
| --- | --- | --- |
| Dressroom | 2.9.60.50 / 296050000 | `fa385c0aca137b8597ff25f3f95ad33d631b99ff4faeee9f253979154dd2b185` |
| Clock Style (`com.samsung.android.app.clockpack`) | 8.9.35.60 / 893560100 | `5aabeee49653ef62a675c7fc4b79e1b67ba5f2b00f21518c2cc86b26098e4725` |

The .50 and .51 declarations match for the checked editor controller, model/date coroutine, weather use case, binding helper, save coroutine and tab classes. Their DEX files differ; this is not a claim that the apps are byte-identical. The tablet's .50 behavior needs an on-device check.

Clock Style has the same named AbsClockView base but a distinct ABI: stored font `L0`, font ID `w0`, render `X(ClockFontData)`, shadow `U(ShadowStyle)` and height balancing `aod.QT.g(List)`. The latter is called from setFontType and limits two segment heights to a 1.6 ratio; manual mode bypasses it only within that clock's font/render call. The APK retains blur code, but that alone does not establish working compositor blur on this device.

The module scope list includes Clock Style. It does not add an AOD feature to the tablet. Runtime hooks still retain the existing primary-display exclusion; fold/cover/secondary-display support is not certified.

Shell root was initially unavailable; the user subsequently enabled it and started LSPosed setup. A parasitic manager need not have a standalone package. The read-only Android binding probe then passed against the installed .50 editor and Clock Style APKs. Version 0.15.0 was installed on this tablet; scope activation/reboot and hook execution, editor visuals and Apply/reopen remain pending validation. Root/framework setup is a separate prerequisite, not something this build changes.

Clock Style's blur manager is `aod.wn`, with `aod.Tq` as its strategy, `aod.Vg` options and wrapper `aod.an0.b` pointing to the clock. The manager checks device feature flag `aod.uJ0.v0`, blur-enabled state and per-clock unsupported state before rendering. The clock stores this manager in `a1`; the reference's `h1` lookup cannot be reused. Both window/canvas tags remain in the strategy. This is a researched path, not an enabled blur adapter; compositor support and the capability policy still need validation.

## Remaining ports

- 8.5 native blur and depth surface binding, including Clock Style's device capability gates.
- Wallpaper source/capture/crop lifecycle on the target build.
- Custom widget capacity, picker variants and independent widget effects. Group dragging has an initial 0.16.1 port. The initial toolbar/scale/placement port retains native packing and orientation.
- Preserve the tablet's confirmed native widget orientation control; do not duplicate it when supported. Foldable/display capabilities still need target validation.
- Legacy SystemUI charging retention and Now Bar input; AOD's different media holder and animation paths.
- Native font/media/doze transitions and optional glyph optimizations for each provider.
- One UI 8.0 APK inspection and device-specific capabilities.

Do not enable reference-only features by changing a version constant. Inspect the owning artifact, add a binding and validate its full save/render lifecycle.

## Initial widget port: 0.16.0

`WidgetEditorBinding` validates the native toolbar model, preview ownership, grid getter and exact size/position repository signatures. The menu uses Samsung’s menu model; its panel uses the 8.5 native rows and scrolling host. Widgets are saved from the ordinary Apply coroutine even if the clock Advanced tab was never opened.

`WidgetEngine.installScale` multiplies the fresh native cell scale after initialization. It leaves native rows, columns, rotation and tablet capabilities alone. Refresh initializes scale before rebuilding native rules, then updates provider size options for currently attached host IDs using Samsung’s own notification method. Unbound draft IDs are never allocated by this path. Editor measurement caches are refreshed separately so selection bounds use the new cell dimensions.

Clock Style runtime ownership is separate from AOD-specific depth/media ownership. Placement uses the existing native widget root and the clock provider; no replacement AppWidget hosts or IDs are created. The P613 binding probe resolves these declarations, but native Apply/cancel, actual provider resizing, orientation and lockscreen placement still require visual checks. This does not certify AOD-phone widget support.

### 0.16.1 follow-up

The user confirmed the 0.16.0 toolbar and slider placement work, but scale changed spacing without enlarging existing contents. Each host and background owns another size manager and caches its geometry. The follow-up refreshes these existing owners, computes the native logical host size before visual zoom, and applies zoom to the host transform. Provider options describe the logical viewport rather than the enlarged display size. No widget IDs are recreated.

Free/docked group dragging now uses the preview root’s inverse transform on 8.5, instead of the reference editor’s obfuscated preview-scale field. Native default placement keeps its touch handling. Native taps are forwarded when movement stays below touch slop; cancellation restores the starting draft. These changes still need on-device visual and interaction checks.

### 0.16.2 host traversal and live dragging

The native widget callback can supply an outer content container rather than an AppWidgetHostView. Discovery now walks its descendants. Factory results also associate saved widget IDs with temporary preview hosts; host IDs are never rewritten by this mapping. An Android regression probe covers nested discovery, identity aliases and unchanged ownership.

Samsung’s 8.5 binding adapter `lb.c.r` and reference 9.0 adapter `rp.e.b` both use their final boolean to bypass the translation animator. The drag hook enables that path only for the current widget preview during a group drag, then flushes pending bindings. Normal editor updates keep native animation.

Both provider paths refresh existing host geometry. Clock Style uses its native size-spec calculation; reference 9.0 uses the native `v99.a` calculation and `setResizeScale`, which its host consumes again during onMeasure. Direct transform writes alone cannot persist on that host. Default-scale hosts are left alone unless restoring a previous custom zoom. Runtime checks on the 9.0 phone remain separate from APK/probe validation.

## Extras and force blur: 0.17.0 initial port

The 8.5 toolbar panel now exposes Extras. A separate snapshot joins the normal save coroutine; completion writes the frozen extras independently of widget/clock profiles. Draft changes stay in the editor until Apply. Charging retention uses the inspected P613 SecPowerUI completion/removal path while leaving native display-state restoration running; reattached held cards retain current percentage/liquid state. The custom-card bridge uses the native NowBarItem API.

Full-screen artwork and the AOD stretch-transition control remain reference-only and are hidden in the 8.5 panel. They require additional provider/media adapters.

The experimental widget/Now Bar force-blur path was removed in 0.17.2 at the user's request after transparent/white-pill failures on the tablet ROM. Old force flags are ignored by ExtrasPreferences. Established clock blur, native widget blur and media blur behavior remain in their existing paths. See [the archived findings](../research/FORCED_BLUR_EXPERIMENT.md).

## Initial Clock Style depth port: 0.18.0

The P613 editor now validates Samsung Object Capture's named factory/capture/result contracts before exposing depth. Both ArcSoft native libraries checked by the SDK exist on the tablet. CropImageView.getDrawableInner is private in this editor; the shared source reader now resolves declared methods through the class hierarchy. Weather preview l3.h.u(Consumer) supplies crop field d and WeatherSurfaceView field j, unlike the reference i7.g.e()/l handoff.

Clock Style uses render-node clipping for all clock styles, including cached horizontal/vertical digital clocks. The existing manual-stretch ownership, source fingerprint, full/detail matte merge, crop tracking and Apply snapshot are reused. Editor and runtime widget adapters now read the selected clock profile and support Follow clock / On / Off depth policy. The separate 8.5 AOD provider remains excluded from depth until its blur-surface renderer is adapted. Device-wide blur forcing stays removed.

The SDK/editor/provider binding probe, all 117 JVM checks and 13 Android suites (361 checks) pass on the P613. These counts overlap; they are not additive coverage. Actual SDK detection is observed in Dressroom logs. The user confirmed clock depth survives Apply/reopen and stays aligned on the lockscreen. Widgets work in the editor but fail to clip on the lockscreen.

### Clock Style widget lifecycle follow-up: 0.18.1

The native controller attaches its outer container before later layout and content callbacks finish constructing the widgets. The adapter now follows those callbacks plus wake/display-state changes, coalesces nested updates and caches the saved clock profile between dark-amount frames. The clip watcher also restores outlines replaced by native code without a geometry change. All added callback signatures resolve against the P613 APK. On installed 0.18.1, runtime logs confirm the saved mask on ComplicationContainer, and ADB captures show landscape lockscreen widgets clipped before and after screen-off/on. User confirmation and portrait/rotation coverage remain pending. This does not enable depth for the separate 8.5 AOD provider.

### Portrait/landscape identity: 0.18.2

Clock and widget records now include layout orientation in their storage keys. EditorBinding reads the ScreenSize already used by native position calculations; Activity configuration is the fallback, not the dimensions of a clock or widget view. Drafts, panel identity and frozen Apply snapshots retain that orientation. Native rotation gets a pre-change depth checkpoint, and runtime owners/sizing caches reload their profile on layout changes. Now Bar extras stay global.

Migration claims an old unsuffixed record for only one orientation and leaves the original intact for rollback. An unconfigured second layout gets native/default settings, never a mask baked for the first layout. Both original and tablet editor APKs declare the configuration callback; this does not certify foldable display transitions. Tests and P613 runtime profile switching pass; independent editor Apply/reopen and other devices remain pending.

In 0.18.3, refine that migration to inherit the original preferences and source-space subject for the second layout while removing its baked screen mask. Preserve existing configured profiles. Recheck ScreenSize at Apply and refresh after configuration changes even when Samsung does not recreate the clock. Date-preview identity also carries the preview orientation. Widget depth can use a wallpaper-wide orientation policy (portrait, landscape or both) without sharing the masks, scale or placement; independent per-orientation policy remains available. User validation of portrait-first saves and the new scope UI is pending.
