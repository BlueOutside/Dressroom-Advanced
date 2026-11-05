# Code-only bug audit — 0.13.2 alpha

VersionCode 95. This pass used the module's own source and isolated Android probes; no JADX or Samsung binary inspection was used. The user confirmed that 0.13.1 made the animation substantially smoother.

## Fixes

1. **Depth cache fill rules.** The subtraction cache compared original path points but omitted fill type. Identical contours using winding and even-odd fill could reuse the wrong cutout and lose a hole. Fill type is now part of the cache key. The regression reproduced against the 0.13.1 APK on the phone before the fix and passes on 0.13.2.
2. **Optional detection failure and bitmap ownership.** A successful full-image mask was previously discarded if the visible-detail pass threw. The detail pass now refines the full result when available and retains it on optional-pass failure. A failure with neither result still fails normally. Temporary detail buffers are recycled; worker-owned prepared masks are recycled if encoding, contour extraction or result posting fails. Ownership transfers only after posting the result successfully. Capture-failure callbacks now release the job mailbox in a finally block. Detector-release linkage failures no longer skip bitmap/job cleanup.
3. **Primitive reflection arguments.** The shared helper supported boolean/int/float/long but omitted double/byte/short/char. Matching now supports all eight primitive types while retaining the existing overload ambiguity and no-numeric-widening rules.

## Validation

Build and existing-key v3 signature verification passed. Nine on-phone probe suites passed 142 checks:

- CodebaseAuditProbe: 16 (fill-rule regression, primitive dispatch, detail failure/fallback, alpha coverage and ownership).
- RenderHotPathProbe: 22.
- ReflectContractTest: 6.
- DepthRegionProbe: 16.
- DepthV2PlatformProbe: 20.
- MediaReturnProbe: 17.
- NowBarPipelineProbe: 16.
- LatestDepthWorkTest: 11.
- LifecycleContractTest: 18.

These are actual Android rasterizer, bitmap, animator and lifecycle-helper checks. Synthetic detail failures test recovery without invoking Samsung's detector. End-to-end fresh editor detection remains a user check after Dressroom restarts; this pass does not claim every SDK/device error case was exercised.

APK installed as an update and SystemUI reloaded once. Runtime logs confirm build 0.13.2 in SystemUI and Dressroom. The editor screenshot (`work/audit0132-start.png`) shows saved stretched clock, widgets and flower depth rendering. Fresh forced detection was not exercised. No preference schema changed, saved profiles were not rewritten, and no wallpaper draft was applied. Dressroom was not force-stopped. The temporary keep-awake setting remains at its original 0.

## Audit scope and limits

Reviewed mask generation/refinement, geometry caching, reflection, depth job cancellation/completion, saved-state helpers, and clock/widget/media rendering cleanup. This is a focused audit, not a claim that the whole module is bug-free. Native rendering behavior, tablet/foldable/A-series compatibility, arbitrary frames and fresh weather-wallpaper detection retain their previous validation limits. The performance changes and native spring timing from 0.13.1 remain.
