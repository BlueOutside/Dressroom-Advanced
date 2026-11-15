# APK feature and bug audit

11 November 2026, module 1.1.32. This audit covers the locally supplied Samsung
APKs and the feature paths in the current checkout. The offline results establish
declarations, call sites, and matching native log anchors. Device reports and
prior binding probes are identified separately. A match says nothing by itself
about initialization, renderer output, or the behavior of an installed APK pair.

## APKs checked

The three reference binaries are the original S948B AOD, Dressroom and SystemUI
in `outputs/analysis-inputs`. The scanner reads every DEX in each included base
APK. Exact binary duplicates are counted once. Different binaries with the same
version label retain separate rows.

| Package | Version | SHA-256 prefix | Role in this audit |
| --- | --- | --- | --- |
| Dressroom | 3.0.10.38 | f493af985544 | One UI 9 editor reference |
| Dressroom | 3.0.10.38 | 2fb281188aec | Additional supplied binary |
| Dressroom | 2.9.60.50 | fa385c0aca13 | P613 port |
| Dressroom | 2.9.60.51 | 0fa224ac1b81 | Supplied 8.5 editor |
| Dressroom | 2.9.60.42 | 7a396000be89 | Supplied older editor |
| Dressroom | 2.9.50.90 | 030f4386d53e | Missing Advanced report |
| Dressroom | 2.8.10.24 | 42f54519324b | One UI 7 comparison |
| AOD | 8.9.66.1 | 1d1b07db9855 | One UI 9 provider reference |
| AOD | 8.9.65.81 | 077da1810d9c | Alternate provider |
| AOD | 8.9.36.23 | 4dce599dcb0d | Supplied 8.5 provider |
| AOD | 8.9.36.23 | ea8ad221a10c | Second binary with the same version |
| AOD | 8.9.32.95 | 9afd419f2cb3 | Supplied 8.5 provider |
| AOD | 8.9.28.18 | 02ae3a7b32ce | Older provider |
| AOD | 8.8.40.20 | a27b923c04b5 | One UI 7 comparison |
| Clock Style | 8.9.65.92 | 55cb7b857f86 | One UI 9 ClockPack comparison |
| Clock Style | 8.9.35.60 | 5aabeee49653 | P613 port |
| SystemUI | 17 | eebcad118579 | One UI 9 reference |
| SystemUI | 16 | a847ee77e09c | P613 port |
| SystemUI | 16 | e7cdb3588c75 | Separate supplied 8.5 APK |
| SystemUI | 17.99.02 | 0e253fad7ce9 | Supplied alternative, firmware pairing unverified |
| Samsung Live Wallpaper | 2.8.78.0 | e782e155bfa6 | Live depth transform dependency |

Fold8 Dressroom 3.0.01.20 and AOD 8.9.57.1 have earlier device evidence, but
their binaries were outside the available corpus for this pass. The OPPO clock
APKs were excluded because none of the module's Samsung scopes load them.
Package versions identify artifacts. A filename such as `v80` does not establish
the firmware running on a device.

## Findings

### One UI 9 fix pass

This pass uses the reference AOD 8.9.66.1 and Dressroom 3.0.10.38.
Platform capability limits and missing proprietary libraries stay in the
compatibility inventory. The rows here describe module-owned behavior.

| Feature | Finding | Change or check |
| --- | --- | --- |
| AOD widget and Now Bar blur | The prior minute-tick hook can resend a drawable with an empty render position. Samsung's compositor excludes that region even while the view and its drawable remain visible. | On wake, retry after a frame and rebuild only the still-visible native drawable whose position is still empty. Check the intermittent dropout before and after wake. |
| Clock editor live refresh | A frame callback could retain a detached clock or clear the work item for a newer refresh when native blur reentered the editor. | Consume only the callback's own queue entry. Recheck live shadow, date/weather and clock changes. |
| Widget docking | A clock can still be attached in a different window, and a clock child can be locally visible while an ancestor is hidden. | Require the cached clock to belong to the widget's root and prefer the genuinely shown clock during discovery. |
| Fullscreen media and widget reveal | Detaching the 9.0 media backdrop could leave its view overrides and pre-draw listener installed on the former window. | Release the artwork, shortcut, and occlusion state on detach and bind the observer to the new attachment. |
| Update dialog | A focus retry for an earlier editor can outlive the active editor request. | Only show the dialog for the current editor and reuse a dialog that is already showing. |
| Apply and editor settings | Reference validation, launch and completion are separate. The save button is disabled only after validation, and native completion checks the count against the same component list. | Keep the existing save boundaries. Next verify failed component completion and the depth/font profiles pending user feedback. |

