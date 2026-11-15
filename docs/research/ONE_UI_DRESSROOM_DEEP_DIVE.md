# One UI 9 Dressroom Deep Dive (`com.samsung.android.app.dressroom`)

**Reference APK:** `com.samsung.android.app.dressroom` (`versionName="3.0.10.38"`, `versionCode="301038000"`, `compileSdkVersion="37"`, `targetSdkVersion="37"`)  
**Scope:** Lock/home screen editor (`EditMainActivity`) architecture, `ComponentMediator` and cross-component mediators, editor view hierarchy and blur containers, `ItemEditorController` and `ClockPreviewItem`, wallpaper preview controller factory (`AbstractC5735h`) and live/interactive host (`AbstractSurfaceHolderCallbackC4355l`), `CropImageView` multi-layer object/frame stack, `ExpandableDrawable` (`RenderEffect` + AGSL dithering), segmentation / clock-avoidance / `EditorStretchAreaMediatorImpl`, `WallpaperFilter`, wallpaper & lockscreen save/commit pipelines, and `DressRoomProvider` / `ClockProvider`.  
**Related docs:** [DRESSROOM_9_ALIASES.md](DRESSROOM_9_ALIASES.md), [ONE_UI_SYSTEMUI_DEEP_DIVE.md](ONE_UI_SYSTEMUI_DEEP_DIVE.md), [ONE_UI_CLOCK_DEEP_DIVE.md](ONE_UI_CLOCK_DEEP_DIVE.md), [ONE_UI_DEPTH_DEEP_DIVE.md](ONE_UI_DEPTH_DEEP_DIVE.md), [ONE_UI_WIDGET_DEEP_DIVE.md](ONE_UI_WIDGET_DEEP_DIVE.md).

---

## 1. Activity, Save Components & Cross-Component Mediators

- **`EditMainActivity`** (`com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity`, extends `EditBaseActivity`):
  - `mo3337a0()` sets outline clipping and elevation on `#preview_single_view_root`, passes `#preview_single_view_root` and `#blending_effect_view` into `EditorPreviewVisibilityMediator` (`p316p8.C6052x`), and sets `elevation = 25.0f` on `#progress_layout`.
  - Routes `dispatchTouchEvent` / `onTouchEvent` through the mediator and preview touch handlers.
- **`p316p8.ComponentMediator`** manages:
  - **Save components (`saveComponents`):**
    - `C5747t` (`WallpaperComponent`)
    - `p080dc.C3088p` (`LockscreenComponent`)
    - `C5596d` (`HomeScreenComponent`)
    - `p235l6.C5130k` (`EditorProfileComponent`, which registers `"EditorProfileMediator"` for lockscreen preset suggestions and AI-driven frame/profile predictions)
  - **Cross-component mediators:**
    - `C6052x`: `EditorPreviewVisibilityMediator`
    - `C6033k0`: `EditorWallpaperColorMediator`
    - `C6035l0`: `EditorWallpaperContentMediator`
    - `C6017c0`: `EditorRotateMediator`
    - `C6034l`: `EditorMoveMediator`
    - `C6020e`: `EditorChangePreviewControllerMediator`
    - `C6027h0`: `EditorStretchAreaMediatorImpl`

---

## 2. View Hierarchy, Z-Order, Lockscreen Blur & `ItemEditorController`

### 2.1 Root Preview Stack (`res/layout/edit_main_activity.xml`)

Bottom -> top hierarchy inside the editor activity:

1. `#editor_root`
2. `#lock_clock_blur_on_lockeditor` (tag `lock_clock_blur_on_lockeditor`)
3. `#blending_effect_view` (`ImageView` receiving a blurred background backdrop; e.g., `p332q6.C6282a` calls `C6356a.m8525a(imageView, bitmapDrawable)` using `getBlurredBackgroundBitmap()`)
4. `#editor_preview_container` -> `#preview_single_view_root`:
   - `#wallpaper_preview_container` (tag `WallpaperPreviewContainer`, containing `#video_preview_container` + dynamically added `CropImageView`, `CropCoverImageView`, `CropSurfaceView`, or live `RoundedSurfaceView`)
   - `#home_lock_preview_container` -> `#lock_preview`, `#home_preview`
   - `#progress_layout` (`elevation = 25.0f`)

