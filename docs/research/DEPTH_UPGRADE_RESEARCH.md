# Depth quality and live tracking research - 2026-10-27

**Implementation update:** static-image automatic detection, source-space crop tracking, contour conversion and file-backed persistence are integrated in [0.11.2](../releases/RELEASE_0.11.2.md). The findings/prototype measurements below describe the pre-upgrade baseline and research; subject editing and video remain deferred.

User priority: automatic detection and live crop/zoom tracking first, animated/video wallpaper subjects afterward. Installed module remains the user-confirmed 0.10.4. The contour prototype below runs independently through app_process; no prototype hooks or new segmentation backend were installed.

## Findings in the current implementation

1. **Detection sees the visible crop, not the whole source.** `LiveSubject.capture` calls Dressroom's `CropImageView.getCroppedBitmapWithMatrix`. JADX confirms that method creates a view-sized bitmap, sets `getImageMatrix`, and draws `getDrawableInner` into it. `unproject` can map that result back into source coordinates but cannot recover a subject outside the captured crop. Zooming out after detection can therefore expose undetected parts.
2. **Live tracking is partial and session-bound.** `LiveSubject.global/changed` tracks drawable bounds, image matrix and the view's global transform. `DepthEffects.updateLiveMask` updates active entries. The live source reference exists only for detections in the current editor session. Saved masks are baked into a screen-shaped image; a reopened editor/runtime does not retain the original source identity, source dimensions or detection/crop transform.
3. **Runtime mapping assumes a static display rectangle.** `drawMask/screenSubjectPath` use display dimensions, preview scale/origin and center crop. They do not consume the wallpaper engine's current zoom/parallax/crop transform. This needs tracing on SystemUI before claiming continuous lockscreen/AOD alignment.
4. **Soft and hard mask representations diverge.** Clock pixels use the bitmap's fractional alpha, while blur/widget geometry thresholds alpha at 128 and simplifies its Region boundary at 0.75 px. Repeated resampling and simplification can alter edges and thin structures. This is a plausible contributor to rims; the blue/raised edge is not proven fixed or fully explained.
5. **The mask pipeline repeatedly rasterizes and encodes.** Detection encodes PNG, unprojection decodes/re-encodes, applying entries decodes and builds contours, and successful Apply bakes another PNG. Several of these operations touch the main thread. A future automatic scheduler must avoid doing them during drawing or gestures.
6. **The serial detector queue is unbounded.** It serializes SDK ownership and rejects stale results, but snapshots are allocated before submitting jobs. Automatic detection must coalesce pending work before allocating another large snapshot, rather than enqueueing one bitmap per wallpaper event.
7. **Persistence is a baked PNG in profile JSON/Bundle data.** Schema and transport need reviewing before adding higher-resolution or time-indexed masks; large Binder payloads and duplicate copies must not grow with every clock digit/widget.

## Samsung APIs inspected through JADX MCP

- `objectcapture.base.ObjectCapture.capture(Bitmap)` supplies automatic selection. `capture(Bitmap,float,float)` supplies point-guided extraction. The latter enables a possible tap-to-choose subject flow; its quality advantage has not been measured.
- `SribObjectCaptureImpl.capture` delegates automatic segmentation to `SribCaptureWrapper`. `toObjectResult` exposes a default output and separate object results. The module currently reads the default output. Do not assume the default includes every object or that combining all results is always appropriate.
- `SribObjectCaptureImpl.recreateBitmap` applies mask alpha to source pixels with SRC_IN. `ObjectInfo.getObjBitmap` is a colored cutout with alpha, not a calibrated depth map. There is no public quality-strength parameter in the inspected ObjectCapture interface.
- Point-guided capture uses `VexFwkObjectExtractor` with `AILASSO_TOUCH_COORDINATE` metadata and an asynchronous mask output. Availability must be checked on the actual device.
- `com.samsung.android.media.vision.SemMatting` exposes only a private native method and loads an interactive-segmentation library. Its presence is not evidence of a supported refinement API; do not call it without establishing the native contract.
- `objectcapture.video.VideoClipper` exposes clip information, capability checks, abort/finish and a bitmap/point entry point. This does not establish a continuously available wallpaper mask stream. Video object removal APIs likewise do not prove real-time wallpaper tracking.