JADX aliases added on the reference Dressroom APK:

| DEX owner and member | Alias |
| --- | --- |
| `p8.a` | `ComponentMediator` |
| `p8.a.i()` | `beginWallpaperSave` |
| `p8.a.H` | `saveComponents` |
| `p8.a.I` | `completedSaveCount` |
| `p8.a.e` | `applyButton` |
| `j6.d.w()` | `finishWallpaperSaveWhenReady` |

The reference AOD stack's `NowBarCardStackView.z()` is now named
`updateCardVisibilityByExpandState` in JADX.

The [Dressroom alias list](../research/DRESSROOM_9_ALIASES.md) and
[AOD alias list](../research/AOD_9_ALIASES.md) include the original DEX names
for matching the same roles across versions.

### Fixes to carry into the APK comparison

The [One UI 9 source findings](../research/ONE_UI_9_BUG_FINDINGS.md) track the
1.1.42 changes, their original evidence and the remaining visual checks.

| ID | Build | Fix and verification target | Other APKs |
| --- | --- | --- | --- |
| FIX-01 | 1.1.39 | An empty depth scan tries the other Samsung backend. Recheck the same image and a case with one unavailable backend. | Shared logic. Check alternate Dressroom SDK capture and release contracts. |
| FIX-02 | 1.1.39 | A variable typeface change refreshes native digit metrics. Recheck the HGHT cutoff in the editor. | Check that the other clock providers recalculate metrics in the same text setter. |
| FIX-03 | 1.1.40 | Wake repair recreates a live window-blur drawable with a missing compositor position. | One UI 9 only for now. Check each SystemUI and framework pairing before extending it. |
| FIX-04 | 1.1.40 | Only the current clock refresh can consume its scheduled editor callback. | Shared logic. Recheck date, weather, and shadow preview on each editor. |
| FIX-05 | 1.1.40 | Widget docking ignores a cached clock in another window. | Shared logic. Check window changes and native clock ownership on AOD and Clock Style. |
| FIX-06 | 1.1.40 | Fullscreen player detach releases the layer, opacity overrides, and occlusion. | Compare the 8.5 artwork host's separate detach lifecycle. |
| FIX-07 | 1.1.40 | Pending update dialogs belong to the currently active editor. | Shared module UI. No APK-specific binding. |
| FIX-08 | 1.1.41 | Widget drag cancels on a second finger and never finishes against a replacement preview. Recheck taps, cancelled drags, and changing layout mid-drag. | The gesture handling is shared. Compare the 8.5 preview root lookup, native tap replay, and direct position binding. |
| FIX-09 | 1.1.41 | The fullscreen artwork touch override closes the player only for a single-finger tap within touch slop. Recheck a swipe that returns to its starting point, multi-touch, and taps on the title. | The reference 9.0 hook targets the old artwork touch root. Verify that root on alternate 9.0 builds and check whether 8.5 has an equivalent interception. |
| FIX-10 | 1.1.41 | Rechecking the same release preserves the existing update prompt's unexpired authorization. Check the same asset twice and authorize the first token, then check that a changed asset replaces it. | Shared module updater. No APK-specific binding. |
| FIX-11 | 1.1.42 | Rear Now Bar cards track opacity and mode, settle after an interrupted handoff, and defer count changes until the stack is idle. | Compare the native stack alpha helper, mode fan-out and busy flags on each provider. |
| FIX-12 | 1.1.42 | Wake recovery can restore missing and zero-alpha widget or front Now Bar backgrounds from a current request. | One UI 9 only. Verify native owner and alpha on each SystemUI/provider pair. Explicit clears and the original dropout during doze remain separate. |
| FIX-13 | 1.1.42 | Layered wallpaper scans prefer original pixels when they fit the live preview transform. | Check the original drawable getter, base-layer index and crop alignment on other Dressroom APKs. |
| FIX-14 | 1.1.43 | Restored the 1.1.41 clock blur lifecycle after comparing both APKs. Motion tracking ends when the transition settles, glyph updates wait for the clock draw, and superseded retries are dropped. | Check idle blur in Dressroom and AOD, and verify opacity recovers after cancelling an unlock swipe. |
| FIX-15 | 1.1.44 | The clock clip uses the host origin selected by Samsung for the current render. Native blur submits immediately and retains its retries while the module waits for a fresh glyph draw. Unlock blur keeps the host's original opacity through a clear and rebuild. | Recheck idle editor and lockscreen blur, repeated partial unlock swipes, and blur/depth alignment after wake. |
| FIX-16 | 1.1.45 | Clock blur transition frames and completion use the color painted by Samsung's cached glyphs. The generic TextView color can remain white while the clock glyphs have a custom color. | Check idle custom-color blur after dragging the clock in Dressroom and after a cancelled lockscreen swipe. |
| FIX-17 | 1.1.46 | The reported white clock persisted with the FIX-16 hook. The phone trace retained the beige palette while Samsung's successful native blur used its inverse-brightness custom-color curve. Use wallpaper metrics for native custom-color blur too. During an unlock drag, retain tracked container surfaces and apply opacity after the native listeners update their views. | Compare awake idle tint and a held partial swipe. Verify cancellation restores the backgrounds and real unlock clears them. |
| FIX-18 | 1.1.47 | The awake clock had customized blur with a zero saturation adjustment. Preserve the native color curve at zero. Nonzero settings offset native saturation within the framework's signed range. | Compare the awake lockscreen with AOD on the same wallpaper and confirm both ends of the saturation slider. |
| FIX-19 | 1.1.48 | Samsung chooses clock blur color metrics and default text opacity. User-set radius, saturation adjustment, tint and text visibility still apply. The wallpaper-sampling override introduced a gold tint and was removed. | Verify Samsung-default appearance with no text visibility override, then test the individual controls. |
| FIX-20 | 1.1.49 | Dressroom's placed widget grid requests Samsung's live widget background on devices with native window blur. Its flat editor background had no surface for the sliders to update. Clock and widget adjustments refresh their own surfaces once per frame. | Verify a widget radius drag on the reference device, native fallback when blur is disabled, and editor reopening without Apply. |
| FIX-21 | 1.1.50 | The editor's widget window blur rendered black. Supported previews use Samsung's canvas blur with the widget's rounded clip. The rear Now Bar opacity binding now checks its static helper's log anchor directly. | Compare the editor while dragging widget radius, and confirm rear card opacity on the reference stack. |
| FIX-22 | 1.1.51 | Samsung's preview canvas blur records the wallpaper alone, so low radius does not reveal a blurred copy of widgets. The editor clock can use the same capture with its native glyph clip. Controls check the framework and observed blur backends. | Check low-radius widgets, clock interaction, and slider availability on the affected S24+. |
| FIX-23 | 1.1.52 | Clock blur in Extras reuses the current editor glyph clip for radius and color changes. It falls back to a native glyph rebuild when the host is relaying out. The Effects strength control is hidden when the Extras radius takes precedence. | Drag Extras radius, switch the override off, and verify the previous per-clock strength is restored. |

