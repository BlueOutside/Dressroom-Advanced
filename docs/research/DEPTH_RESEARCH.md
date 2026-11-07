# Depth effect research (2026-10-24)

**Latest quality/live-tracking review (2026-10-27):** [Depth upgrade research](DEPTH_UPGRADE_RESEARCH.md) records the current 0.10.4 limitations, inspected Samsung extraction/video APIs, and an independently tested contour prototype. Historical interaction-only depth failures below were subsequently fixed; they are not the installed baseline.

**Current lifecycle audit (2026-10-25):** see [CLOCK_PIPELINE_AUDIT.md](CLOCK_PIPELINE_AUDIT.md). Builds through 0.5.23 still lose clock occlusion while idle. The report records verified native state/scene-save behavior and separates it from earlier unverified rendering hypotheses. The SDK mask detector works; that does not confirm final render integration.

The requested effect is visual occlusion: draw the clock normally, then hide only the clock pixels that fall inside the wallpaper subject. This must remain independent from Samsung's dynamic-stretch calculation, so manual segment heights and position are not changed by the subject.

What the inspected APKs expose:

- Dressroom's `WallpaperObjectData` contains only an `isStretchableWallpaper` Boolean. It tells the suggestion controller whether subject-aware placement can run; it is not a subject mask.
- Dressroom's `qp.e` suggestion controller checks that Boolean together with wallpaper expansion and clock-style eligibility. `WallpaperClockBundle` stores the resulting geometry and stretch heights. Reusing that bundle would reintroduce Samsung's automatic resizing, so it is not the rendering input for the proposed effect.
- Dressroom's region cache (`oc.f`, populated through `yc.x`) has normal and exclusive regions. JADX call sites show these feed stretch and forbidden placement areas; neither is the subject silhouette.
- SystemUI contains Samsung's `BgRegion`/`SemRegion` classes. `BgRegion` deserializes an `android.graphics.Region` from rectangle runs and carries a `contentsType`. That may supply the wallpaper's background or subject area at runtime; the producing service and coordinate transform have not yet been traced.

Implementation path once the region is verified:

1. Capture the region from the wallpaper pipeline on a wallpaper with a clear subject, without asking the clock to resize.
2. Map its wallpaper coordinates into the clock view's screen coordinates, accounting for crop, scale, rotation, and lockscreen/AOD differences.
3. Clip only clock drawing against the subject region, preserving the clock's touch area and Dressroom controls. An edge-offset control can expand or contract the mask slightly.
4. Expose separate options for visual occlusion and, on styles that support it, Samsung's subject-aware automatic stretch/placement. Manual stretch continues to win when selected.
5. Test a supported dynamic clock and a normal clock with the same wallpaper, then verify lockscreen, Dressroom, and AOD separately.

## Traced subject-mask chain (JADX, Dressroom APK)

- Editor consumer: `p333q7.DialogInterfaceOnCancelListenerC6304i0.m8489k2` reads the object-detection result `C3001j.f7827a` (`C3004m`) and passes `C3004m.f7856n` (a `Region`) to `m8177j`, which stores it as `C6027h0.objectRegion`. `m6311c` copies `f7856n` into `C5329c.f16423k` for the wallpaper content callback.
- Subject bitmaps on `C3004m`: `f7853k` (source), `f7854l` (full-size mask), `f7855m` (variant). `p192j6.AbstractC4170e.m6312f` picks one by style.
- Mask writer: `C3004m.m4721c(grayFullBitmap, objectBounds)` sets `f7854l` (full mask) and `f7851i` (cropped subject). It is called from `ca.C1701h.m1625b` with the gray filter bitmap from `m1627d(GrayScale|Shadow)` and `objectBounds = c3004m.m4722d()`. The blur variant is `m4720b`.
- Not yet traced: the code that fills `C3004m.f7856n` and the `C3004m.m4722d()` Rect from the native detector output. `o6.C5729d.m7894n` only clears these fields. `RunnableC0066f.run` only reads `f7856n`, and `C4180d.m6337g` is not the writer.
- Writer found in smali (apktool 2.x, `work/dressroom-smali`): `d7/m.n` (Region) is written only in `androidx/work/impl/d.smali`, method `run()`, around line 6838 (`iput-object v5, v2, Ld7/m;->n`). This is the code JADX could not decompile as `p043c7.C1649z.accept`, so the obfuscated smali name differs from the JADX name.
- How the Region is built (same `run()`, lines ~6555-6750): the code reads `ObjectResult.getOutput().getObjBitmap()` (an ARGB int bitmap), copies it with `Bitmap.getPixels` into an int array, then walks every row. Each run of non-zero pixels is added to a new `android.graphics.Region` with `Region.op(..., UNION)`. Zero pixels are background. The Region is translated by `ObjectInfo.getBoundRect()` left/top (line ~6590s) and stored in `n`.
- `Ld7/m.f` (Rect) is set at line 5547 in the same `run()`. It is used for the bounds, so the Region's coordinate space is the object-bounds space and needs the same translation as the clock.
- Result in `d.smali`: the subject region is a union of the mask's non-zero runs in the object-bounds frame. The mapping to screen coordinates still needs live verification.
- Coordinate frame (same `run()`, lines ~5539-5630): the subject `Region` is built from `ObjectInfo.getObjBitmap()` and translated by `ObjectInfo.getBoundRect()` left/top. The same `getBoundRect()` Rect is stored as `d7/m.f`. The detector's bitmap is drawn onto a canvas of the source wallpaper size at the bound offset, so the Region and its Rect are in wallpaper-source pixel space.
- Input chain: `d7/i` (`a` = source wallpaper Bitmap, `c` = ObjectResult, `d` = Uri) is built in `j6/e`, from `j6/b` (`b` wallpaper bitmap, `c` Rect). `j6/b` is built in `i4/i`, `i4/q`, and `ra/n`. The Rect's exact source is still to be traced.
- Status: the producer and its pixel-to-Region logic are identified. Still open: the Rect source in `i4/i`, the crop/scale/rotation applied to the wallpaper before the clock is drawn, and whether the Region stays in sync with it. These are needed to map the Region onto screen coordinates.

Version 0.5.8 adds a first depth control backed by Samsung's bundled object-capture SDK. It scans the current static lockscreen wallpaper, turns the returned subject bitmap into a compact alpha mask, and clips clock drawing in the editor and runtime. Manual stretch is untouched. The center-crop mapping and AOD behavior still need on-device confirmation; detection errors are reported in the editor instead of silently enabling the effect.
