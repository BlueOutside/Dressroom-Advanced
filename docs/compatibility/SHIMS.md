# Shim layout

`compat/shared` contains the binding contracts used by both supported setups.
`compat/oneui85` maps the inspected 8.5 Clock Style and AOD methods; `compat/oneui9`
maps the reference AOD and Clock Style methods. All classes retain the existing Java package.

The clock source contract resolves its entire dependency list before hooks are
installed. Missing methods disable that optional synchronization, leaving native
behavior intact. Do not try nearby obfuscated names until their behavior is
checked in the matching APK.

In 8.5 Clock Style, `FaceWidgetClockPage.w()` measures the stretched height.
The equivalent 9.0 method is `y()`. Both consult the provider's font data, so
restoring only the visible clock leaves native measurement with old heights.

AOD 8.9.32.95 has an experimental 8.5 source/measurement and depth-blur shim.
It is declaration-checked, not device-validated. 9.0 Clock Style (ClockPack 8.9.65.92)
is supported via the One UI 9 Clock Style combo shim (`OneUi9ClockStyleFonts`,
the shared clock-source contract, `OneUi9ClockStyleStack`). The 0.21.0 fullscreen artwork port follows the native 8.5 fade and retains its
controls container. The tablet user confirms fullscreen display; 0.21.1 follows the native wallpaper
fade for closing. The tablet user confirms both closing and reopening during
the fade work correctly. Glyph-cache optimizations and
the framed song-change shader/lighting remain unported on 8.5.

## Resolving renamed members

Known names remain signature-checked fast paths. A fallback must find exactly one
matching member or class. Ambiguity disables the affected binding instead of
letting reflection order choose its behavior. Parameters match exactly; a null
parameter is an explicit wildcard and still requires a unique result.

ClockBinding identifies AOD versus Clock Style using the provider-owned settings
provider class in the clock's own loader. It derives the legacy/modern ABI from
the resolved height normalizer's parameters. Stored font data, the renderer and
shadow refresh are resolved by unique shapes, excluding their public API setters.
These checks replace the former L0/X/d0 family guesses. The known normalizer
mappings now live in ClockSourceBinding rather than four duplicate wrappers.

Clock source deoptimization selects a small superset of declared measurement and
font/view getter signatures. Those methods are only deoptimized, never hooked or
invoked through that broad match. This avoids an all-or-nothing dependency on
short names such as C/y/w while preserving native behavior.

Clock/font binding objects are shared by subclasses within a provider loader.
DEX names are deduplicated and cached with a fingerprint of the loader's current
DEX elements. Empty or failed scans are retried; adding a DEX invalidates the index.
Only renamed targets need the full scan. Signature matching does not prove
semantic equivalence, so keep known mappings and APK checks for operations with
otherwise indistinguishable signatures.

The current hardening was built and checked against the local 9.0 AOD, 8.5 Clock
Style and 8.5 AOD declarations. This is not an additional device compatibility
claim; a new APK still needs runtime verification.

## Depth source failures

ObjectCaptureBackend tries the provider-selected implementation, then the bundled
SRIB and ArcSoft implementations without treating their support-report flags as
proof that native initialization cannot work. Failed attempts are released and
retain their original errors. ArcSoft recovery keeps its established position before loading the alternate
native backend, under the SDK's class lock when present. Missing native libraries remain a real failure; no
SDK binaries are bundled by the module.

LiveSubject keeps its last notified transform separate from draw queries. Zoom
and pan now invalidate mask geometry, and a missing/replaced source cannot fall
back to its previous transform. A failed scan suspends the matte and clears stale
mask data without resetting user choices. Applying while the source is pending
cannot commit an old matte. Wallpaper source notifications are coalesced after
native setter batches, and the weather SurfaceView lifecycle also binds its hidden
crop view when the surface becomes ready.

The isolated Android depth-safety probe passed 24 checks on SM-S948B, including
zoom notification after draw queries, stable geometry after settling, protection
from caller matrix mutation and rejection of a replaced image. SRIB initialization
on the affected MediaTek device and weather-editor visuals remain user checks.

The inspected SRIB DVS class loads `/system/lib64/libdvs.camera.samsung.so` with
System.load and catches UnsatisfiedLinkError, logging it under the DVS tag. A
later unresolved DVS.initJni therefore does not distinguish a missing library,
a linker/namespace failure or an incompatible JNI implementation. Collect the
preceding DVS log before choosing a workaround; the Java fallback alone cannot
supply missing native code.

## 8.5 custom widget grid

OneUi85WidgetLayout resolves the editor's native rotation use case from its widget
preview field type, profile converter, clock-assist alignment getter and picker
eligibility method. Capacity and physical scale remain separate. Custom layouts
use the shared runtime size/grid hooks, while the earlier scale-only path remains
a fallback if full installation fails. The controls require both runtime and
editor bindings to be ready.

