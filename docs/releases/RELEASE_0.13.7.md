# Retire the compact media blur outline — 0.13.7 alpha

**Subsequent result: failed visual verification.** The user reports the compact clock still flashes after a long-open player return. 0.13.8 also fails. The investigation is tracked in MEDIA_RETURN_INVESTIGATION.md; the pending wording below records the original test status only.

VersionCode 100. Installed and SystemUI reloaded; runtime build 0.13.7 logged under PID 17326.

## Problem and evidence

The user reports that the compact clock flashes over the stretched clock when closing the full player after leaving it open for several seconds. Immediate open/close often avoids it. The original 0.13.3 recording reproduces the small clock at `work/media0133-correct-frames/frame-46.png`.

0.13.4's layout deferral and 0.13.5's after-child-draw synchronization did not resolve the user's long-open case. 0.13.6 added a temporary bounded native-path trace. In the captured long-open return, the trace showed a valid stretched path at 20:32:50.513, approximately 823 ms after return began: one visible LOCK_SCREEN owner, alpha/transition alpha both 1, with stretched digit views 1102 px high. It did not show a newly submitted compact path in that trace window. This supports stale expanded-player blur content being exposed during native blur reactivation, rather than proving an extra compact TextView is deliberately drawn.

Targeted native inspection also confirms that ClockBlurController hides/reactivates blur for state/animation reasons, that text glyph paths can be cached until onDraw, and that the blur manager retries unready glyph paths. The exact native reactivation callback responsible for every variant is not claimed proven.

## Change

At media return, for profiles with saved manual heights and manual stretch or depth enabled, call the native blur strategy's clear operation (`AbsClockView.h1.p.c`) to discard the expanded player's cached compositor outline. This uses Samsung's own `semSetBlurInfo(null)` path without changing the native blur-enabled flags, radius or saved preferences. If the strategy is absent, skip the operation. An unsupported variant retains the established return path and logs the unavailable optional cleanup.

The existing native HEGT spring and final saved-height restoration remain. Coalesced after-child-draw rebuilding from 0.13.5 is retained so a requested fresh outline consumes current glyph paths. This synchronization alone was insufficient and is not described as the confirmed fix. The temporary 0.13.6 trace code has been removed.

## Validation

Build/v3 signing and 204 Android checks pass. Twelve suites: ClockBlurSyncProbe 24, OptimizationProbe 26, ClockContentClipProbe 12, CodebaseAuditProbe 16, RenderHotPathProbe 22, ReflectContractTest 6, DepthRegionProbe 16, DepthV2PlatformProbe 20, MediaReturnProbe 17, NowBarPipelineProbe 16, LatestDepthWorkTest 11 and LifecycleContractTest 18. Tests cover onDraw-owned surrogate path timing, work coalescing/cancellation, native-strategy cleanup without altering flags, and the previous rendering/lifecycle regressions. Isolated checks do not replace visual verification.

On the phone, the player was held open for over 6 seconds and collapsed with Back. Logs show:

```
20:51:42.562 Discarded compact blur outline on media return
20:51:42.578 Media clock blur aligned after glyph draw
20:51:43.236 Manual clock heights restored after media
```

Recording: `/sdcard/media0137-long-return.mp4`; 20 ms frame samples are being inspected in `work/media0137-long-frames`. User confirmation remains pending. Closing via tap and the stock non-fullscreen player are not yet certified by this test.

No wallpaper edits or saved-setting rewrites. USB keep-awake restored to original 0. Existing signed fallback APKs remain; 0.13.4–0.13.6 should be treated as incomplete/diagnostic candidates.