### 2.2 Lockscreen Preview Blur & Item Layers (`p080dc.C3088p` + `p323po.C6209y`)

Inside `#lock_preview` (`mPreviewRootView` = `f14906e`):

1. **Per-item blur containers (`p347qo.C6398b` `"BlurItemController"`):**
   - If `BlurConfiguration.isSupport()` is true, `C3088p` inflates `AbstractC5560h.item_preview_blur_container` for each `ItemSupportBlurData` (`ClockData`, `WidgetData`, `ShortcutData`) **before** adding `ItemEditorController`.
   - `C6398b.m8581b()` applies `view.semSetBlurInfo(...)` (`BlurVariationType.Preset` vs custom).
2. **`ItemEditorController` (`p323po.C6209y`):**
   - Added to `mPreviewRootView` via `ItemEditorController.m8393d()` above the blur containers.
   - Inflates `item_editor_container` (`item_guide_box_layout` + scale/rotation handles) and `item_preview_container` with 4 stacked child containers (bottom -> top):
     1. `#background_container`
     2. `#lockstar_preview_container`
     3. `#item_preview_container`
     4. `#appear_container`
3. **`ClockPreviewItem` (`p441vo.C7374x`):**
   - Hosts `ClockView` (or `C1554q` informative/world clock when `ClockType == 50002`) inside `clock_item_layout`.
   - Wires `WallpaperClockBundle`, `MotionClockExtension`, `PreviewExtension`, `ImageClockExtension`, `IWeatherManager`, and `C1549l` (`"ClockBlurCallback"`, active when `PreviewState.getIdle() && clockData.isBlurEffectEnabled() && clockData.getIdleBlur()`).
   - Cross-fades clock replacements (`300ms` fade-out, `160ms` delay + `300ms` fade-in).

---

## 3. Wallpaper Controller Factory & Live / Animation Hosts

### 3.1 Controller Factory (`p286o6.AbstractC5735h`)

| Mode / Wallpaper Type | Controller | Notes |
|---|---|---|
| `isDexMode` | `C1635l` | DeX preview controller |
| `0`, `12` | `p043c7.C1623b0` (`ImagePreviewController`) or `C1493b` | Static / layered image preview |
| `3`, `9` | `C3227h` | Motion / multipack variant |
| `7` | `C5754a` | Video preview controller |
| `8` | `SurfaceHolderCallbackC6891i` | Surface-backed video / GIF controller |
| `10` | `C4349f` / `C4350g` | Interactive / live wallpaper subclass |
| `11` | `C7502j` | `IDailyGradientEditor` |
| `13` | `C4365v` | Interactive / live variant |
| `23`, `409` | `C3229h1` | Design / frame / style preview controller |

### 3.2 Live / Interactive Preview Host (`p215k7.AbstractSurfaceHolderCallbackC4355l`, `AbstractC4351h`)

- Mounts a `RoundedSurfaceView` (`"interactive_surfaceView"`) and fallback `ImageView` (`"interactive_fake"`) into `f17758Q` (`#wallpaper_preview_container`).
- Binds the target `WallpaperService` via `p335q9.C6337b` (`ServiceConnectionC6467p`).
- `AbstractC4351h` wires `BinderConnector` for remote live-wallpaper engine IPC.
- Forwards scaled pointer events plus `"android.wallpaper.tap"` and `"android.wallpaper.secondaryTap"` commands via `IWallpaperEngine`.
- Fetches live screenshots and thumbnails synchronously from ContentProvider authority `"com.samsung.android.wallpaper.live.provider.sepwallpaper"` (`"get_screenshot"`, `"get_thumbnail_by_backup_id"`).

