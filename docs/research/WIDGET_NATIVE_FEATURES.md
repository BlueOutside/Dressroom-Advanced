# Samsung widget functionality and 0.7.1 integration defects

Research resumed 2026-10-26 using the JADX MCP on Dressroom, then AOD. This is a code inventory, not a claim that all larger-device features work on this phone. No replacement APK was installed during this research pass. User confirmed independent widget depth works after SystemUI reload; layout and panel scrolling remain broken in installed 0.7.1.

Implementation follow-up: 0.7.4 fixes the integration defects identified below. Device testing confirms scrolling, native rotation, saved vertical restoration, and rendering on both lockscreen and AOD. The sections describing 0.7.1 are historical findings. Additional discovered gates are `rh.a.i` at editor binding, `cg.n.c` on resume, and the runtime large-device/portrait orientation path. Runtime restoration reads the actual native AODDataManager G flow, rather than treating the module's capacity-orientation mirror as the authoritative arrangement.

Native grid-size transition was traced to raw `aod.wz2.h`: it publishes the new Size and position type. Explicit repacking uses raw `aod.sy2.o(List,Set,boolean)` with existing holders from `sy2.f()` and container lookup `wz2.b(String)`. These are the raw APK names; JADX displays aliases m13049h/m11132o/m11124f/m13043b. That distinction was checked during actual on-phone reflow testing.

## Native features to retain or expose

| Feature | Native implementation / storage | Integration decision |
|---|---|---|
| Horizontal/vertical widget arrangement | `WidgetAlignType.Default/Vertical`; Dressroom `cg.v.a`, `op.j`, `pc.h.a`; AOD `onclickChangeWidgetAlign` | Use one native transaction. The stock rotation affordance and Advanced must operate on the same state, not independently write it. |
| Tap-to-rotate guide | `widget_align_assist_layout`, `sp.y1.a`, `op.g`; resource `widget_assist_message` | This is genuine Samsung UI, normally gated. It is not evidence of an extra third layout. Keep or deliberately suppress it when Advanced owns the controls; never leave two unsynchronized writers. |
| Arrangement persistence | `sd.q.b`, `pd.s` copy callback | Native widget record X encodes Default versus Left/vertical. Do not replace it with an arbitrary free-placement coordinate. |
| Separate arrangements per orientation | `ModuleInfo` X/Y and secondary X/Y, preserved by `WidgetFactoryKt.makeWidget` and `ComplicationWidgetRepository` | Preserve both coordinate sets and IDs. No duplicate module preference is needed for native ordering. |
| Reorder, fit, insertion and removal | Native grid rules, drop listeners, removal callback, real AppWidget hosts | Reuse. Enlarging capacity must retain fit validation and provider ownership. |
| Provider configuration | `setConfigActivityLauncher`; grid click callback passes `SharedWidgetInfo` | Keep the provider's native configuration flow; do not substitute a generic settings screen. |
| Supported widget formats | Provider tile target width/height, facade size table, picker eligibility, `getTargetDpSize` | Expose advertised variants only. More grid cells do not create a provider layout. A rectangular widget's span remains rectangular. |
| Larger-device grids | Size manager and `isBiggerSize` rule selection | Existing code supports 8×2/10×2 and 2×8/2×10 behind device/feature gates. Phone default is 4×1. Exposing this requires coherent scoped gates, not global tablet spoofing. |
| Clock-relative alignment | `updateClockGravity`, horizontal pivot rules; Dressroom widget/clock/notification assist repositories | Reuse native alignment where it matches the requested behavior. All-side docking and arbitrary detached coordinates are additional features. |
| Provider size updates | `sendAppWidgetOptions` sends `appWidgetSizes` and `semDisplayDensity` | Preserve provider communication when scaling. A view transform is not a new RemoteViews layout. |
| Wallpaper/clock appearance | `changeBlurType`, `updateClockColor`, `updateWidgetBackground`, dim background methods | Keep native wallpaper brightness/color decisions unless the user explicitly overrides them. These methods alone do not prove independent persistent user settings exist. |
| AOD transitions | `setDozing`, `updateDarkAmount`, `updateBlurByAODState` | Preserve native lifecycle and accessibility policy. Independent module depth/blur overrides must compose with it. |
| IDs, cache, removal, listening and restoration | Factory rebind, controller cache, host ID change/package removal handlers, native facade | Leave ownership with Samsung. Closing the selector saves its cache but is not the wallpaper Apply completion. |
| Scrollable native settings panels | `v8.j0.h=true` creates `v8.d0` (`NestedScrollView`) | Use for our long control list. The stock widget picker uses `h=false` because its RecyclerView scrolls itself. |

## Concrete defects in our current implementation

### 1. Wrong meaning of isGridType

`EditorWidgets.restoreModel` and the `sd.h.a` hook force `WidgetGridData.setGridType(true)`. This does NOT simply enable a larger complication grid.

