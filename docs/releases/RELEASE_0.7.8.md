# 0.7.8 alpha - draggable widgets and detailed subject depth

Installed versionCode 57. This includes the widget-drag and preview-wallpaper changes introduced in 0.7.5-0.7.6, plus the confirmed idle-flower correction.

## Widget controls

The native rotation guide is hidden; Advanced Rotate retains Samsung's layout transaction. Dragging the preview widget group moves its native model and bounds. A tap still opens the native provider picker. Samsung default becomes Free on a group drag; Free moves both axes. Above/Below slide horizontally, Left/Right slide vertically along the selected edge. Gap and slide offset are independent. Dock offsets are saved with the existing widget extras and applied by runtime placement.

On-phone checks verified a free drag of −160/−490 physical pixels, a Right-attached diagonal drag that moved down 310 pixels without changing X, and a tap that opened the native provider picker. A temporary Right attachment left during testing explained the user's up/down-only observation; that unsaved test state was discarded during subsequent restarts. See RELEASE_0.7.6.md for the gesture integration details.

## Correct wallpaper source

The flower initially received a mask extracted from the previously applied mountain wallpaper. The user confirmed the flower had not been applied. Detection now captures Dressroom's current CropImageView with its current image matrix on the UI thread and scans that bitmap. It no longer falls back to the applied image when the preview is unavailable. The user confirmed the cutout follows the unapplied flower.

Masks preserve up to 1440 pixels of width and no longer receive the five-pixel box blur that could erase narrow details. Their immutable vector contour is shared across views. Existing saved masks are not automatically rescanned.

## Detailed contours and idle blur

The user reported interaction-only depth in 0.7.5 and 0.7.6. Replacement-clock binding alone did not fix it. A focused 0.7.7 trace showed that the new flower mask reached the idle blur hook, but the path intersection was invalid. The 0.7.8 trace explicitly reports intersection success=false for the 1152×2496 flower mask; the coarse mountain mask reports success=true.

The old depth hook returned Samsung's uncut blur path when Path.op(DIFFERENCE) failed. Interaction used the bitmap drawing path, so it still clipped correctly. Version 0.7.8 falls back to integer Region subtraction at 2× host resolution (half-pixel coordinates). It uses the same fallback for native widget blur/background/content clipping when their vector subtraction fails. Ordinary successful vector operations remain unchanged.

The user confirmed that depth remains after releasing the clock on the flower in 0.7.8. A settled editor capture corroborates the leaf/stem cutout. The replacement-clock/inherited-mask binding from 0.7.6 remains, but is not presented as the sole cause or confirmed fix.

## Validation

- APK compiled, v3-signature verified and installed as versionCode 57.
- 54 JVM checks passed (reflection 6, stretch identity 6, lifecycle 18, widget geometry 24).
- A minimal native Android probe passed 8 checks against the real Region rasterizer: one-pixel stem occlusion, retained subject holes, untouched surrounding pixels, empty subject and complete coverage. This probe does not load the target APKs.
- User confirmed correct new-flower source and idle depth after release in 0.7.8.
- Capture: work/flower-depth-fixed.png. Focused device trace records the failed native float-path intersection for that mask.
- Free/snap group dragging and native picker tap behavior were exercised on the phone. A new Apply/reopen/runtime cycle specifically for dock offsets has not yet been visually confirmed.

Samsung subject extraction can still omit image details. Main preview differences, all provider variants, every large-grid/cancel combination, cover screens, and other One UI versions remain unvalidated. The current flower is a test editor draft; these tests did not require applying it.