Dressroom `p8.a.e(MotionEvent)` is named `dispatchTouchToPreviewComponents`
in JADX. The activity calls it before Android's normal touch dispatch.

### Earlier findings and compatibility notes

| ID | State | Finding and affected path | Next verification or change |
| --- | --- | --- | --- |
| EDIT-01 | Report resolved earlier | Advanced was missing on Dressroom 2.9.50.90 with AOD 8.9.28.18. The user later confirmed Advanced appeared. The old editor lacks both modern controller names, but the previous structural probe resolved the panel. | Recheck alternate editor builds with the complete controller, tab, and successful Apply contracts. A missing literal name is expected when the structural fallback resolves. |
| EDIT-02 | Confirmed ABI gap | The 8.5 save start and completion are `P3/f.E0` and `P3/f$a.g`. The 9.0 reference uses `p8/a.i` and `j6/d.w`. The two-string 9.0 start marker does not match the older save-start body. | Preserve the separate start/failure paths. Test apply, skip, exception and retry with reopen and lockscreen readback. |
| EDIT-03 | Reported, current status unknown | Scroll endpoint clipping, spacing, and keyboard overlap were seen during editor changes. Several specific layout defects were fixed and retested on the tablet. | Test the longest Clock pages and the custom message editor at large text size and in landscape. |
| EDIT-04 | Known preview mismatch | The main wallpaper preview can omit weather-only rendering while the clock editor and runtime reflect the saved choice. This preview was explicitly deferred. | Keep this separate from a failed save. Compare the active editor and runtime before reopening the main-preview issue. |
| CLOCK-01 | Historical report, regression target | Clock placement was shifted after Apply with no date or weather on 9.0 Clock Style. That case was reported fixed. A separate widget-present displacement report followed with an unknown grid setup. | On the exact ClockPack build compare no widgets, native grid and custom grid while keeping clock/date/weather settings constant. |
| CLOCK-02 | Confirmed family boundary | The One UI 7 AOD comparison still has a fixed typeface setter and a seconds transition. It lacks the checked modern height-source API and media expansion setter. | Keep the One UI 7 package outside support. A font setter alone cannot provide native per-digit stretching. |
| BLUR-01 | Confirmed adapter gap | Clock Style declares clock blur, glyph outline and retry roles, but `ClockBlurBinding.resolve` explicitly rejects Clock Style for the AOD path. | Map the Clock Style path and compositor capability independently before enabling it. |
| BLUR-02 | Confirmed version difference | `setCoverWidgetBlurState(ZF)` is present on the three scanned 8.9.65/66 providers. It is absent on the scanned 8.9.28–36 providers. The underlying widget host and refresh method exist on both sides. | Use the 8.5 lifecycle for older providers. Do not backport by calling the 9.0 setter blindly. |
| BLUR-03 | Reported, open | Seconds-view return still snaps blur. The native temporary clock is not the settled clock managed by the normal controller. The duplicate from the native source/destination crossfade is parked at the user's request. | If resumed, trace the temporary drawing owner and final handoff separately. |
| BLUR-04 | User-confirmed fixed | Widget and Now Bar backgrounds failed to return after notifications were dismissed. 1.1.32 restored them on the reference phone. | Retest partial/cancelled shade, wake into shade, media active and actual unlock. Clock blur is blocked while the shade hides the clock. |
| BLUR-05 | Historical regressions | Live swipe previously left backgrounds transparent or revealed a second outline. Later builds improved opacity and cancellation recovery. | Sweep partial up-swipes, cancel after full alpha loss, unlock, screen-off during drag, and shade interception. The option remains API 37 only. |
| DEPTH-01 | Reported, open | Subject initialization can fail on MediaTek and other hardware. All six modern editor binaries have the factory and SRIB JNI class. None bundles the exact `libdvs.camera.samsung.so` filename. | Check actual device library availability and the initialized backend. Static SDK presence cannot prove that ArcSoft or SRIB works. |
| DEPTH-02 | Awaiting retest | Subject extraction can miss obvious subjects or fine branches on some devices. The same image can succeed on the reference phone. 1.1.39 tries an alternate Samsung detector after an empty result. | Retry the same wallpaper on the affected setups. |
| DEPTH-03 | Historical regressions | Wallpaper replacement, crop/zoom, unsupported wallpaper types and Apply have previously left stale masks or editor-only depth. One zoom report was confirmed fixed after the signature migration. | Cover static replacement, pan/zoom, filter/frame/weather effect, video/gyro rejection, failed detection and Apply/reopen. |
| DEPTH-04 | Confirmed scope boundary | Live wallpaper tracking has matching engine and canvas draw anchors in 2.8.78.0 only within this corpus. A black AOD with wallpaper disabled was fixed on the reference device. | Verify alternate wallpaper binaries and scale/translation across screen-off/on. Missing renderer scope uses the saved mapping. |
| WIDGET-01 | Confirmed ABI gap | The previous isolated binding probe found `ClockAssistData.getWidgetAlignTypeValue()` missing on Dressroom 2.9.50.90. Custom rotation/restore is unavailable through that adapter. | Keep stock rotation controls on large screens. Add an older semantic binding only after mapping its placement contract. |
| WIDGET-02 | Reported, open | On 8.5 AOD, saved custom grids have rendered empty or mispositioned containers even when the editor preview was correct. The 4×4 weather/health report had widget depth off. The screenshots did not identify which of the two same-version 8.9.36.23 binaries was installed. | Capture the exact binary, AppWidget host bounds, provider size options and native content clip after Apply. |
| WIDGET-03 | Confirmed signature drift | The drag adapter shape is present under `rp/e.b` in 9.0, `lb/c.r` in 2.9.60.x and `jb/c.r` in 2.9.50.90. A nested class also declares the same unqualified shape on each older editor. | Match staticness and the native call site before adding a fallback. Matching argument types alone is insufficient. |
| WIDGET-04 | Historical regression target | Scaling once enlarged spacing without changing contents. A later build fixed live scaling and dragging on the reference phone and P613. | Retest 1×1 and rectangular items, provider updates, grid resize, rotate, interrupted drag and free/docked placements. |
| FOLD-01 | Coverage gap | Profile bundle, mapping and write shapes are widespread across the modern providers. The migration adapter targets a known Fold8 profile layout. | Use the actual Fold8 binaries to test fold during Apply, same-orientation screen switches and artwork return. Do not infer support from common profile classes. |
| MEDIA-01 | User-confirmed fixed | The temperature had a different text handoff from the clock digits, causing a dark fade and snap on media close. | Recheck both artwork modes after changes to the media host lifecycle. |
| MEDIA-02 | Regression rolled back | Splitting wallpaper blur around the shrinking cover in 1.1.30 broke fullscreen closing. 1.1.31 removed that change. | Preserve artwork ownership and existing transition geometry. Retest normal and fullscreen close before any new media change. |
| MEDIA-03 | Reported, exact build unknown | An 8.5 AOD user saw settled fullscreen artwork blurred. 8.5 artwork and transition signatures exist, but the source/render choice still needs device evidence. | Identify the exact APK and native image/blur layers after the opening animation finishes. |
| MEDIA-04 | Reported visual polish | Fullscreen song changes can look worse than the framed player. Widget disappearance on player opening is also abrupt. | Record stock and module transitions using the same album sequence. Compare artwork and widget timing separately. |
| BAR-01 | Confirmed ABI gap | Stock SystemUI 17 has `ChargingAnimationUI.removeNowBarView`. Both SystemUI 16 files use `SecPowerUI.removeNowBarView`. The supplied 17.99.02 APK matches neither checked entry nor the custom-card bridge. | Establish its firmware pairing before treating it as a usable SystemUI target. Probe charging timeout and custom-card initialization independently. |
| BAR-02 | Historical regression target | Charging retention, percentage text and liquid fill were previously fixed on the reference phone. | Repeat charger attach/detach, percent updates, card cycling and return from shade. |
| BAR-03 | Historical regression target | Rear cards could overlap, disappear or flash in certain orders. Native default stack repair was confirmed on the phone. Custom count and further device combinations still need coverage. | Cycle mixed stock/custom cards with 3–8 visible, including removal during swipe. |
| BAR-04 | Historical regression target | The collapsed pill used to consume touches well above the visible bar. The user confirmed that normal swiping and direct pill dragging worked after the fix. | Verify touch ownership after expansion, gesture cancel, changes in stack size and folded layouts. |
| UPDATE-01 | Coverage gap | The updater is module code. APK declaration scans cannot establish GitHub availability, DNS behavior or Android installer handoff. | Test cached/latest checks, offline recovery, signer/version rejection and installer cancellation. |