The native rotation updates WidgetData; module drafts carry rows/columns and are
restored before preview sizing. Reflow resolves the native List/Set/boolean
operation (n on AOD 8.9.36.23, rather than the 9.0 o hint). Oversized layouts are
rejected before applying. Picker tablet eligibility is confined to the call that
lists widgets for a custom multi-row lockscreen grid.

Large-screen editor windows retain Samsung's rotation entry point. Phones show
the module rotation action. Native rotation passes through the same draft and
capacity update hook. Structural hook installation has its own rollback group;
a failed optional binding cannot leave a partial custom-grid installation.

The 2.9.60.42 editor binding probe resolved rotation, profile restoration, picker
and clock-assist declarations. A combined runtime probe was killed before its
provider checks completed; runtime rotation/save/reopen remains a device check.

The 1.0.3 diagnostic also restores native requested-font IDs for known storage
layouts. FontData can describe a temporary transition font, so using it as the
identity for all providers could read a different profile. Unknown layouts retain
the FontData fallback. This is a compatibility correction, not confirmation of
the separately reported One UI 9 visual regression.

## Profile reads after wallpaper replacement

The 1.0.4 diagnostic makes saved-profile validation local to the profile being
read. A mismatched depthImageKey clears that returned profile's matte; it no
longer calls invalidateDepth and resets other clocks or the active detector.
After a source reset, a stamped profile matching the current still image is
valid again. Unstamped legacy profiles and mismatched images remain rejected.
The widget profile loader uses the same policy. Confirmed source-change events
still clear the previous wallpaper's subject and require depth to be re-enabled.

The 1.0.5 diagnostic also reapplies the selected clock's depth after widget edits,
both immediately and on the next animation frame. The latter re-resolves the
renderer and checks the editor session/controller, so a native reflow cannot leave
the clock's newly rebuilt owners without the saved subject. No settings are
staged by this refresh. The reports do not yet establish whether the failure is
preview-only or persists after Apply; both require a visual check.

The widget refresh explicitly rebuilds Samsung's separate native blur too: an
unchanged matte does not prove its native blur owner survived reflow. Rotation
success queues the same refresh on both editor adapters. Scale/position-only
slider updates do not request this heavier rebuild unless the depth policy also
changes.

## One UI 9 foldable profiles

The foldable port uses the same NativeContract resolver as the shared shims.
Inspected Dressroom 3.0.01.20 and AOD 8.9.57.1 names are additional checked
hints, not a device/version dispatch table. ClockTransitionBinding and
ClockGlassBinding cache native state owners and checked scalar layouts.
FoldProfileBinding resolves the native load/save, policy, row-mapping and
widget-deletion transaction before migration hooks install. Missing or
ambiguous declarations disable the affected capability.

A missing modern weather-capability getter does not imply a legacy editor:
the helper's ClockView bind signature selects the ABI. Weather support can
still be queried through its native manager wrapper. Widget preview fields
resolve by role and type. The optional rotation guide is never found by an
unconstrained no-argument scan, which also matches unrelated editor bindings.

### Apply and unlock clipping

Apply rejects an enabled depth profile while its subject is pending or missing. The draft stays in the editor for retry instead of saving an empty matte. Clock, widget and Extras commits report failures separately, so a clock upload failure cannot silently skip the grid commit. Widget profiles are stamped with the saved wallpaper source just like clock profiles.

Depth's temporary canvas layer uses the incoming clip rather than the view's measured rectangle. Samsung can keep a one-row widget host while children extend into a custom grid during unlock; constraining that layer to the host cuts off the lower rows, even though their separate blur backgrounds remain visible.

A temporary source-read error suspends the editor matte without resetting profile choices. The runtime widget draw watcher invalidates cached content only when the host geometry or subject changes, so unlock translations cannot carry a stationary wallpaper cut along with the moving widgets. It does not install a content outline or rebuild the native blur on every frame.

### Shared detector lifetime and final wallpaper completion

ArcSoft and SRIB keep a static initialized flag and class lock, despite exposing multiple detector instances. The module registers successful init calls as live owners and only runs native release after the last owner leaves. Capture rechecks init while holding that same SDK lock. This also repairs native callers initialized before the hooks loaded. No JNI success is fabricated.

On the 9.0 reference editor, the clock component can finish at 3/4 while wallpaper saving is still pending. Module settings now commit at the mediator's final component completion, when the new wallpaper ID is available. Apply readiness is checked before starting the component batch; a failed preparation must not drop a component callback and strand the save spinner. The final-completion binding is discovered from DEX method bodies, not obfuscated identifiers. It matches the save-start/validation and component-count/final-completion string pairs, then resolves the fields read before the final-completion branch by type and declaring role. Start and completion must share the same component list. Missing or ambiguous matches abort editor hook installation; there is no name-based or early-component fallback.

