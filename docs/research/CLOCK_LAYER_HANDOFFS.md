# Clock layer ownership during transitions

Reference AOD 8.9.66.1 and the matching One UI 9 SystemUI. The AOD methods
were read through JADX MCP. The control-flow checks use MCP smali because
JADX flattens two of the important branches in Java. SystemUI's plugin
callback and layout were checked from the local APK.

The follow-up patch records the source and destination during the native
handoff. It rejects outgoing blur submissions and artwork-mask paths.
An outgoing clear can still reach the host until the destination submits
blur. The fix uses the One UI 9 transition hook. Device verification is
pending.

## Inventory

| What can look like another clock | Identity and scope |
| --- | --- |
| Lockscreen clock | The `AbsClockView` bound to `FaceWidgetClockPage.clockView`. The page can bind a replacement before its first draw. |
| Seconds clock | Another real provider view, requested as type 8 for `DASHBOARD` or `DASHBOARD_AOD`. It has a separate font payload and its own time and blur state. |
| Transition source and target | Two real root views referenced by `FaceWidgetTransition`. The transition captures and animates their named child views. The snapshots used for matching are geometry records. |
| Main AOD clock | The AOD plugin's own clock container can inflate and bind a distinct `AbsClockView`. The FaceWidget page's `AOD` path requests a container through the SystemUI callback. |
| Nested clock | A module or world clock can have legitimate inner clock content. Counting all descendants with a clock base class can overcount the active root. |
| Offscreen clock | The provider's measurement instance, used for size and scale. It has its own identity even when type, text and axes match the visible clock. |
| Native blur silhouette | A drawable clipped to the glyph and weather outlines on a shared host. It can show a complete clock-shaped image with no second visible clock root. |
| Full-player artwork blur | The module can draw blurred album art through its own union of recorded clock outlines. This is a separate rendering pass from Samsung's blur drawable. |

A numeric clock type identifies a style. The same style can appear in two
separate views, or one view can switch between compact and stretched axes.

## Seconds view and page replacement

1. `FaceWidgetPage.updateContentsView` requests the new contents. The
   dashboard route calls `getClockViewForFullScreen`, which requests type
   8. The normal route requests the configured lockscreen clock.
2. In the animated branch, the page cancels any older transition, creates a
   `FaceWidgetTransition` from the current holder and exposes the new
   contents as `transitionDestination` while configuring the transition.
   The transition retains its own source and target references. The page
   binds the new view at the end of the same update.
   `bindClockView` clears the page's temporary destination field and
   publishes the replacement as `clockView`. Consequently, both the
   public-facing clock pointer and the blur controller can already point
   to the destination while the source still occupies the holder.
3. `attachTransitionTarget` captures the outgoing view's named child
   geometry, removes the destination from any previous parent, inserts it
   at holder index 0, and sets the source to `GONE`. A pre-draw stage
   then captures the destination's geometry. DEX confirms the source is
   set back to `VISIBLE` when a named override or the default transition
   requests a fade. The overlap is intentional. Seconds and the seconds
   colon have their own fade configuration.
4. The next stage builds animations from matching transition names on
   the two real hierarchies. Completion can wait for other participants
   through the transition synchronizer. `finishClockPageTransition`
   removes the source and ensures the target is attached and visible.
   The page completion callback clears the transition and dispatches
   `onClockPageTransitionEnded`. Cancellation can reach cleanup through
   an animator end or the immediate path. Hooks on the finish method
   should tolerate repeated cleanup.
5. If the animation is skipped, the base page directly removes the old
   children and attaches the new contents. When a transition is chosen
   but its source or observer is ineligible, it takes an immediate
   completion branch. The smali jumps past that branch when the
   pre-draw listener was registered successfully.

**Implication for the module.** `ClockBlurMotion` captures the
destination before bind and reapplies blur after target attachment. The
original hook inferred dashboard return from the previous state seen by its
`configureClockPageTransition` hook. An immediate replacement can
bypass that hook. A stale state in that cache can therefore miss or
misclassify a later return. Where the view-binding signature resolves,
the patch checks the actual source against the clocks bound in the
dashboard state. Other layouts keep the original inference.

## Who writes to the blur host

