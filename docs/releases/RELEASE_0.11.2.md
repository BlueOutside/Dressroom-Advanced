# Automatic source-space depth and edge quality — 0.11.2 alpha

Installed versionCode 78. Scope: automatic static-image detection and live editor crop/zoom tracking, with improved mask geometry. Subject editing/application UI and animated/video wallpaper are deferred as requested.

## Result

- Enabling clock depth can initiate detection without first creating a mask manually. Existing depth-enabled profiles bind from the editor lifecycle independently of opening Advanced.
- The user confirmed automatic detection before opening Advanced. The initial full-image-only pass lost flower branches; 0.11.1 combines full-image detection with a complementary visible-detail pass and the SDK's individual object results. Both results are projected into the same full-image coordinates and merged by maximum alpha. Overlap does not raise 50% alpha to 75% as SRC_OVER would.
- The user confirmed the other branches returned and the cutout follows wallpaper pan/zoom. A crop/zoom updates transforms, not inference. New source images trigger debounced detection.
- The full-image alpha mask, source fingerprint and version are saved alongside a baked screen-space fallback. Matching sources can rebind the saved mask to the current editor transform; changed sources are detected again. Existing baked masks remain readable.
- Subpixel alpha contours replace integer-row Region boundary extraction in normal operation. Holes, disconnected objects and thin diagonals are retained, with 0.2 px contour simplification rather than the previous 0.75 px. The existing bounded Region subtraction fallback and native widget/blur clipping remain in use.

## Lifecycle and persistence

`DepthAuto` tracks the main wallpaper CropImageView, its drawable/bitmap generation, editor session and current request. Gallery thumbnails and other windows do not become the detection source. One running job and one latest pending descriptor are retained; large snapshots are allocated only when a job starts. Stale results are rejected. Bitmap ownership stays with the proper caller, and contour preparation runs on the detector worker for new scans.

Mask blobs now use content-addressed files in the module's private provider, rather than large settings Bundles. Uploads use temporary files, checksum verification and atomic publication before a profile references them. The provider retains its existing Samsung/own-UID access checks. Readers verify hashes and use a bounded byte cache. Runtime AOD/SystemUI readers load the baked mask rather than the full editor source matte. Legacy inline PNG data remains readable.

Native Apply/Done remains the commit boundary. If Apply is pressed before the source is ready, the save excludes stale mask data and reports that another Apply is needed after detection. This case does not publish an old subject over a different wallpaper. Existing metadata is not a guarantee of matching source pixels: a fingerprint mismatch causes detection rather than silently stretching the old matte to a new image.

## Regressions found and corrected during phone testing

1. **Manual stretch depended on Advanced.** Depth's saved heights are now restored into each incoming native model, passed through the editor font setter, and held in the editor's `g0` rendering path. That path uses cached profile data instead of IPC for each render. Runtime media flattening/restoration is separate and unchanged. The user confirmed manual stretch and full flower depth are present before visiting Advanced.
2. **Unchanged custom grid could disappear after Apply.** Native Apply can allocate a new wallpaper ID. Widget settings were frozen only when marked dirty, and their commit context could be missing before opening the widget UI. Settings are now carried across every native Apply when loaded, with a usable context. Native picker finalization still occurs only for actual widget edits, not mask-only updates. The same save path can retain clock profiles even when Advanced was never opened. The user rebuilt their affected widgets manually before this correction; those selections were preserved. The user confirmed Custom grid and arrangement remain correct after Apply/reopen.

## Validation

- APK builds with the existing signer; v3 signature verifies. Final install is 0.11.2 / 78. SystemUI and AOD were reloaded by process, and the SystemUI rendering guard passed. AOD was not force-stopped.
- All 77 JVM checks pass: existing reflection/identity/lifecycle/widget/deferred-discovery checks (66), plus the latest-job mailbox checks (11).
- Final candidate: 20 Android contracts for contours, crop/rotation mapping, metadata/legacy/reference persistence, source isolation, inherited/independent widget masks, and maximum-alpha merging/ownership.
- Existing 16 Android Region checks and 7 native clipping/blur groups pass against the final APK. The prior 10 mask/contrast checks also passed during the integration.
- A 1.2 MB file-bridge round trip passed through the actual installed provider as an authorized Samsung UID, after evicting the reader cache. It did not modify any profile. The standalone probe uses an externally acquired provider client because a plain app_process ApplicationThread is not registered with ActivityManager.
- On-phone user confirmations: automatic detection, recovered flower branches, wallpaper-relative movement, and final manual-stretch/grid persistence before/after Apply without opening Advanced.
- Private settings inspection confirms source-version 2, source and screen file references, saved manual heights, and the user's 4×2 custom grid.

## Performance evidence and limits

The earlier isolated contour benchmark on the saved 1152×2496 flower mask measured approximately 11–25 ms versus 77–134 ms for the old preparation path, with 2418 versus 3198 simplified points. This is CPU mask preparation, not inference time, AOD FPS or battery consumption. The complementary detail pass adds detection work once per source; it is not run per frame.

JADX shows this phone's `ImageWallpaper.CanvasEngine.shouldZoomOutWallpaper()` returns false. The build preserves native runtime cropping and uses the committed screen fallback, rather than inventing a framework zoom formula. Other wallpaper engines, external display/rotation changes and other device families are not certified. The blueprint remains in DEPTH_UPGRADE_RESEARCH.md; its prototype-only status is historical now that this static-image implementation is integrated.

The blue/raised rim is not claimed universally fixed. Bitmap alpha is still soft while Samsung's native clip-path interfaces are geometric. Hair/fur, unusual transparency, very large or noisy mattes, rapid source replacement during Apply, and AOD transition performance across more wallpapers need broader validation. The previous small native blur refresh quirk is also tracked separately.

## Recovery notes

Original 0.10.4 sources/APK and pre-upgrade preferences are backed up in the workspace at `work/source-before-depth-011/`. The old provider does not understand new file references; downgrading to 0.10.4 after saving v2 profiles requires the matching preference backup. Prefer a compatible 0.11.x recovery build to retain newer edits.
