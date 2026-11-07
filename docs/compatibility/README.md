# Compatibility audit: One UI 8.0 onward

Audited 2026-10-30. The original audit below describes the adaptation surface, not a declaration that another Samsung build works. Runtime adapter work has since started; see [implementation status](IMPLEMENTATION.md) for supported bindings and unfinished ports.

The target floor is One UI 8.0. One UI 7 is outside the requested scope. Features still need individual capability checks: a One UI version alone does not establish the presence of dynamic clocks, usable Object Capture, real-time blur or a particular Now Bar implementation.

File font and variable-axis renderer mappings are recorded in [font bindings](FONT_BINDINGS.md).

## Reference inputs

Package metadata read directly from the existing local APKs with `aapt dump badging`:

| Package | versionName | versionCode | APK min / target SDK |
| --- | --- | --- | --- |
| `com.samsung.android.app.dressroom` | 3.0.10.38 | 301038000 | 36 / 37 |
| `com.samsung.android.app.aodservice` | 8.9.66.1 | 896601000 | 35 / 37 |
| `com.android.systemui` | 17 | 37 | 37 / 37 |

These identify our reference inputs; they do not identify every build marketed under the same One UI label. The module's own `minSdk = 36` is an installation floor, not a claim of One UI 8.5 compatibility: `PlatformGate` refuses to install hooks unless the build reports API 36/37 and One UI 8.5 or 9.0. Keep install SDK and supported Samsung capabilities separate.

We need the exact One UI 8.5 APKs to establish target bindings. For full feature support, its matching SystemUI matters too: Dressroom and AOD alone cannot verify charging-card behavior, shade layering or unlock touch routing. Do not install these Samsung APKs over another OS build just to test their classes.

The subsequent [Dressroom 2.9.60.51 inspection](DRESSROOM_2.9.60.51.md) maps the first supplied 8.5 editor APK. Its save pipeline, UI/controller names and weather signature differ; renderer and SystemUI dependencies are tracked separately below.

The [AOD 8.9.32.95 inspection](AOD_8.9.32.95.md) maps the supplied 8.5 renderer/grid and part of its media/store paths. The [older 8.5 SystemUI inspection](SYSTEMUI_16_4006.md) maps interface 4006, legacy charging and the native input gate. These inputs are not established as one firmware-matched set. The [adapter design](ADAPTER_DESIGN.md) explains how package-specific shims will feed the shared feature engine.

## What the code audit found

The generated [literal lookup inventory](reflection-inventory.json) contains 221 occurrences across 70 production Java files: 114 class lookups, 90 declared-method lookups, 6 public-method lookups, 6 declared-field lookups and 5 resource lookups. These are occurrences, not unique classes or hooks. The count deliberately excludes names passed through constants, arrays, traversal and most `Reflect.call/field/set` chains. The real binding surface is larger.

Regenerate with Python 3.10+:

```text
python scripts/compatibility-inventory.py
python scripts/compatibility-inventory.py --check
```

Named Samsung classes and Java methods below are private implementation contracts unless demonstrated otherwise. Human-readable names are easier to match, but do not make them stable public APIs.