## Feature coverage checklist

This ties the feature list to the evidence. Rows with no native role are still
part of the audit because their behavior depends on module state or Android.

| Feature | Native role or module path checked | Open checks and findings |
| --- | --- | --- |
| Advanced clock tab, Widgets and Extras toolbar | Editor controller, tab class and both toolbar/preview families | EDIT-01, EDIT-03 |
| Preview, Apply, cancel, reset and restart persistence | Save start/failure/completion and preview ownership | EDIT-02, EDIT-04, DEPTH-03 |
| Size and X/Y position | Clock renderer, profile ownership, widget-aware movable area | CLOCK-01, FOLD-01 |
| Manual per-digit height | Font data setter, height source, stretch predicate and HEGT anchor | CLOCK-02. Verify native font and each supported clock style. |
| AOD keep-stretched option | Dozing, doze amount and media expanded setters | API/adapter gate. Check AOD with and without wallpaper. |
| Shadow and text visibility | Shadow setter, native text opacity and reveal | Test native/imported fonts, gradient and clock swaps. |
| Font file import/reset | Fixed typeface signature, file picker and private copy | Historical device confirmation. Retest failed import and cancel. |
| Variable axes | Font renderer plus module fvar parser | Test different axes, static fonts, ranges and reselecting the same file. |
| Date and date placement | Native order setter and module DateEffects | Check disabled date, alternate calendar and clock style. |
| Weather only, weather restore and eligibility | Editor use-case anchor, 9.0 eligibility getter, renderer weather setters | EDIT-02, EDIT-04. 8.5 uses an alternate eligibility route. |
| Clock blur enable/strength/tint/saturation | Native host, retry, glyph outlines, color/opacity layer | BLUR-01, BLUR-03, BLUR-05 |
| Widget blur radius/tint/saturation | Widget host/refresh and final SemBlurInfo submission | BLUR-02, BLUR-04 |
| Now Bar blur radius/tint/saturation | Card/stack roles and module surface classification | BLUR-04. Compare ordinary cards and native Brief rendering. |
| Live unlock blur | SystemUI gesture/alpha bridge and provider motion | BLUR-05, BAR-04 |
| Notification shade blur recovery | Controller blocker transition and container submission snapshot | BLUR-04 |
| Subject detect/automatic fallback | Object Capture factory, init, release and result shapes | DEPTH-01, DEPTH-02 |
| Wallpaper crop, zoom, replacement and effects | Crop drawable/draw and module source policy | DEPTH-03 |
| Clock subject depth | Clock provider, drawing path and persisted mask ownership | BLUR-01, DEPTH-03. Compare analog, world/calendar and dynamic clocks. |
| Full-screen AOD depth tracking | Wallpaper engine/bitmap draw anchors and compositor mapping | DEPTH-04 |
| Widget independent depth and orientation | Widget host, geometry and orientation-specific module profile | WIDGET-02, FOLD-01 |
| Widget scaling and larger custom grid | Provider size manager, editor adapter and host | WIDGET-02, WIDGET-04 |
| Widget rotation and guide | Editor grid ABI and native rotation ownership | WIDGET-01, WIDGET-03 |
| Widget edge attachment and free dragging | Editor drag adapter, runtime movable area and placement | CLOCK-01, WIDGET-04 |
| Portrait/landscape layout and screen profiles | Module orientation state and native profile read/write | FOLD-01 |
| Fold cover/inner wallpaper, widget and Extras separation | Screen identity and fold profile transaction | FOLD-01. Prior Fold8 device evidence only. |
| Charging card retention and live fill | SystemUI legacy/reference removal and charger view | BAR-01, BAR-02 |
| Custom Now Bar message | Plugin bridge and native card item | BAR-01, EDIT-03 |
| Stack repair, count and swipe/tap area | Stack class, Now Bar identity and SystemUI touch routing | BAR-03, BAR-04 |
| Framed/fullscreen artwork, changing songs and depth return | Media page/model, coordinator shape, observer, mask and lighting | MEDIA-01 through MEDIA-04 |
| Update prompt, verification and installer | Module updater and Android package installer | UPDATE-01 |