## Tested outline prototype

Sources: `work/depth-v2-prototype/MaskContour.java` and `MaskContourProbe.java` in the workspace.

The prototype traces an interpolated 50% alpha contour with marching squares, an asymptotic saddle decision, shared edge vertices and closed even-odd loops. It retains disconnected objects and holes. It reads two bitmap rows at a time instead of making a full pixel-array copy. The experiment uses the existing module's simplifier with a tighter 0.2 px tolerance, compared with the current Region builder and 0.75 px tolerance.

On-phone results, five timed runs after two warmups:

| Saved mask | Current median | Prototype median | Current points | Prototype points |
| --- | ---: | ---: | ---: | ---: |
| Flower, 1152×2496, first run | 77.15 ms | 11.12 ms | 3198 | 2418 |
| Same flower, second run | 133.74 ms | 25.09 ms | 3198 | 2418 |
| Simple mask, 512×1109, first run | 1.88 ms | 2.01 ms | 214 | 370 |
| Same simple mask, second run | 3.51 ms | 4.70 ms | 214 | 370 |

Eleven Android checks passed: empty/opaque masks, boundaries, holes, disconnected objects, diagonal stems, subpixel crossings, and preserving input pixels.

At a 4× raster sampling of pixel centers against the original alpha>=128 classification, mismatches were 1217 versus 1329 for the flower, and 0 versus 132 for the simple mask (prototype versus current). This is a geometric diagnostic, not a perceptual quality score. These timings cover CPU outline preparation, not model inference, native SurfaceFlinger clipping cost, total frame time or battery life. Device timing varied substantially. A simple-mask fast path and native rendering comparisons are still required before integration.

## Implementation sequence

### First: source-anchored automatic depth

- Preserve an owned, immutable source-space matte with its source identity, dimensions and coordinate basis. Detect the whole image, with source/crop selection and resolution checked against difficult subjects; compare against today's visible-crop detection so context changes do not silently lose flower parts.
- Make enabling depth initiate detection when needed, and observe actual source changes independently of opening Advanced. Crop/zoom must update only the transform; they must not restart inference.
- Retain one in-flight job and one latest pending source descriptor. Allocate snapshots only when work can start. Gate publication by editor session, source identity and request; cancel stale callbacks without clearing a newer valid matte.
- Store the source-space result and transform metadata on successful native Apply. Keep a migration path for existing baked screen-space masks. Cancel must discard editor drafts and leave committed masks intact.
- Integrate a tested contour builder and cached geometry. Preserve the native cached-node content/blur fixes and media suspension/restore behavior from 0.10.4.
- Add a mask inspection preview and explicit subject selection/refinement so detection failures are visible and correctable. A soft-edge slider alone cannot recover missing petals/stems.
- Trace native SystemUI wallpaper zoom/crop/burn-in transforms and map the same matte into clock/widget/blur coordinates. Avoid fixed-display assumptions on rotation, cover screens and alternate layouts.

### Then: animated/video subjects

- For finite local clips, investigate preparing time-indexed alpha masks off the rendering thread and presenting them at the wallpaper's actual playback timestamp.
- For arbitrary live wallpaper engines, establish access to frames or native subject data first. An app drawing its own Surface is not equivalent to a static CropImageView.
- Reuse temporal information and bound pending work. Do not run still-image detection or PNG encoding on every draw, and do not infer mask timing from wall-clock time alone.
- Match mask cadence to the wallpaper and AOD's actual update cadence, handle loops/seeks/pause/screen transitions, and stop background work when the wallpaper is not visible.

## Acceptance checks before a new on-phone feature build

- Thin flowers/stems, hair/fur, disconnected subjects, holes, bright and dark backgrounds: compare matte coverage, fine edges and rim appearance with the same source/crop.
- Detect once, pan/zoom out/in and rotate without losing previously offscreen source coverage; Apply/reopen and lockscreen/AOD must agree.
- Enable without first visiting Advanced, rapidly switch sources, cancel detection, cancel editing, Apply during work, restart provider/process and update module.
- Cached native blur/content must stay aligned at idle, during movement, doze/wake, widget placement and media expansion/collapse.
- Measure inference, first contour build, allocations, steady-frame work and transition frame times separately. A faster contour build alone is not a performance pass for the renderer.