| Area | Current dependency and failure mode | Adaptation |
| --- | --- | --- |
| Editor entry | [DressroomModule](../../app/src/main/java/dev/denver/dressroomadvanced/DressroomModule.java): `vo.x.a(): boolean`, fields `Y`, `b: mf.o0`. This exact check gates the editor setup. | Move controller/panel/repository access into an editor binding. Resolve and validate all required members before hooking. Select by inspected artifact plus shape checks. |
| Apply and cancel | `dc.p.Y` selected by name/argument count; `androidx.work.impl.utils.c.invoke`, discriminator `a == 5`, owner `c: dc.p`. The callback's package name hides an obfuscated/synthetic role. | Bind the actual save request and successful completion path, including failure/cancel. This is a required editor capability, not a best-effort optional hook. Do not save on a generic coroutine completion or button click. |
| Native transactions | [NativeEdits](../../app/src/main/java/dev/denver/dressroomadvanced/editor/NativeEdits.java): `vo.g`, coroutine constructor/launch, controller/model fields and model `c(...)`. | Provide operations such as setWeather and begin/endEdit through the binding. Preserve the stock transaction and undo flow; avoid direct field writes as a fallback. |
| UI injection | [NativeClockPanel](../../app/src/main/java/dev/denver/dressroomadvanced/clock/NativeClockPanel.java), [PanelTabs](../../app/src/main/java/dev/denver/dressroomadvanced/editor/PanelTabs.java): exactly two stock tabs, child index 1, `binding_1`, Material `tabs.c`, methods `p/b/d/f/t`, tab field `d`; SESL controls/picker signatures. | Resolve the stock content by ownership/resource role, append a tagged module tab and retain its actual index. Add per-build SESL/tab access. If styling is unavailable, preserve stock controls and omit only the module panel rather than replacing Samsung's editor. |
| Preview restoration | Weather `mf.i0.a`, helper `oc.a.e`, main preview `vo.x.e`, renderer/repository field chains. | Bind model hydration separately from showing Advanced. Stock tabs and saved preview must use the same restored model. Test reopening without ever visiting Advanced. |
| Manual stretch | Editor/runtime `aod.qn1.X`, `AbsClockView.g0`, `setFontType`, `isStretchable`, fields `T0/D0/W0`; native font-copy layout. | A clock binding supplies font data, independent-height normalization, render update and the native height transition. Enable manual stretch only when the complete contract is available. Keep normal/non-dynamic clocks usable. |
| Media/AOD height | [AodStretchExtras](../../app/src/main/java/dev/denver/dressroomadvanced/clock/AodStretchExtras.java), [RuntimeClockHooks](../../app/src/main/java/dev/denver/dressroomadvanced/clock/RuntimeClockHooks.java): `K0`, controller `W0`, `d/h/i/j`, `t0`, axis string `HEGT`. | Map doze, media and height-animation state by semantics. Preserve only the height axis; continue native palette, weight, doze and position updates. Do not assume the same field letter or axis string on another provider. |
| Date/weather layout | [DateEffects](../../app/src/main/java/dev/denver/dressroomadvanced/clock/DateEffects.java): `D0`, named date/weather resources and native update methods. | Enumerate date/weather roles for each supported renderer. Missing role means unavailable override for that style. Restore native visibility/margins when disabling the override. |
| Clock blur and tone | [BlurEffects](../../app/src/main/java/dev/denver/dressroomadvanced/blur/BlurEffects.java), [DepthBlurHooks](../../app/src/main/java/dev/denver/dressroomadvanced/depth/DepthBlurHooks.java), [WallpaperBlurTone](../../app/src/main/java/dev/denver/dressroomadvanced/blur/WallpaperBlurTone.java): `aod.tx0/xb0/cq0/e70/n16/rv2/l49`, glyph state, SemBlurInfo. | Separate blur strategy, outline submission, text opacity and wallpaper-tone bindings. Require the correct ownership/clear semantics. Treat real-time blur availability as a capability, not a model-name/One UI flag. |
| Glyph optimizations | [GlyphFontCache](../../app/src/main/java/dev/denver/dressroomadvanced/clock/GlyphFontCache.java), `GlyphBlurReadiness`, `GlyphDrawReuse`: `h/i`, fields `b0/c0/d0`, outline readiness and inherited draw behavior. | Optional per-provider optimizations after verifying the native lifecycle. An unavailable cache optimization must leave native font rendering intact. Port cache invalidation with the hook; matching a signature alone does not prove cache safety. |
| Subject detection | [DepthEffects](../../app/src/main/java/dev/denver/dressroomadvanced/depth/DepthEffects.java), [MaskAlpha](../../app/src/main/java/dev/denver/dressroomadvanced/depth/MaskAlpha.java): ObjectCapture singleton/support/init/capture/release; result `isCaptured/getOutput/getObjects`, object bitmap and rectangle. | SubjectCapture binding translates results into module-owned full-image alpha plus coordinates. Check support and result conventions. Missing SDK disables detection only; preserve stored profiles/masks. Never load it from AOD's class loader as a substitute for Dressroom's. |
| Wallpaper capture/tracking | `CropImageView.onDraw/getDrawableInner`, ImageView matrix, `i7.g.e` with fields `d/l` for weather; [LiveSubject](../../app/src/main/java/dev/denver/dressroomadvanced/depth/LiveSubject.java). | WallpaperSource binding provides source identity, owned snapshot, source-to-window matrix and content-ready events. Keep unfiltered source separate from visual filter/frame transforms. A SurfaceView or changed crop implementation needs its own source path. |
| Runtime depth transform | `DepthEffects.screenSubjectPath` uses display metrics, preview scale/origin; editor live source uses full matrices. | Define one source-image coordinate contract and a per-display/window transform. Verify rotation, insets, zoom and AOD burn-in movement. Do not stretch a portrait baked mask into landscape and call it supported. |
| Widget editor | [EditorWidgets](../../app/src/main/java/dev/denver/dressroomadvanced/widget/EditorWidgets.java): `v8.y`, `pc.h`, `op.m`, `sd.q`, `rh.a`, `cg.n/cg.v`, `sp.y1` and many owner fields. | Widget-editor binding supplies toolbar, grid/model, native rotate/reflow, picker rules and save lifecycle. Keep capacity, physical scale, orientation and free placement separate. The native guide/binding must be identified by role per build. |
| Widget runtime | [WidgetEngine](../../app/src/main/java/dev/denver/dressroomadvanced/widget/WidgetEngine.java), [RuntimeWidgetHooks](../../app/src/main/java/dev/denver/dressroomadvanced/widget/RuntimeWidgetHooks.java): complication controllers/managers, alignment enums, native model widths/heights, host options, field `_scaleFactor`. | Map spans, pivots, eligibility and provider host sizes together. Verify native units/orientation encoding before transforming. Keep native widget IDs and reject incompatible grid reductions. |
| Widget compositor | [WidgetBlurInfo](../../app/src/main/java/dev/denver/dressroomadvanced/widget/WidgetBlurInfo.java), `WidgetDepthBlur`: exact nine-argument SemBlurInfo constructor, `mClipPath` and corner fields. | Validate constructor and field types before enabling widget blur clipping. Use an independent copy of submitted info; never modify a shared native object. Content and separate blur surfaces require both capabilities. |
| Full-screen media | [MediaFullscreenExtras](../../app/src/main/java/dev/denver/dressroomadvanced/media/MediaFullscreenExtras.java): `aod.fa2`, bitmap `G0`, controls `t0/o0`, `U/O`; `aod.n82` discriminator `o == 5`, fields `q/r`; shade layout/resource ordering. | Media binding exposes artwork, native controls, expansion/collapse progress, thumbnail and cleanup. Identify the actual animated owner and z-order in each build. If incomplete, keep the stock player. |
| Input routing | [MediaInteraction](../../app/src/main/java/dev/denver/dressroomadvanced/media/MediaInteraction.java): holder `c0/T0/J`, close `m`, `aod.di1.D` and native content-bound decisions. | Bind both the native hit-test and touch dispatch. Preserve gesture ownership from DOWN through CANCEL/UP; don't recreate the dead zone above the pill or swallow unlock input on unknown variants. |
| Charging/custom Now Bar | [NowBarExtras](../../app/src/main/java/dev/denver/dressroomadvanced/NowBarExtras.java), `ChargingVisuals`: named SystemUI classes, generated callback names, overlay state and Lottie properties. | Separate custom cards from charging retention. Map timeout completion independently from unplug/explicit dismissal and doze-session cleanup. Validate percentage and liquid-state rebinding. |

