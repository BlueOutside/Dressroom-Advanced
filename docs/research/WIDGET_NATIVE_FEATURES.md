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