`cg.o.a` chooses its source by this flag: true (or sub-grid mode) reads `ClockDataBindingHelper.b()`; false reads `IWidgetUIController.getModuleInfo()`. `td.f` saves/restores this flag in `SubProfileData`; the ordinary lockscreen `sd.q` constructs the default false model. Picker long-press also switches into the other grid-extras path under this flag.

Thus our override demonstrably selects a different native data path. Its contribution to each visible symptom still needs a corrected-build comparison. Preserve the native backend flag; do not conflate it with capacity or the provider's separate `isBiggerSize()` capability.

### 2. Competing orientation transactions

Native path verified in Java and smali:

1. Rotation guide calls `cg.v.a(target)`, which sets `widgetAlignChanging` and publishes WidgetData.
2. `op.j` observes the orientation and starts the native movement animation.
3. Its move callback (`androidx.work.impl.utils.c`, discriminator 22) calls `pc.h.a(target)` → `onclickChangeWidgetAlign`, updates clock/notification assist state, and recalculates widget placement.
4. `op.k` completion clears `widgetAlignChanging` and publishes the settled model.

Our update directly changes the model, directly calls `changeAlignType`, then publishes via list/position refresh, allowing the observer to run another transaction. It also unconditionally enables `isEditableAlign`, exposing the stock rotation guide. Updating only model dimensions does not repair this ordering problem.

### 3. Grid shape selects the coordinate set

AOD `ComplicationPositionTypeRule.d(Size)` returns Default for width >= height, Secondary otherwise. This is independent of the controller's WidgetAlignType. The current arbitrary Rows/Columns controls can contradict Direction. For example, Vertical with 4×2 still selects Default coordinates.

The native vertical pivot is (0,0). `GridItem` stores one width/height span plus two positions, not a rotated width/height per orientation. Preserve the provider shape and choose compatible dimensions/rules; do not transpose individual widget spans. Square-grid behavior needs an explicit policy because native selection treats it as Default.

`ComplicationWidgetsPositioningByGridSizeChangeRule` is an empty marker class implementing an empty interface. Its name alone does not establish complete migration behavior. Bounded JADX xrefs lead to rule registration (`wz2`), the rule holder (`da6`), and its provider/property accessor (`mf5`, `o00`); they do not yet establish a migration operation. Actual grid-size transition and invalid-coordinate recovery remain to be traced before relying on arbitrary capacity changes.

### 4. Non-scrolling panel

`WidgetPanel.show` explicitly sets `v8.j0.h=false`. JADX shows this constructs `v8.e0`, a plain FrameLayout. Our content is a LinearLayout, unlike the native picker RecyclerView, so nothing scrolls it. The native nested-scroll alternative is `v8.d0`. Anchor/expansion state also affects its gesture handling and needs an on-phone scroll check.

### 5. Incomplete metrics / notification contract

`sd.o` caches unit size, padding, radius and related values. Our hook updates only part of that contract. `cg.p.a` publishes position and updates both clock and notification assist state. Capacity, provider size, preview bounds and cached geometry must be refreshed consistently; the simple Java packing test does not validate these Android paths.

## Revised boundary for custom settings

Native state should remain authoritative for orientation, selected providers, spans, IDs, ordering and both coordinate sets. Module-only state is needed for arbitrary grid dimensions, separate physical scale, custom gap/docking/free position and independent subject depth/blur policy. Native orientation persistence does not imply an unsupported vertical phone layout will remain usable with the module disabled: device capability gates still apply.

Before another release: remove the backend-flag misuse, route orientation through one native transaction, keep dimensions compatible with the native position type, trace grid-size migration, use the scrolling host, then validate mixed 1×1/2×1 widgets through switching, picker close/reopen, Apply/cancel and SystemUI/AOD reload. Also verify any larger provider variant by its actual advertised size, not just a permissive picker.

## Evidence

Snapshots: `work/widget-expansion-audit` contains the named AOD rules, GridItem, size manager/controller/repository, Dressroom `cg.o`, `cg.v`, `op.m`, `sd.h`, `sd.o`, `sd.q`, `td.f` and panel classes. For failed Java decompilations, checked `work/dressroom-smali/smali_classes2/op/j.smali`, `op/k.smali`, `sp/y1.smali` and `work/dressroom-smali/smali/androidx/work/impl/utils/c.smali`. Resource strings and `widget_align_assist_layout.xml` confirm the native rotation guide.


## AOD tint and blur curve follow-up (2026-11-24)

Reference: AOD 8.9.66.1 inspected through JADX, including refreshBlur, its curve
helpers, BlurEffectManager, background decoration and RemoteViews content coloring.