`AbsClockView` constructs one `ClockBlurManager` per instance. The
normal lockscreen strategy resolves `lock_clock_blur` from the clock's
root. SystemUI declares that tag on a full-screen sibling FrameLayout in
`super_notification_shade.xml`. It starts invisible. The editor and
fallback branches resolve their own tags. Resolving a host sets it
visible, including when the resolution comes from `clearBlur`.

The following operations can run on different schedules:

| Operation | Source of geometry or alpha |
| --- | --- |
| Submit or retry | The per-clock manager combines its own current text and weather paths. It can retry at 8 ms intervals up to five times. Its runnable retains the manager and arguments. |
| Reveal | The ordinary manager reveal lasts 300 ms. The dashboard return animator has a separate 500 ms path. The ordinary reveal can reset background alpha to zero while setting the host view to full alpha. |
| Tick transition | The strategy's own animator lasts 1000 ms on this APK, despite a 300 ms log message. Each update can resolve the shared host again and write background alpha, text color alpha and weather icon alpha. |
| View replacement | `ClockBlurController.observeStretchAnimation` unregisters the old observer, installs the new one and immediately applies the current blocker state to the bound clock. |
| Removal | Native detach cancels the two manager reveal animators and the strategy animator, clears paths and attempts to clear the host. A posted retry has its own lifecycle. Native hide and native detach are distinct events. |

The strategy checks whether glyph children are shown. During an intentional
source/destination fade, an outgoing view can be attached and shown again.
The original retry guard checked attachment, visibility, and whether
the manager belongs to that view. That rejects many stale callbacks but
does not prove the view still owns the shared host. An old manager could
pass while its source is temporarily visible. The blur host also has no
source identity in its resource tag. A host-level handoff must consider
late submits, alpha animation frames and clears. Blocking only the retry
will leave the other two writers in place.

The documented 0.13.11 compact blur flash had a specific confirmed cause:
the fullscreen-art layer restored an old native blur drawable before the
stretched submission arrived. That fix keeps the native drawable attached
and mutes the host while artwork is active. It is a useful precedent for
diagnosing a silhouette by its drawable identity. It does not establish
the cause of the more recent seconds-view artifact. See
[media return investigation](MEDIA_RETURN_INVESTIGATION.md).

## Media collapse

The ordinary expanded media player uses the bound clock's media and doze
inputs. The relevant page path changes axes on the bound instance.
`computeFontCollapseFraction` clamps the maximum of doze and the effective
media fraction. When the media animation is idle it uses the settled
expanded flag. While closing, it reverses the media animation's fraction.
The page can update blur scale independently of those axes. A compact
outline can therefore come from a valid earlier pose of the same view.
There is no type-8 construction in this fraction path.

The fullscreen artwork backdrop also collects paths from clocks in its
window to draw blurred album art within the glyph shapes.
`MediaFullscreenExtras` originally built that mask from every recorded
path whose view was shown and shared the window token. Two eligible roots
could contribute both shapes. The patch also checks the view's alpha and
whether it belongs to the outgoing clock. Mask ownership changes
invalidate the backdrop even when artwork geometry is stable. The
original rule is a candidate source of duplicates pending evidence from
a failing media cycle.

For a media artifact, distinguish the visible glyph draw, native host
drawable, and the module's artwork mask before adjusting the media
fraction or adding a new delay.

## AOD wake

SystemUI's `PluginFaceWidgetManager.getAODClockView(boolean)` delegates
to `PluginAOD.getAODClockContainer(boolean)`. Meanwhile inside AOD's own
plugin code (`AODUiController`), wake/sleep routes split between reusing the
lockscreen `FaceWidgetClockPage` (`ClockPageState.LOCK_AMBIENT`) and spawning a
separate `AODClockContainer` (`aod.AODClockContainer`):

- **`FaceWidgetViewModel.onStartedGoingToSleep()`** sets
  `mIsAODThemeClock = !faceWidgetClockRoamManager.shouldShowDualClock() && rv2.isAODThemeClockSelected(context)`.
- **`FaceWidgetViewModel.isLockAmbientEnabled()`** returns
  `cj1.isFullScreenAODSupported() && !mIsAODThemeClock`. When true,
  `AODPolicyFactory.makeUIPolicy` sets `AODUIPolicy.mNeedClockContainer = false`,
  and `FaceWidgetViewModel.updatePageState()` transitions `FaceWidgetClockPage`
  to `ClockPageState.LOCK_AMBIENT` (reusing the lockscreen `AbsClockView`).
