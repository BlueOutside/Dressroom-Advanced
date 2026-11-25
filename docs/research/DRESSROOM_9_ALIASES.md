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

## Clock ownership batch (2026-11-24)

These are JADX display aliases verified in this pass. The previous-name
column can contain JADX-generated identifiers; compare raw DEX descriptors
and call/field contracts before using any result in a runtime shim.

| Kind | JADX owner / previous name | Alias |
| --- | --- | --- |
| class | `p441vo.C7374x` | `ClockPreviewItem` |
| class | `p035bp.C1549l` | `ClockBlurCallback` |
| class | `p260mf.C5441c0` | `ClockBlurIdleStateCoordinator` |
| class | `p260mf.C5464h2` | `SetClockIdleBlurUseCase` |
| class | `p260mf.C5446d0` | `ClockMeasureStateCoordinator` |
| class | `p292oc.C5791a` | `ClockDataBindingHelper` |
| class | `p347qo.C6397a` | `BlurInfoFactory` |
| class | `p347qo.C6398b` | `BlurItemController` |
| class | `p260mf.C5517v` | `ClockPositionIdleFlow` |
| class | `p260mf.C5513u` | `ClockIdleConditions` |
| field | `p260mf.C5441c0.f16752i` | `animationSettleJob` |
| field | `p260mf.C5441c0.f16753j` | `animationIdleFlow` |
| field | `p260mf.C5441c0.f16754k` | `layoutChangingFlow` |
| field | `p260mf.C5441c0.f16755l` | `measureStateEvents` |
| field | `p260mf.C5441c0.f16761r` | `positionIdleFlow` |
| field | `p260mf.C5441c0.f16746c` | `setClockIdleBlurUseCase` |
| field | `p035bp.C1549l.f1667c` | `idleStateCoordinator` |
| field | `p292oc.C5791a.f18015a` | `boundClockView` |
| field | `p441vo.C7374x.f24100Q` | `clockBlurCallback` |
| field | `p441vo.C7374x.f24097N` | `measureStateCoordinator` |
| field | `p347qo.C6397a.f20474c` | `presetAttributesCache` |
| field | `p347qo.C6398b.f20480f` | `blurInfoFactory` |
| method | `p347qo.C6398b.m8580a` | `reconcileBlurItemViews` |
| method | `p347qo.C6398b.m8581b` | `applyBlurItem` |
| method | `p347qo.C6398b.m8582c` | `scalePreviewCoordinate` |
| method | `p347qo.C6397a.m8576a` | `buildCustomBlurInfo` |
| method | `p347qo.C6397a.m8577b` | `getCachedPresetAttributes` |
| method | `p347qo.C6397a.m8578c` | `buildCanvasPresetBlur` |
| method | `p347qo.C6397a.m8579d` | `buildWindowPresetBlur` |
| method | `p292oc.C5791a.m7990a` | `addClockBlurCallback` |
| method | `p292oc.C5791a.m7992c` | `removeClockBlurCallback` |
| method | `p292oc.C5791a.m7994e` | `setBoundClockView` |
| method | `p260mf.C5464h2.m7661a` | `setIdleBlur` |
| method | `p260mf.C5446d0.m7650a` | `notifyMeasureTrigger` |
| method | `p260mf.C5446d0.m7651b` | `updateRequestedPositionInfo` |
| method | `p260mf.C5446d0.m7652c` | `updateRequestedSize` |

## Clock ownership batch, part 2 (2026-11-24)

Continuation on Dressroom 3.0.10.38. Field aliases below come from constructor
parameter names (exact) or member bodies; see `JADX_RENAME_LOG.md` batch 19.