### 3.3 Animation Preview Controller (`p332q6.C6282a`)

- Instantiates `C6283b` (for `EnumC4343t.ANIMATE`) or `C6285d` into `f17758Q`, and populates `#blending_effect_view` via `C6356a.m8525a(...)` with `getBlurredBackgroundBitmap()`.

---

## 4. `CropImageView`, Object Layers, `ExpandableDrawable` & Clock Stretch

### 4.1 Multi-Layer `CropImageController` (`p286o6.C5729d`)

- Creates the primary `CropImageView` (`f17710l`, or `CropCoverImageView` for cover/GIF) inside `WallpaperPreviewContainer`.
- Maintains additional overlay `CropImageView` instances in `HashMap<EnumC3014w, CropImageView>` (`f17722x`, tagged `"Effect_<enum>"`) for `p075d7.EnumC3014w`:
  `NONE`, `OBJECT`, `FRAME_BACKGROUND`, `FRAME_BASE`, `FRAME_TOP`, `FRAME_BOTTOM`, `STYLE_CUR`, `STYLE_PREV`, `STYLE_NEXT`.
- Reorders overlay views inside `#wallpaper_preview_container` via `m7888e` / `m7889g` and toggles visibility via `m7884F`.
- Style/frame controller `p151h7.AbstractC3792x` inspects index order between `EnumC3014w.OBJECT` and `EnumC3014w.FRAME_BASE` (`f12824A = 1` if `OBJECT` > `FRAME_BASE`, else `0`).

### 4.2 `CropImageView` Dual-Layer Drawable & Scaled Recording

- `convertToExpandableDrawable` / `setLayerBitmap(int layer, Bitmap bitmap, boolean z10)` manages a 2-layer `LayerDrawable`:
  - **Layer `0`:** wrapped in `fj.C3530j.m5683p(drawable)` / `com.samsung.android.wallpaper.expandable.ExpandableDrawable`.
  - **Layer `1`:** object-layer `BitmapDrawable` (`C3004m.f7853k`), populated in `C1623b0.onObjectCaptureFinish()` via `cropImageViewM1536P1.setLayerBitmap(1, bitmap, false)`.
- **`updateObjectLayerVisibility(String)`:**
  - Sets layer `1` alpha to **`255`** when vertical expansion (`expandable.m9411e()`) is active (and not horizontal expansion `expandable.m9410d()`), otherwise **`0`**. This keeps the segmented foreground subject unblurred over the top blurred/extended margin while expanded.
- **`onDraw(Canvas)`:** when `canvas` width/height differs from view width/height, records into a `Picture` (`beginRecording` -> `super.onDraw` -> `canvas.drawPicture(picture)`).

### 4.3 `ExpandableDrawable` Blur + Outpaint Gradient + AGSL Dithering (`fj.C3530j`)

- **`Expandable` (`p434v9.C7183j`):** computes `EnumC6871b` from `faceRect` / `objectRect` / `cropRect`, tracks horizontal (`m9410d()`) and vertical (`m9411e()`) expansion, and shifts `drawableBounds` (`m9409c`, `m9413h`).
- **Offscreen GPU blur:** creates `RenderNode("ExpandableDrawableBlurEffect")` + `HardwareRenderer` + `RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)`.
- **Edge blending + AGSL dithering:**
  - Computes `ExpandEdgeColorInfo` along the expanded edge(s) and composites `BitmapShader` with `LinearGradient`s (`ComposeShader` using `PorterDuff.Mode.DST_IN` / `SRC_ATOP`).
  - Wraps the `ComposeShader` with a lazy **AGSL `RuntimeShader`** (`"DitheringShaderFactory"`) taking `originalShader`, `iDitheringLevel` (`0..3`), and `direction` (`triangularNoise` at level 1, 3-octave `fbm` noise at levels 2–3) to eliminate 8-bit gradient banding.