- When `isLockAmbientEnabled()` is false (full-screen AOD unsupported or an AOD
  theme clock is active), `FaceWidgetClockPage` goes to `ClockPageState.AOD` and
  `AODClockViewModel` creates `AODClockContainer`, which inflates its own
  `AbsClockView` (`View.inflate(..., clockInfo.getLayoutId(), null)`), sets
  `setDozing(true)`, `setDozeAmount(1.0f)`, `setAntiScreenBurnIn(...)`,
  `updateFontAxesForTransition(1.0f, true)`, and scales down by `0.975f` on attach
  if smaller than max width/height. No `ClockBlurController` is attached to it.
- **Wake transition skips:** `AODUiController.m5573h` (`detachAODView`) passes
  `needTransition = false` to `FaceWidgetController.requestAODState(false, z2)`
  if AOD duration was `< 350` ms, policy width mode is `yl4.NARROW`, or clock
  type/group mismatches Lockscreen.
- **Morph blocker & weight hide:** `FaceWidgetClockPage.configureClockPageTransition`
  sets `clockBlurController.setFaceWidgetTransitioning(true)` (cleared in
  `onClockPageTransitionEnded()`), and `AodToLockFontWeightTransitionHelper` zeroes
  out source `fontTransitionNames` alpha (`setAlpha(0.0f)`) whenever AOD and
  lockscreen font weights differ (`qn1.resolveClockWeightAxis != qn1.m9976y`).
- **Rect-sync gating:** `FaceWidgetClockPageObserver` / `aod.c82` (`case 0`)
  skips `clockBlurController.refreshClockBlur()` whenever AOD, Dashboard,
  FullScreen, Quick Panel, Media Expand, screen-off, or screen on/off
  animation is active, or when `scaleX != 1.0f`.

The lockscreen blur controller considers fractional doze, an independent
position-doze state and proximity screen-on. Its state machine also has
a screen-animation blocker. Fast wake/unlock can skip part of the
ordinary font transition, and the page can take the non-animated
replacement path. A fix tied only to the end of the page AnimatorSet
would miss those paths. Use the attached holder, active page state and
actual host identity to distinguish an outgoing AOD clock from an
incoming lockscreen silhouette.

## Signatures checked on other providers

These are local DEX coordinates for research. Each sampled provider has
the attach anchor with indexed `addView` and visibility changes, a
matching finish anchor with removal/re-attachment, and a pre-draw stage
with both source relocation and target capture strings. The reference
One UI 9 behavior above was read in detail. Other entries establish
candidate handoff hooks, independent of clock axes or blur support.

| APK | Attach and finish owner | Pre-draw owner |
| --- | --- | --- |
| AOD 8.9.66.1 | `aod.qb2.i/h` | `aod.ds1` |
| AOD 8.9.36.23 | `aod.TS.i/h` | `aod.RS` |
| AOD 8.9.28.18 | `aod.HS.i/h` | `aod.FS` |
| Clock Style 8.9.35.60 | `aod.HL.i/h` | `aod.FL` |
| Clock Style 8.9.65.92 | `aod.ft1.i/h` | `aod.na1` |
| AOD 8.8.40.20 | `aod.yH.i/h` | `aod.wH` |

A binding can anchor attach and finish on their strings, then confirm they
share an owner, operate on the same source and target fields, and have
the expected calls to the holder. Match the pre-draw path against that
owner. Blur policy and AOD-container handoff still need independent
verification for each provider.

## What would distinguish the remaining bugs

- **Two foreground clocks.** Compare source and target root identity at
  attach, source fade and finish. A visible source during the planned
  fade is expected. A source still present after finish points to a
  different holder or lifecycle.
- **One clock plus an old silhouette.** Compare the owner and outline
  bounds of the last host submission with the destination's current
  layout. Record any subsequent clear or alpha write from a different
  manager. On media, also compare the backdrop's mask sources.
- **Correct shape with a late blur snap.** Check which blocker remains
  and whether the first valid destination submission happened before the
  reveal. This is a different failure from two drawing owners.

The ownership traces are static. The open issue is which of these routes
produces each remaining flash. No device trace was collected in this
pass.