| Kind | Owner / old | Alias |
| --- | --- | --- |
| class | `p260mf.C5456f2` | `p260mf.SaveClockDataUseCase` |
| field | `SaveClockDataUseCase.f16836b..f16845k, f16846l, f16847m` | `clockStoreRepository`, `clockDataRepository`, `profileDataRepository`, `screenSizeRepository`, `deviceTypeRepository`, `clockAssistDataRepository`, `isThemeAppliedSettingRepository`, `clockMovableAreaRectRepository`, `clockMarginRepository`, `orientationRepository`, `tag`, `executionPhase` |
| field | `p260mf.ClockBlurIdleStateCoordinator.f16744a/f16745b/f16747d/f16748e/f16749f/f16750g/f16751h/f16756m` | `coroutineScope`, `getObservableClockDataUseCase`, `getObservablePreviewStateUseCase`, `getObservableClockAssistDataUseCase`, `getPreviewScrollUpHelperDataUseCase`, `getObservableWallpaperInfoDataUseCase`, `updateClockIdleUseCase`, `lazyClockDataFlow` |
| field | `p035bp.ClockBlurCallback.f1665a/f1666b` | `getObservableClockDataUseCase`, `getObservablePreviewStateUseCase` |
| field | `p260mf.ClockMeasureStateCoordinator.f16792a` | `clockDataObservable` |
| field | `p347qo.BlurItemController.f20475a..f20484j` | `context`, `itemDataSet`, `lifecycleOwner`, `previewViewModel`, `getPreviewScaleUseCase`, `clockBlurLayoutViewModelProvider`, `widgetBlurLayoutViewModelProvider`, `shortcutBlurLayoutViewModel`, `getObservableBlurConfigurationUseCase` |
| field | `p347qo.BlurInfoFactory.f20472a` | `getObservableBlurConfigurationUseCase` |
| field | `p260mf.ClockPositionIdleFlow.f17079a/f17080b/f17081c/f17082d` | `label`, `collector`, `clockPositionRect`, `ownerCoordinator` |
| field | `p260mf.ClockIdleConditions.f17065a..f17069e` | `animationIdle`, `layoutStable`, `measureIdle`, `positionIdle`, `scrollUp` (result = a&&b&&c&&d&&!e) |
| field | `p441vo.ClockPreviewItem.f24110a..f24154z` (35 fields) | ctor param names  -  `lifecycleOwner`, `layoutChangeListener`, `getObservableClockData`, `clockView`, `clockPreviewViewModel`, `sidePanelView`, `clockPanelViewModelProvider`, `worldClockPreviewController`, `palettePanelViewModelProvider`, `weatherInfoListener`, `uiController`, `activityRef`, `clockManagerWrapper`, `clockPreviewContainer`, `previewViewModel`, `initialized`, `getObservableScreenSize`, `screenSizeLiveData`, `saLogging`, `coroutineScope`, `initClockSizeUseCase`, …, `getThumbnailDataUseCase`, `saveMotionClockThumbnailBitmapUseCase` |
| method | `p441vo.ClockPreviewItem.m9664f / m9665g` | `isThumbnailSavePending` / `unregisterWeatherListener` |

## Self-tagged class sweep (2026-11-24, batch 20  -  74 classes)

Method: `getSubTag` search → each class's own `getTag()` literal used as the class
alias. Highlights: `p501yc.C7896t`→`ClockStoreRepositoryImpl`, `p501yc.C7892p`→`ClockPositionRepositoryImpl`,
`p481xg.C7699c`→`PreviewController`, `p075d7.C2998g`→`LockScreenSaveFinalizer`,
`cg.C1742i`→`LockScreenSaveSessionPreparer`, `ca.C1702i`→`LockScreenSaveEnvironmentPreparer`,
`eg.C3326d`→`SaveProfileDataUseCase`, `p080dc.C3059e1`→`StretchMediatorControllerImpl`,
`p080dc.C3080l1`→`WallpaperColorsControllerImpl`, `p348qp.C6415q`→`ColorOverrideAreaCoordinator`,
`sd.C6687q`→`WidgetStoreRepositoryImpl`, `sd.C6680j`→`WidgetListStoreRepositoryImpl`,
`p260mf.C5435b`→`ApplyStretchResultUseCase`, `p260mf.C5458g0`→`EnableFontStretchUseCase`,
`p260mf.C5520v2`→`UpdateClockTypeUseCase`, `p294of.C5802d`→`ClockPositionSuggestionExecutor`,
`p294of.C5803e`→`NormalPositionSuggestionConfigBuilder`, `p294of.C5806h`→`StretchPositionSuggestionConfigBuilder`,
`p437vf.C7298b`→`MakeLockStarItemsUseCase`, `p437vf.C7300d`→`UpdateLockStarItemUseCase`,
`so.C6746y`→`LockStarContentsController`, `ag.C0130j`→`PreviewSavingStateAwaiter`,
`dk.C3154c`→`RemoteDataAccessor`, `ro.C6550a`→`RemoteDataController`, …
(full 74-entry mapping in `JADX_RENAME_LOG.md` batch 20)

