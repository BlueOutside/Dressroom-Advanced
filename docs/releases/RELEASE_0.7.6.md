# 0.7.6 alpha — widget dragging and editor wallpaper detection

Follow-up: the user reported that 0.7.6 still failed while idle. The confirmed correction is in RELEASE_0.7.8.md: detailed flower contours caused native Path.op to fail, and Region subtraction now supplies the fallback. The user confirmed idle flower depth in 0.7.8.

Build 0.7.6 / versionCode 55 is installed. This continues the 0.7.4 native widget integration.

## Widget interaction

- Hides Samsung's tap-to-rotate guide through its binding; the native alignment capability and Advanced Rotate transaction remain available.
- Moves the entire native widget group by dragging its preview bounds. A tap still opens Samsung's widget picker. Gesture handling is confined to EditMainActivity and the current widget group's bounds; native picker gestures are not replaced.
- A drag from Samsung default switches to Free position. Free moves horizontally and vertically. Above/Below slide horizontally along the clock edge; Left/Right slide vertically. A persisted dockOffset stores that extra slide distance, independently of the gap.
- Uses the editor's real PreviewScale for pointer conversion, updates WidgetData and its native bounds through the established placement path, and keeps module extras in the Apply transaction. Runtime placement reads the same slide offset.

On-phone 0.7.5 tests verified a free drag of −160/−490 physical pixels, with the group and selection bounds following it. A subsequent Right-attached diagonal drag moved the group down 310 pixels while retaining X. A regular tap then opened the native provider picker. The temporary Right attachment was an unsaved test choice and was discarded before the 0.7.6 restart; it explains the user's up/down-only observation.

## Subject detection

The user reproduced an incorrect flower mask after choosing a new wallpaper without applying it. The previous detector read WallpaperManager's applied wallpaper, so it could still scan the mountain. Detection now snapshots the current CropImageView via getCroppedBitmapWithMatrix on the UI thread, then runs Samsung Object Capture on that snapshot. It includes the current crop and does not fall back to a different applied image when the preview is unavailable.

New masks retain up to 1440 pixels of width, and rendering no longer blurs them with the former five-pixel box filter. That filter could erase narrow stems before path thresholding. A shared immutable subject path avoids rebuilding the higher-detail contour for each clock/widget view. Existing saved masks are not rescanned automatically.

The user confirmed 0.7.5 detects the flower, but reported that depth appears only during clock interaction. Version 0.7.6 adds replacement clock owners to the editor depth binding and lets the idle native blur path inherit the active mask from preview ancestors. This targets the separate native idle surface when candidate wallpaper changes replace the renderer. Visual confirmation of this correction is pending.

## Validation and limits

- APK compiled, signed with v3 verification, and installed. Dressroom restarted with the user's permission to discard the flower test draft.
- 54 Java checks passed: reflection 6, stretch identity 6, lifecycle 18, widget geometry 24, including snapped-edge offset and bounds checks.
- Detection completed through the preview-based path on the phone. The user confirmed it follows the new flower in 0.7.5.
- Free/snap dragging and native picker tap behavior verified on the phone as above.
- Idle flower depth in 0.7.6, a new Apply/reopen cycle for drag offsets, and runtime offset transitions remain to be visually confirmed. Samsung's extraction quality can still omit subjects/details; the source correction cannot guarantee perfect segmentation.

Rollback: work/source-before-widget-drag-074 contains the 0.7.4 source/APK, and the stable 0.7.4 APK was restored in outputs after the version bump. Main editor-preview discrepancies and other One UI versions remain outside this change.