- **`com.samsung.android.wallpaper.imageprocessing.WallpaperFilter`:** JNI wrapper around `libWallpaperFilter.so` exposing `nativeStackBlur`, `nativeCreateGaussianNoiseSamples`, `nativeSetGaussianNoise`, and `nativeSetHighLightFilter`.

### 4.4 Segmentation, Clock Avoidance & `EditorStretchAreaMediatorImpl`

- **`GetObjectProcess` (`p075d7.C2998g`):** initializes `ObjectCaptureProvider.getInstance(...)` with a `CountDownLatch`, produces `C3004m` (`ObjectResult`, `Rect f7848f`, `Bitmap f7853k`, `EnumC3161d f7857o` e.g. `People`/`Animals`), and gates `"layered"` content on `ObjectCapture` support and XML existence.
- **Clock avoidance (`p043c7.C1623b0.updateViewForExpand`):** calls `findAvoidingClockRect` using `C3004m`, `Rect clockAvoidingCropRect = f17764T.getClockAvoidingCropRect()`, and animates `CropImageView` crop bounds when changed.
- **`EditorStretchAreaMediatorImpl` (`p316p8.C6027h0`) & `PreviewStretchAreaDetector` (`C5358d` / `C5357c`):**
  - Track `"cropRect"`, `"objectRegion"`, `"isFrameHidesObject"`, `"frameBasePath"`, `"frameBottomPath"`, `"exclusiveRegion"`, `"bgRegion"`, and `EnumC6023f0` blocking flags (`BLOCKED_FROM_EXTERNAL`, `BLOCKED_GET_OBJECT_PROCESS_NOT_FINISHED`, `BLOCKED_DATA_NOT_READY_FRAME_OR_OBJECT`, `BLOCKED_DATA_NOT_READY_CROP_RECT`, `BLOCKED_FRAME_CHANGE_ANIMATION`, `BLOCKED_UNSUPPORTED_WALLPAPER_CONTENTS`).
  - `p080dc.C3098u` mirrors `WallpaperObjectData.isStretchableWallpaper()` and updates `WallpaperExpandConfigData` (`clockType`, `expandableDirection`, `stretchable`, `clockBounds`, `clockGravity`).
  - `p283o3.C5685e` (`MakeStretchClearedClockDataUseCase`) resets `ClockStretchData` inside `ClockData` if `isStretchableWallpaper()` is `Boolean.FALSE`.

---

## 5. Save, Commit & ContentProvider Pipelines

### 5.1 Wallpaper Save & Commit (`C5712j` -> `C1610d` -> `C1619m` -> `C5117a`)

1. **`p285o5.C5712j.mo7837d()`:** handles `isExpanded` (`m7842e` + `EnumC1609c.ExpandedHints = "expandedHints"`), DeX (`addDexCropImageData`), AI generated (`"creativeStudio"`, `IsAiGenerated`), Weather (`IsOutdoor`, `IsNight`, `HasWeatherObject`, `WeatherId`), SmartCard/FlipSuit (`ContentOrigin="flipsuit"`, `SAContentCategory="Suitcase"`), and dispatches to `p042c6.C1610d` -> `AbstractC5703a` -> `p042c6.C1619m` (`"ImageSaveTask"`).
2. **`p075d7.C3017z` (`"SaveImageMaker"`):**
   - Writes `Layer/<which>/` files: `background.png`, `object.png`, `masked.jpg`, `single_image.jpg`, `thumbnail.jpg`, `frame_base.png`, `frame_bottom.png`.
   - Serializes `<setWallpaper.xml>` (`v1.0.3` with `LayerSpecNode`/`MaskLayerSpecNode`, `src`/`dst`, `originalImageWidth`/`Height`, `croppedLeft`/`Top`/`Right`/`Bottom`).
   - Serializes `BgRegion` into compact binary `bg_region` (12-byte header + byte flag + short/int rects).