## Installation isolation needs work first

There are useful existing guards:

- Class-loader-aware provider discovery in `DeferredDiscovery` and `RuntimeClockHooks`.
- Exact controller shape checks before injecting the editor.
- Hook-handle rollback in `WidgetEngine`, `RuntimeWidgetHooks` and `GlyphFontCache`.
- Optional optimization/feature installers with native fallback.
- Module-owned geometry, snapshot and scheduling helpers with regression contracts.

But isolation is incomplete:

1. `DressroomModule.onPackageReady` runs widget installation, save hooks and clock binding in one outer sequence. `EditorWidgets.install` can throw before the save boundary or clock tab is installed. A renamed optional widget member can therefore block more than widgets.
2. `RuntimeClockHooks.installProvider` installs independent pieces, then requires `aod.qn1.X` before basic font/attach/effect observers. A renamed stretch normalizer can leave other runtime features uninstalled. Removing the loader from `installed` on failure does not unhook everything already installed.
3. `installCommitHook` installs activity lifecycle and request hooks before resolving the success callback. If completion resolution fails, earlier hooks can remain. The callback discriminator and request argument-count test are specific to this APK.
4. Several installers, including Now Bar and some blur/media paths, do not use a full hook-handle transaction. An exception after the first hook can leave a partial feature. A log saying "unavailable" is not proof that no behavior changed.
5. Optional deoptimization lookups can currently fail after behavioral hooks are active. Required target resolution, optional optimization resolution and actual installation should be distinct phases.

