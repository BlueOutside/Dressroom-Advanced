# One UI 9 Dressroom research aliases

Reference package `com.samsung.android.app.dressroom`, version 3.0.10.38
(301038000). The alias names are for JADX. Use the original DEX names when
comparing other builds.

## Apply, preview, and touch dispatch

| DEX owner and member | Alias |
| --- | --- |
| `p8.a` | `ComponentMediator` |
| `p8.a.i()` | `beginWallpaperSave` |
| `p8.a.e(MotionEvent)` | `dispatchTouchToPreviewComponents` |
| `p8.a.e` (field) | `applyButton` |
| `p8.a.H` | `saveComponents` |
| `p8.a.I` | `completedSaveCount` |
| `j6.d.w()` | `finishWallpaperSaveWhenReady` |
| `p8.a.f` (method) | `hideEditorPanel` |
| `p8.a.j` (method) | `notifyOtherPreviewComponents` |
| `j6.d.v` (method) | `onComponentSaveCompleted` |
| `p8.a.b` (field) | `editorActivity` |
| `p8.a.g` (field) | `lockscreenPreview` |
| `p8.a.J` (field) | `saveCompletionListener` |

`EditMainActivity.dispatchTouchEvent` calls the component mediator before
Android's normal dispatch. The save callback increments the component count
before the final completion check. The class `j6.d` also implements unrelated
callbacks, so the class itself keeps its original name.

## Wallpaper crop and layers

All entries below are on
`com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView`.

| DEX / JADX method before alias | Alias |
| --- | --- |
| `D` / `m3375D` | `setLayerBitmap` |
| `F` / `m3377F` | `syncDrawableBounds` |
| `G` / `m3378G` | `animateDrawableBounds` |
| `J` / `m3379J` | `updateSingleFingerPan` |
| `K` / `m3380K` | `finishSingleFingerPan` |
| `L` / `m3381L` | `refreshDrawableBounds` |
| `M` / `m3382M` | `updateObjectLayerVisibility` |
| `N` / `m3383N` | `scaleAroundGestureFocus` |
| `j` / `m3388j` | `convertIntelligentCropToDrawableBounds` |
| `k` / `m3389k` | `convertToExpandableDrawable` |
| `m` / `m3390m` | `getCropRectF` |
| `p` / `m3393p` | `renderCroppedBitmap` |
| `s` / `m3395s` | `initializeCropGestures` |
| `u` / `m3396u` | `initializeDrawableBounds` |
| `w` / `m3398w` | `usesExpandableCenterCrop` |

Start with `getCropRectF` and `renderCroppedBitmap` when following saved crop
coordinates. `setLayerBitmap` and `updateObjectLayerVisibility` are the
separate path for the preview's object layer.