## Signature examples

| Role | One UI 9 reference | Other matches |
| --- | --- | --- |
| Editor successful save | `j6/d.w()V` | `P3/f$a.g()V` in the four scanned 2.9.x builds |
| Clock blur controller clear call | `ClockBlurController.b(ec0, AbsClockView)` calls `removeBlur()` | Same operation with `jl`, `ll`, `fc0`, `bh` and `e10` state types |
| Dashboard blur reveal | `cq0.l()V` | `Zr.n`, `bs.n`, `wn.n`, `dq0.l`, `ge0.l` |
| Fullscreen artwork coordinator shape | `ag4.b(FrameLayout, MediaLightingView, MediaMaskingFrameLayout, ImageView, ImageView)` | `Ir0.b`, `Nr0.b`, `Ur0.b`, `Dh0.b`, `bg4.b`, `wq3.a` |
| Clock/widget movable area | `gs0.b(Integer, Rect, SizeF, String): Rect` | `hs0.b` and `ag0.b` on the other 8.9.65 providers |
| Unlock drag | `ActionMoveHandler.handleMotionEvent(MotionEvent): boolean` | Exact declaration also present on both SystemUI 16 binaries. The module gate remains API 37. |

For a repeatable scan, use [the audit runner](../../scripts/apk-audit/run.ps1).
It writes APK identity, every applicable match/miss and the module's literal
hook IDs into separate CSVs. The rules use raw DEX names, complete prototypes,
selected calls and exact anchors. A raw-name rule deliberately measures drift.
A shape-only match identifies a candidate for inspection.