Both the 8.5 and 9.0 save pipelines hook only the discovered batch entry and final completion. It no longer depends on the clock component's request name, synthetic callback class, discriminator or callback field names. The SDK lifecycle hooks use Samsung's public SDK class/method names and its named static lock, which are separate from obfuscated editor symbols.

On 8.5, validation is a separate helper and the final callback uses synthetic accessors for private mediator fields. The resolver follows those exact typed calls; it does not depend on their names. Read-only binding checks cover Dressroom 2.9.60.42, 2.9.60.50 and the 9.0 reference editor.

The DEX reader runs once during binding and keeps only matched method/field metadata. It handles instruction widths and switch/array payloads rather than searching arbitrary bytes for string IDs. Local checks resolve the reference APK and a D8-built fixture with different class, method and field names; the fixture also checks 3/4 versus final completion, editor identity and ambiguous matches. `tests/integration/SaveSignatureProbe.java` checks the installed editor contract without changing its settings.

### 8.5 custom grid and rotation

The editor's native grid-size use case receives an already-oriented provider size, then transposes it again for editable vertical layouts. The 8.5 port now resolves that use case by its exact WidgetGridData/WidgetAlignType/boolean/boolean contract, restricted to the main widget repository. It reapplies the selected grid dimensions to the active model before cell metrics run. The new binding has no obfuscated-name hint.

Native 8.5 position-rectangle updates can also swap the model width and height. Custom grids now take their physical bounds from WidgetSizeData after that update, matching the 9.0 correction. Existing valid native widget coordinates are retained. Saved overlaps, unplaced cells or out-of-bounds coordinates request Samsung's native reflow.

Runtime refresh compares custom-grid enablement, columns, rows, orientation and scale instead of scale alone. Legacy AOD/Clock Style custom layouts use the saved orientation even when a phone's native capability gate reports Default. Stock layouts keep Samsung's normal refresh path.

Phone rotation guides are identified by the public assist-guide resource IDs and their shared parent structure. The guide is hidden on layout updates, without disabling rotation eligibility or changing the widget picker. Tablet and fold-main roles come from Samsung's DeviceType; these keep their native control. The guide observer is removed on editor reset. A read-only probe checks both affected Dressroom 2.9.60.42 and the tablet's editor bindings and guide restoration; full AOD-combination rendering still requires an affected-device test.

At 100% scale, 8.5 custom grids also refresh each widget host viewport and its rounded background after restoring the grid. The previous neutral-scale shortcut could retain the stock host size. This forced refresh is restricted to legacy providers with a custom layout; 9.0 and stock layouts keep their existing behavior.

### Startup signatures on older 8.5 builds

Dressroom 2.9.50.90 has both a preview helper and an editor controller with the same create/open method shapes. Controller discovery also requires the weak Activity reference. When the weather use-case class moves, its native EnableWeatherInfoUseCase tag and boolean method identify it instead of a process-wide boolean-setter scan.

AOD 8.9.28.18 has multiple unrelated List-to-List utilities and two blur-clip helpers. Unknown legacy height bindings follow the call from AbsClockView.isStretchable(List); the blur fallback follows the native strategy's render method. These call-site checks run during binding, not rendering, and add no obfuscated-name aliases. Known provider bindings retain their existing paths.

The supplied startup log was searched only for module failures and their adjacent stack traces. A read-only startup probe covers the supplied editor/provider pair; binding success does not replace an on-device visual test of every feature.

On AOD 8.9.36.23, the native grid controller can leave widgetAlignType null until an explicit alignment is supplied. The 8.5 runtime restore previously called getClass() on that value and aborted before grid refresh, widget depth policy and placement. Saved custom alignment is now resolved from the checked public WidgetAlignType declaration and applied through Samsung's changeAlignType method. Stock layouts and the 9.0 runtime path are unchanged. The affected-device LSPosed export confirms the null dereference; the visual result still needs verification there.

### Dependency and call-site signatures

Widget toolbar discovery verifies the native menu-item array and Activity/View constructor. Its renderer must read the array and rebuild native child views. Metrics/position discovery checks full argument lists and excludes sublauncher. The obsolete rotation-host check was unused and is removed; OneUi85WidgetLayout still validates the real rotation use case.

The older 8.5 size-spec getter is only used for legacy providers. The 9.0 static sizing fallback is unchanged. Media deoptimization follows actual callers of setMediaExpanded rather than selecting a generic observer. Artwork discovery includes its constructor. Unknown stack key builders must read all three native identity components; count callbacks require their zero-argument result operation. DEX scans happen during binding, never in a draw hook.

The supplied older pair and three reference combinations are recorded in [the compatibility matrix](Dressroom-2.9.50.90-AOD-8.9.28.18.md). The remaining older-editor rotation failure is an absent API and stays unavailable rather than guessing a replacement.