| Finding | Change / status |
| --- | --- |
| AOD tint/profile recognition depended on optional lockscreen-preset helper discovery. A helper mismatch disabled the entire appearance binding. | Register the basic AOD surface/refresh binding even if the optional helper lookup fails. Native curves remain unchanged in that fallback. |
| Tint fades could write the old color while entering AOD, then wait for an animation frame that doze suspends. Repeated target submissions also wrote the unfinished color. | Settle the target tint immediately for AOD widget submissions and displays outside STATE_ON. Awake tint changes retain their fades. Live confirmation of the reported white tint remains required. |
| Neutral colors replaced the final curve after custom styling, so further curve adjustments would be lost. | Start from a neutral curve before applying adjustments. Add tone-curve bias and output-brightness controls, defaulting to zero. Preserve native input bounds, depth clips and blur backends. |
| AOD widget controls inherited a 9.0-only gate intended for clock glass. | Separate hardware fullscreen-AOD capability from the existing 9.0 clock/depth gate. Widgets can use their validated native contract on earlier supported builds; black AOD and system blur restrictions remain native. |
| High saturation plus a large radius mixes strongly amplified wallpaper colors. | Keep the existing saturation range; expose tone curve and brightness separately for readability rather than changing saved saturation values. |

Native checks covered 13 provider APKs: stock 8.0 AOD/Clock Style, 8.5 AOD
8.9.28.18, 8.9.32.95 and both 8.9.36.23 inputs, Clock Style 8.9.35.60,
9.0 AOD 8.9.57.1, 8.9.65.81 and 8.9.66.1, Clock Style 8.9.65.92, the
9.5 beta AOD input and the supplied modified beta input. All retain the same
named blur owner fields and float refresh/dark-amount methods. The 9.5 curve
helpers take an extra direction boolean; that path stays signature-resolved.
These are static binding checks, not visual certification of every firmware.

Android 16 and 17 framework bytecode agrees on six color-curve values:
saturation [-1,1], bias [-100,100], input min/max and output min/max [0,255].
The two new controls adjust bias and output brightness only. Additional framework
BGColorAdjustment and gradient APIs exist in the Android 17 input, but are not
exposed by this change because older backends differ.

Content review: Samsung applies widget coloring through its native host utility
and provider color-support bit. Picture Frame alone receives the separate AOD dim
layer in the reference decorator. No new content-color or RemoteViews replacement
hook is introduced here; a specific remaining content defect needs its own repro.

Local evidence is under the ignored .build/widget-tint-audit directory. Validation
includes 30 passing JVM suites (336 curve/tint-policy checks) and 67 passing
Android SemBlurInfo checks on the connected One UI 9.0 phone, covering native
copying, settings persistence and neutral curve composition. Those first-pass checks did not establish the visual AOD fix; the handoff and
user-confirmation follow-up below records the later findings.

### Confirmed native background handoff

A scoped SystemUI trace showed the AOD request and drawable both receiving
opaque black (`FF000000`, alpha 255), while the widget blur View was hidden.
`BriefWidgetManagerImpl.setSeamlessAOD(true)` calls
`setWidgetBlurBGVisibility(false)`: its `showDimBgView` hides `blurView` and
creates a separate flat `previewDimView`. Styling the hidden live surface cannot
change that substitute.

`AodWidgetBackground` now scopes the exception to this main-display seamless-AOD
manager transaction, when custom AOD blur or retained lockscreen colors are on.
It uses the native inverse handoff `removePreviewDimBG(false)` and current
`refreshBlur(darkAmount)`. Editor/cover editing calls to the same preview methods
remain native, as do media expansion, disabled blur, black AOD and system blur
restrictions. Settings updates replay the last native visibility request, so
turning the override off restores Samsung's substitute. The full named handoff
contract was checked against all 13 inputs above.

Further user testing distinguished opacity from the underlying curve: Dark at
100% works, while lowering opacity or selecting None exposes the pale native
luminance preset. Explicit AOD Dark/Light/None now start from linear luminance
before the user's curve/brightness adjustments, preserving the native saturation
unless Neutral is selected. Samsung default retains the original curve. Regular
widget and clock profiles keep their existing curve policy.

The clock's reported natural-blur flicker remains separate. Its styled curve was
stable in the focused submission trace; that does not prove native glyph/reveal
alpha continuity. The readiness follow-up is recorded in CLOCK_RENDERING.md; visual confirmation remains required.


Follow-up user confirmation: the apparent remaining white fill with None was
identified as the wallpaper blur itself, without a dark overlay. Saved None
(`aodWidgetBlurTint=3`) was verified; no additional forced darkening was added.
Preview6 passed 30 JVM suites, 91 native Samsung appearance checks and 61
standalone glyph/draw synchronization checks. The sync harness now explicitly
models its installed draw hook; a standalone app_process has no Xposed dispatcher.

The preview7 motion-policy experiment was removed after the focused player trace
identified the module fullscreen handoff instead. No temporary DRBlurState or
DRClockAudit trace code remains in the production tree.