Use the rollback pattern that already works in `WidgetEngine`, extended to a per-feature install transaction. Pre-resolve required members; reserve an INSTALLING state to prevent class-load reentry; record handles; commit READY only after success; unhook on failure. Keep a process-wide shared hook separate from the rollback of one provider loader. A transient missing provider can retry on its load event; a structurally unsupported binding should not retry every frame.

The save boundary is special: if it is unknown, do not offer new module edits that appear savable. Existing committed runtime profiles can still render when their own bindings are valid.

## Proposed binding model

Keep rendering/state logic shared; move Samsung access behind small, typed role interfaces. These are proposed boundaries, not interfaces already present:

| Binding | Responsibilities |
| --- | --- |
| EditorBinding | Entry points, selected clock, draft repository, transactions, Apply success/failure and session reset |
| EditorUiBinding | Stock panel ownership, SESL controls, tab listeners and toolbar insertion |
| ClockRendererBinding | Clock identity, font/heights, animation/doze state, date/weather roles |
| ClockBlurBinding | Strategy, outline state, tone/color and native clear/update lifecycle |
| WallpaperSourceBinding | Source drawable identity/snapshot, crop/frame matrix and weather surface ownership |
| SubjectCaptureBinding | Detection lifecycle, object-list extraction and source-coordinate alpha result |
| WidgetBinding | Editor/runtime grid, spans, native rotate/reflow, owner and provider options |
| SurfaceBlurBinding | Native blur-info cloning, clip submission and capability checks |
| MediaBinding | Artwork/thumbnail/controls, expansion progress, completion, hit tests and shade owner |
| NowBarBinding | Custom item integration, charging timeout, active session and live visual updates |

Resolve once per actual class loader, not per animation frame. Do not replace `Reflect` with a global "find any method with these arguments" resolver. `Reflect` caches names and rejects some ambiguous matches, but does not establish the semantic role of a member.

Selection should combine:

1. Exact package identity/version and inspected artifact fingerprint for known bindings. SystemUI's versionCode `37` alone is plainly not a unique firmware identity.
2. Framework/build identity for Samsung hidden APIs such as SemBlurInfo.
3. Required superclass/interface, return/parameter types, expected field types and role ownership checks.
4. Runtime readiness and narrowly scoped behavioral validation before mutation.

Unknown builds can use demonstrably stable named interfaces for independent features. Obfuscated targets should remain off unless an inspected mapping and validation establish them. A list of guessed old/new class names is not adequate compatibility.

Report each feature as available, unsupported, temporarily waiting or failed, with one short reason. Separate requested saved state from currently effective state; unavailable support must not erase the user's saved preferences. Gate the relevant editor control with that reason.

## Saved data and form factors

- `SettingsScope.key` currently stores wallpaper ID + numeric clock type + numeric font. Verify those IDs on the target APK before reusing a profile. Preserve old records and add an explicit migration if the identity meaning changes; don't blanket-reset settings after an app update.
- `depthSourceVersion = 2` already distinguishes source-mask metadata. A future binding/schema version should describe actual coordinate/data changes, not just stamp the One UI version onto the same blob.
- Wallpaper identity comes from standard lock/system flags in `SettingsBridge`. Confirm Samsung's main/cover wallpaper selection and per-user ownership before adding display support.
- `RuntimeClockHooks.read` explicitly returns no profile for a nonzero display ID. `WidgetEngine.main` excludes cover and informative displays. These are deliberate current boundaries, not supported foldable-cover paths.
- A cover-screen port needs display-specific wallpaper/profile identity, crop, grid bounds and native controller ownership. Do not remove the display guard on its own.
- Main-display tablets and rotation still need verification: display metrics are used in saved-mask placement, horizontal phone metrics are used for custom widget cell sizing, and the editor panel assumes a phone-style layout.
- Capability checks should distinguish native real-time blur from no supported blur. A-series support cannot be established by globally spoofing a flagship/tablet flag. Do not add a CPU screenshot blur loop as an automatic fallback.
- For multi-user/work-profile cases, verify content-provider access and mask/profile separation within the actual Android user. The current package allowlist should not be widened merely to make an unsupported process work.

