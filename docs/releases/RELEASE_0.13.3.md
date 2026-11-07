# Rendering allocations and cache cleanup - 0.13.3 alpha

VersionCode 96. Installed as an update, signed with the existing module key. Source-only optimization pass; no JADX inspection.

## Changes

- **CPU/allocation:** No-argument reflection calls use a dedicated name-to-method cache and a shared empty argument array. The fixed-arity overload also avoids the compiler-generated empty varargs array at module call sites. Explicit empty varargs calls retain their ABI and use the same fast path. Argument-bearing overload selection, inherited member lookup and target exception handling are unchanged.
- **Draw preparation:** Clock container clipping and widget blur watchers share `ViewGeometry`. Each watcher retains its matrix and comparison buffers instead of constructing Matrix/float-array snapshots on every pre-draw. Updates still compare the exact global transform, dimensions, subject identity and media state/cover identity. Parent scroll/translation, scale, rotation and resize continue to invalidate geometry.
- **Effect graphs:** Each active shadow reuses its immutable RenderEffect composition while its radius, offsets, color and native input effect identity are unchanged. Changing any input creates a fresh composition; disabling the shadow drops its cached graph. The blend/filter order, blur radius and shader tile mode are identical to the previous implementation. This reduces graph construction; GPU frame-time improvement was not measured.
- **Memory:** The bounded subtraction cache holds weak references to subject paths and prunes dead entries on lookup, so retired source paths need not remain alive for the cache. Live results are still copied before returning. Removed the unused original-background reference from media restoration records; the actual current background wrapper still owns its restoration drawable. Clock tracking prunes collected weak-reference entries when new clocks are tracked.
- **Cleanup:** Removed duplicated geometry-snapshot logic and moved shadow composition into one bounded per-view cache helper. No preference/schema or UI changes, no mask-quality changes, and no frame-rate/timing reduction.

## Measurement

`LookupAllocationProbe` ran the same DEX against the previous 0.13.2 APK and the candidate 0.13.3 APK in isolated on-phone processes. Each trial warmed 10,000 calls, then measured 20,000 repeated no-argument lookups with a reused empty argument array. Java allocation uses Android's `art.gc.bytes-allocated` counter.

| Trial | 0.13.2 time | 0.13.2 allocated | 0.13.3 time | 0.13.3 allocated |
| --- | --- | --- | --- | --- |
| 1 | 5.482188 ms | 3,211,264 B | 1.940052 ms | 0 B reported |
| 2 | 5.476562 ms | 3,211,264 B | 1.917864 ms | 0 B reported |
| 3 | 5.537656 ms | 3,211,264 B | 1.956355 ms | 0 B reported |

Raw results: `work/opt0133-allocation-results.txt`. These measurements cover one lookup hot path, not total application allocation, peak memory, GPU utilization or animation FPS. Native frame rendering and subject geometry quality are not reduced to obtain them.

## Validation

Build/v3 signing passed. Eleven real Android probe suites passed 180 checks: OptimizationProbe 26, ClockContentClipProbe 12, CodebaseAuditProbe 16, RenderHotPathProbe 22, ReflectContractTest 6, DepthRegionProbe 16, DepthV2PlatformProbe 20, MediaReturnProbe 17, NowBarPipelineProbe 16, LatestDepthWorkTest 11 and LifecycleContractTest 18. The new checks cover stable and changing transforms, parent movement/scroll, mask identity replacement/removal, effect-cache input changes and disable/re-enable cleanup. Existing hole/edge/alpha, native animation, touch, saved-state and clipping restoration checks pass.

SystemUI reloaded once and logged build 0.13.3 widget-owner hooks under PID 20949. The foreground was another app during the screenshot check, so no taps or navigation were used to take over the phone for further visual tests. Live shadow appearance, transition smoothness and independent AOD-process reload remain user checks. Dressroom was not force-stopped, no wallpaper edits were applied, and USB keep-awake remains at its original 0. The preceding signed APKs remain available.