## Batch 21  -  p260mf use-case fields (66)
Repository/dependency fields renamed from ctor params on:
`ApplyStretchResultUseCase`, `InitClockPositionInfoUseCase`,
`ApplyWallpaperClockBundleDataUseCase` (13 repos incl. focusedWallpaperSceneRepository,
wallpaperClockBundleRepository, cachedWallpaperClockBundleRepository, clockBundleConvertRepository,
orientationRepository, wallpaperObjectDataRepository), `ResetStretchDataUseCase`,
`ChangeClockPositionInfoUseCase`, `ChangeClockSizeUseCase`, `EnableFontStretchUseCase`,
`InitClockSizeUseCase` (13 incl. previewStateRepository, clockRectOptimizerRepository,
clockDataBindingHelper, clockMeasureStateCoordinator, clockSizeConstraintRepository),
`InitClockStretchDataUseCase`, `GetExclusiveAreaUseCase`.
Naming pattern: `<domain>Repository` for interfaces, `…Coordinator`/`…Helper` for concrete helpers.
Full field list in `JADX_RENAME_LOG.md` batch 21.

## Batch 23  -  self-tag class renames (89)
Harvested via code-search for self-tag suffixes (`UseCase"`, `RepositoryImpl"`, …); full mapping in
`JADX_RENAME_LOG.md` batch 23. Highlights: cg.* widget use cases (AddWidget/RestoreWidget/SetWidgetIdleBlur…),
ad.* SubClock* repositories (position/size/type/align/margin/movable-area), be.* settings repos
(IsThemeApplied, ReduceTransparency, SupportAODFullScreen…), p080dc UiControllerImpl/LockScreenBackupAndRestore,
p066cp/p035bp world-clock preview VMs + adapter/viewholder, ShortcutViewModelFactory, CancelCustomPackHandler.

## Batch 25  -  self-tag sweep p2 (29)
p083dg editor update use cases (UpdateEditMode/IdleState/MovingData/Savable/Selected*/Touched/Resizing…),
p075d7.SaveImageMaker, p080dc.LockScreenComponent, p108ep/p164hp preview items+viewmodels,
p127fg Is*ThemeAppliedUseCase, gp CustomClock VH/adapter, p088dp.ContactInfoEditPanel.
Full list: `JADX_RENAME_LOG.md` batch 25.

## Batch 28  -  library data sources + editor panels (17)
p072d4 DataSource* family (ThemeStudio/CategoryBase/Animals/Downloaded/TiltClockWallpaper/Gallery/
ColorWallpapers/FlipCoverInformative), p098e7 EditorPanelBase + Cover/Main/GraphicalDesignPanel,
p333q7.MultiplePreviewPanelController, p043c7.ImageFilterPanel, p114f3.GeneratedWallpaperHandler,
p121fa.WallpaperProfileActionHandler, p102eb.BitmapUtils. Full list: `JADX_RENAME_LOG.md` batch 28.

## Batch 29  -  final Dressroom sweep (7)
p260mf clock-move use cases (ResetClockPositionAndApplyScale/RestoreClockData/MoveClockPosition/
UpdateScaledClockBoundary), p323po.ItemEditorController, p348qp.ClockPositionSuggestionController,
p289o9.DownloadCoordinator. p434v9/p435vb/p316p8 = synthetics only, skipped.
DRESSROOM 3.0.10.38 PASS COMPLETE  -  see HIDDEN_FEATURES.md §Dressroom for all findings.