3. **`p042c6.C1610d.mo1436e()`:**
   - Packs `ParcelFileDescriptor` handles under `"assetFiles"` (`f1858f`: `"setWallpaper.xml"`, `WallpaperAreaType.BACKGROUND`, `"background.png"`, `"object.png"`, `"masked.jpg"`, `"bg_region"`, `"thumbnail.jpg"`, `"frame_base.png"`, `"frame_bottom.png"`, `"single_image.jpg"`, `"live_effect_3d_portrait.jpg"`, `"live_effect_3d_landscape.jpg"`; `f1859g`: `"aod_background"`, `"aod_object.png"`, `"aod_ThumbOri_background.jpg"`, `"aod_ThumbOri_object.jpg"`).
   - Populates extras: `"manifest"="setWallpaper.xml"`, `"hasCroppedObject"`, `"cropHints"`, `"thumbnailFile"`, `"representativeImageFile"`, `"bgRegion"`, `"aodThumbnail"`, `"bgErasedAodThumbnail"`, `"baseOrientation"`, `"oneUiVersion"`, `"allowBackup"`.
4. **`p042c6.C1619m.m1452o()` -> `p234l5.C5117a` (`WallpaperManagerApi`):**
   - Attaches `"lockLegibilityColors"` / `"systemLegibilityColors"` (`"rotation0"`/`"rotation90"`).
   - Commits to `WallpaperManager` via `C4380f` reflection / hidden APIs: `setWallpaperComponentWithExtras` (when `componentName != null`), `WallpaperManager.setStream(..., extras, ...)`, `semSetUri`, `setVideoWallpaper`, and `setVideoLockscreenWallpaper`.

### 5.2 Lockscreen Item Save (`C3088p.mo4852Y` -> `C1923d` -> `C5685e` / `C3326d`)

- `C3088p.mo4852Y()` constructs `p178ic.C3965d` (`LockScreenSaveRuntime`) and launches `p178ic.C3963b` on `com.google.android.material.datepicker.C1923d` (`LockScreenSaveCoordinator`, producing `p178ic.C3966e` `LockScreenSaveSession` & `p178ic.C3962a` `LockScreenSaveArtifacts`), persisted by `p283o3.C5685e` (`LockScreenSavePersister`) + `eg.C3326d` (`SaveProfileDataUseCase`) and finalized by `p075d7.C2998g.m4715l` (`LockScreenSaveFinalizer`, delaying `250L` ms when `isFromLockScreen` is true).
- **`p260mf.C5456f2` (`SaveClockDataUseCase`):**
  - Calculates `ClockMarginData` (`gravity`, `horizontalMargin`, `bottomMargin`, `areaRatio`) against `calculatedMovableArea`.
  - Bundles `"clock_position_data"` (JSON), `"theme_pkg_value"`, `"show_noti_below_clock"`, and `"clock_movable_area"` (`Rect`), and persists `ClockItem` / `LockScreenItem` (`com.samsung.android.sdk.aodservice.entities.item.ClockItem`) via `InterfaceC6099h` / `InterfaceC5635b`.
- **`eg.C3326d` (`SaveProfileDataUseCase`):** persists `ProfileData`, `EntryInfo`, `WallpaperColorData`, and override areas to `profileDataStoreRepository` (skipped in DeX).

### 5.3 ContentProviders

- **`DressRoomProvider`** exposes `call` methods:
  `"custom_pack"`, `"apply_pending_custom_pack"`, `"cancel_pending_custom_pack"`, `"custom_pack_path"`, `"cover_thumb_path"`, `"apply_pending_single_wallpaper"`, `"edit_cover_wallpaper"`, `"reset_edit_info"`, `"clear_cover_wallpaper"`, `"edit_large_cover_wallpaper"`, `"set_clock_face_profile"`, `"layered_image"`, `"notification_delegate"`, and `"profile_method_*"`.
- **`ClockProvider`** serves `profile/<profileId>/lock_region` (`dataType=region`) and `informative_image_orig.png` / `informative_gif_orig.gif` (`dataType=image`).
