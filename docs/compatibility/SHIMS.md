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