## What can stay shared

`MaskContour`, most alpha-mask math, scheduling/coalescing, immutable save snapshots, `WidgetLayout`, identity locks, contrast sampling and geometry contracts do not depend directly on a Dressroom obfuscated name. Retain these helpers and tests. Their bindings still have to supply the correct coordinate/ownership semantics.

Keep the fixed compact-clock regression invariant: `MediaBlurHost` hides the live `lock_clock_blur` surface through alpha while Samsung updates the attached drawable. Never restore a detached compact BackgroundBlurDrawable during collapse. The equivalent host needs identifying on each SystemUI, but the ownership rule stays.

## Implementation order

1. Add capability state and per-feature install transactions. Port the existing inspected APK's bindings without changing its behavior. Verify unknown targets leave native behavior intact and do not mutate preferences.
2. Extract Apply/session/model operations and UI binding first. Establish draft/commit/cancel correctness before exposing features on another editor.
3. Extract the provider core and manual/date/weather capabilities. Make stretch normalization optional for unrelated effects. Keep the depth-to-manual dependency only for genuinely stretchable styles.
4. Separate wallpaper-source/Object Capture, then blur/surface clipping. Verify the same source-to-display mapping in editor, lockscreen and AOD.
5. Port widget editor/runtime together. Then port full-screen media and Now Bar against the matching SystemUI. Preserve optional glyph optimizations only after their native lifecycle is inspected.
6. Publish a tested package/build/device matrix, with partial feature support stated explicitly. Do not advertise universal One UI 8.0+ support after a single 8.5 APK comparison.

## One UI 8.5 APK comparison checklist

For each provided APK, record package, versionName/versionCode, SHA-256, min/target SDK and intended device/firmware when known. Keep the APKs and decompiled dumps outside Git.

Inspect targeted classes one at a time in JADX:

1. **Dressroom entry and Apply:** selected-clock controller, stock panel/tab resources, toolbar, save request, success/failure callback and native weather transaction. Follow call sites to confirm roles; do not map by class-name similarity alone.
2. **Wallpaper:** CropImageView or replacement, drawable getter, image matrix, filters, frame geometry, weather SurfaceView owner. Inspect the bundled Object Capture API and result object coordinate conventions.
3. **Widget editor:** native horizontal/vertical behavior, orientation encoding, picker spans, saved grid model and provider transfer. Confirm the custom-grid handoff without first opening Advanced.
4. **AOD provider:** AbsClockView or replacement, font structures, independent normalization, `setMediaExpanded`, doze/height transition and date/weather renderers. Compare dynamic, analog, calendar and world clock paths.
5. **Compositor/media:** outline/blur strategy, SemBlurInfo handling, complication background owner, media holder, actual animation owner and completion cleanup. Matching SystemUI is needed to finish layer and touch checks.

Search class names/package scopes before reading source. Avoid APK-wide method/code searches; previous large JADX requests crashed the session. Compare semantic role + full signature + caller/field ownership, then populate a second binding and its capability manifest.

Required validation after a port:

| Case | What must hold |
| --- | --- |
| Cold editor start | Saved effects and weather load without visiting Advanced; opening controls does not mutate the draft |
| Apply/cancel/failure | Only successful Apply commits the frozen snapshot; cancellation and failed Apply keep prior committed state |
| Clock styles | Dynamic horizontal/vertical, non-dynamic, analog/calendar/world clock; change style/font and return |
| Depth | Fresh detection, full subject list, crop/zoom/filter/frame/weather changes, redraw and reopen |
| Runtime | Awake, doze/AOD, wake, digit change, media expanded for at least eight seconds then collapse |
| Widgets | Native/custom grids, rectangular spans, rotate, detach/snap/drag, depth/blur independence and provider restoration |
| Now Bar | Actual pill hitbox, unlock swipe above it, charging updates, card cycling, unplug and doze cleanup |
| Unsupported variants | Missing method/resource/SDK or ambiguous match leaves stock behavior; no partial install or saved-state deletion |
| Form factors | Rotation, tablet main display, fold posture and cover display only when separately implemented |
| Upgrade | Module replacement and Samsung package/provider reload restore the same committed profiles without reapplying |

Helper tests are necessary but do not establish compatibility with an APK whose bindings have not been inspected and exercised.
