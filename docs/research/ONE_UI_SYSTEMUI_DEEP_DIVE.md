# One UI 9 SystemUI Deep Dive (Wallpaper, AOD Flags, Blur & Keyguard Host)

**Reference APK:** `com.android.systemui` (`AndroidManifest.xml`: `versionCode="37"`, `versionName="17"`, `compileSdkVersion="37"`; `get_package_tree`: `64,567` classes across `16,120` packages, predominantly unobfuscated)  
**Scope:** SystemUI-side wallpaper engines (`ImageWallpaper` / `WallpaperAnimator` / `ImageEngine.drawFrameOnCanvas` / `VideoEngine` / `MultipackEngine` / `CanvasEngine`), bundled `com.samsung.android.wallpaper.live.sdk` internals, AOD ambient wallpaper and window flags, `SemBlurInfo` / `SemGfxImageFilter` blur architecture, `PluginKeyguardStatusView` / `PluginAODManager` host bridge, `KeyguardTouchAnimator` view hierarchy, Unlocked Screen-Off / AOD ↔ Lockscreen transition timing, and Lockscreen/AOD Notification / NIO / NowBar / Media Z-order, positioning & blur.  
**Related docs:** [ONE_UI_CLOCK_DEEP_DIVE.md](ONE_UI_CLOCK_DEEP_DIVE.md), [ONE_UI_WIDGET_DEEP_DIVE.md](ONE_UI_WIDGET_DEEP_DIVE.md), [ONE_UI_DEPTH_DEEP_DIVE.md](ONE_UI_DEPTH_DEEP_DIVE.md).

---

## 1. Built-In Wallpaper Scaling in SystemUI (`ImageWallpaper` + `WallpaperAnimator`)

Unlike third-party or live/AI wallpapers hosted in `com.samsung.android.wallpaper.live`, standard still/built-in lockscreen wallpapers run **inside `com.android.systemui`**:

- **`com.android.systemui.wallpapers.ImageWallpaper`** extends `com.samsung.android.wallpaper.live.sdk.service.LiveWallpaperService`:
  - `ImageWallpaper.IntegratedEngine` receives `KeyguardWallpaperController` messages in `C96561.onEventReceived(int, Bundle)` and wraps sub-engines (`ImageEngine`, `VideoEngine`, `GifEngine`, `MultipackEngine`, `TransitionEngine`, `AnimatedEngine`, `MotionEngine`).
  - **`com.android.systemui.wallpaper.engines.image.ImageEngine`** owns `mWallpaperAnimator = new WallpaperAnimator(getSurfaceHolder(), getSurfaceControl(), keyguardUpdateMonitor)` and forwards `onDisplayStateChanged(...)`, `onVisibilityChanged(...)` (`WallpaperUtils.isShowWallpaperOnAodEnabled(getWhich() & 60) && mDozeParameters.mControlScreenOffAnimation`), and `onCommand(...)` (`"samsung.android.wallpaper.resume"` / `"android.wallpaper.keyguardgoingaway"`).
- **`com.android.systemui.wallpaper.engines.WallpaperAnimator`** (`ImageWallpaper[WallpaperAnimator]`):
  - Animates `mCurrentScale` between **`1.0f`** and **`1.025f`** with duration **`600L`** ms and interpolator `new PathInterpolator(0.17f, 0.17f, 0.4f, 1.0f)`.
  - **`onDisplayStateChanged(displayState, which, isFullAodShown, isMultipack, z3)`** & **`onEngineVisibilityChanged(...)`**:
    - Sets `mKeyguardState = true` whenever `displayState != DisplayState.ON`.
    - When `isMultipack == true`: `DisplayState.ON` triggers `reserveDownScaleAnimation()` (`240L` ms delay → `600L` ms `1.025f → 1.0f`), while `DisplayState != ON` triggers `playUpScale()` (`600L` ms `1.0f → 1.025f`).
    - When `isMultipack == false` and `WhichChecker.isSystemAndLock(which)`: `DisplayState != ON` with `!isFullAodShown` calls `changeToUpScaleImmediately()` (`1.025f`); when engine becomes visible (`onEngineVisibilityChanged`), `playDownScale()` runs (`600L` ms `1.025f → 1.0f`), and `"samsung.android.wallpaper.resume"` in `ImageEngine.onCommand` snaps `1.0f` immediately.
  - **`onTransitionScaleChanged(float f)`** sets `f2 = 1.0f - f`, insets/expands `surfaceFrame` on all four sides (`left + width * f2`, `top + height * f2`, `right - width * f2`, `bottom - height * f2`), and calls `scaleSurface(Rect)` which applies:
    ```java
    transaction.setGeometry(this.mSurfaceControl, surfaceFrame, rect, 0).apply();
    ```
    Because both left/right and top/bottom shift by `(1.0f - f) * dim`, `f = 1.025f` expands `rect` symmetrically by `-2.5%` per edge (**+5% net visual scale** centered on `surfaceFrame`). This changes the `SurfaceControl` geometry directly and leaves the `Canvas` matrix alone.
- **Comparison with Dressroom Advanced (`WallpaperMotionPublisher` / `WallpaperMotionBinding`)**:
  - [WallpaperMotionPublisher.java](../../app/src/main/java/dev/denver/dressroomadvanced/depth/WallpaperMotionPublisher.java) + [WallpaperMotionBinding.java](../../app/src/main/java/dev/denver/dressroomadvanced/compat/shared/WallpaperMotionBinding.java) currently hook `"drawRenderNodes: durDraw="` and `"draw: drawable is null"` / `SwitchableWallpaperService$DelegatorEngine.onCommand` inside `com.samsung.android.wallpaper.live` only.
  - Built-in `ImageWallpaper` / `ImageEngine` transitions driven by `WallpaperAnimator` via `SurfaceControl.Transaction.setGeometry(...)` bypass those Canvas hooks. The module observes the image engine's rectangle updates in SystemUI and applies them only when the saved wallpaper identity, display and viewport aspect match. The native hook shape was checked on the reference One UI 9 and P613 One UI 8.5 APKs. Device alignment is still pending.

---

## 2. AOD Wallpaper Display Monitors & Window Flag Control

- **`LiveWallpaperService.BaseEngine.onCreate`** selects a `DisplayStateMonitor` based on `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN` and floating AOD flags:
  - **`FullAodDisplayStateMonitor`**: reads `SettingsHelper.INDEX_AOD_SHOW_LOCKSCREEN_WALLPAPER` (returning `DisplayState.ON`, `DisplayState.AOD_WITH_WALLPAPER`, `DisplayState.AOD_WITHOUT_WALLPAPER`, or `DisplayState.OFF`) and reacts to `"android.wallpaper.goingtosleep"`, `"samsung.android.wallpaper.goingtosleep"`, and `"android.wallpaper.wakingup"`.
  - **`SeamlessAodDisplayStateMonitor`**: tracks integer `mAodState` (`2` -> `AOD_WITH_WALLPAPER`, `1` -> `AOD_WITHOUT_WALLPAPER`) via `"android.wallpaper.aodstate"` and `"android.wallpaper.wakingup"`.
  - Also selects `BasicAodDisplayStateMonitor` or `NoAodDisplayStateMonitor` when appropriate.
- **`com.android.systemui.aod.AODAmbientWallpaperHelper`**:
  - `isAODFullScreenMode()` checks `LsRune.AOD_FULLSCREEN && settingsHelper.isAODEnabled() && settingsHelper.isAODShowLockWallpaper()` (plus `AOD_SUB_FULLSCREEN` / `AOD_MAIN_FULLSCREEN` fold state via `KeyguardFoldController`).
  - `getAlpha()` returns **`0.6f`** when `isAODFullScreenMode()`, **`0.0f`** for WonderLand ambient wallpaper (`WonderLandWallpaperReloadedService` / `WonderLandWallpaperReloadedSubService`), **`0.1f`** in factory binary, and **`1.0f`** otherwise, applying it to `LightRevealScrim.setAlpha(...)`.
  - On `WakefulnessLifecycle.Observer.onStartedGoingToSleep()` / `onStartedWakingUp()`, calls `StatusBarWindowControllerImpl.setAODAmbientWallpaperState(false/true)` (toggling `layoutParams.samsungFlags & ~262144`) and updates `LightRevealScrim` alpha.
- **`com.android.systemui.shade.SecNotificationShadeWindowControllerHelperImpl.applyHelper`**:
  - Invokes `pluginAOD.applyAODFlags(layoutParamsChanged2, dozing, isCoverClosed)`.
  - Controls `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER` (`1048576`) on the NotificationShade/Keyguard window using `aodAmbientWallpaperHelper.isAODFullScreenMode()`, `fastUnlockController.isWakeAndUnlockAnimationAODFullScreenMode()`, `settingsHelper.isUltraPowerSavingMode()`, `settingsHelper.isEmergencyMode()`, and `needWallpaperForUnlockAnimation`.
- **`aod_erase_background`, `semGetWallpaperBackgroundRegion`, `stretch_ui` absences in SystemUI**:
  - `"aod_erase_background"` returned `0` matches in `com.android.systemui.util`.
  - `"semGetWallpaperBackgroundRegion"` returned `0` matches in `com.android.systemui.wallpaper`, and class search for `Stretch` found no Keyguard/Clock stretch classes (`stretch_ui` lives in `aodservice`, while `KeyguardWallpaperController` maintains `TouchRegionManager` `RegionRecord(bounds, purpose)` consumed by `PluginAODManager` when `(purpose & 1) != 0`).

---

## 3. Blur Architecture (`SemBlurInfo`, `SemGfxImageFilter` & Plugin Blur)

- **`com.android.systemui.blur.BouncerColorCurve`**: Reads Samsung blur presets via `SemBlurInfo.Builder.getBlurPresetAttrs(int)` (`131` / `137` / `103` / `122` / `118`) and computes `(mRadius, mSaturation, mCurve, mMinX, mMaxX, mMinY, mMaxY)`. Used both by `SecNotificationShadeWindowControllerHelperImpl.applyBouncerWindowBlur(...)` (`SemBlurInfo.Builder(0).setColorCurve(...)` + `BLUR_AMOUNT = 0.07f` optical FP / `0.1375f` non-optical) and by `SecCapturedBlurBitmapGenerator`.
- **`com.android.systemui.blur.QSColorCurve`**: Defines Quick Panel blur curves (`radius` `300.0f` / `250.0f` / `70.0f`, `saturation` `0.25f` or `0.45f`/`0.65f` popover, `curve` `10.0f` or `20.0f`/`-15.0f` popover, `minX`, `minY`, `maxX`, `maxY`).
- **`com.android.systemui.blur.domain.interactor.SecPanelWindowBlurInteractor`**: Builds `SemBlurInfo.Builder(0)` with `setBGColorAdjustment(0.1f, 0.18f, ...)` when background is hidden, and `setRadius(shouldBlockWindowBlur ? 0 : radius)`.
- **`com.android.systemui.blur.domain.interactor.SecCapturedBlurBitmapGenerator`**: Captures window or wallpaper screenshot (`ScreenShotBitmapFactory`) and applies `SemGfxImageFilter` with `QSColorCurve` (`QUICK_PANEL`) or `BouncerColorCurve` (`BOUNCER`, `FULL_SCREEN`, `ALT_VIEW`).
- **`com.android.systemui.statusbar.BlurUtils`**: Computes `blurRadiusOfRatio(float)` via `MathUtils.lerp(minBlurRadius, maxBlurRadius, f)`, checks `supportsBlursOnWindows()`, and dispatches `SurfaceControl.Transaction.setEarlyWakeupStart(EarlyWakeupInfo)`.
- **`com.android.systemui.facewidget.plugin.PluginFaceWidgetManager`** (handling `PluginKeyguardStatusView.Callback` v`4022`):
  - `applyBlur(int)` drives `SecQpBlurController.doBlur(i, BlurType.FULL_SCREEN)` when `LsRune.SECURITY_CAPTURED_BLUR` is enabled.
  - `applyBlur(View, int)` sets `view.setBackgroundDrawable(secCapturedBlurBitmapGenerator.getBlurredBitmapWithEffect(BlurType.ALT_VIEW))`.

---

## 4. Keyguard Plugin Bridge & Touch/Affordance View Map

- **`PluginKeyguardStatusView`** (`ACTION = "com.samsung.systemui.action.PLUGIN_FACE_WIDGET"`, `VERSION = 4022`) + **`FaceWidgetContainerWrapper`**:
  - Exposes `attachFaceWidgetContainer`, `getContainerView()`, `getContentsContainers()` (`mClockContainer = get(0)`), `getPositionAlgorithm()`, `createFullNowBar`, `destroyFullNowBar`, `setFullNowBarProgress`, `onScreenOnOffAnimationStart`, `onScreenOnOffAnimationEnd`, `setDarkAmount` / `setDarkAmounts`, `dozeTimeTick`, and `onNioLayoutUpdated`.
- **`com.android.systemui.keyguard.animator.KeyguardTouchAnimator`** view index map:
  - `0`: `mKeyguardStatusBar`
  - `1`: `mFaceWidgetContainer` / `mClockContainer` (`mKeyguardStatusBase`)
  - `2`: `mNotificationStackScrollLayoutController.mView`
  - `3`: `mLockscreenNotificationIconsOnlyController.getIconContainer()`
  - `4` / `5` / `6`: `mKeyguardSecBottomArea` `leftView` / `rightView` / `indicationArea`
  - `7`: `mStatusBarKeyguardViewManager.getLockIconContainer()`
  - `10`: `mPluginLockStarContainer`
  - `11`: `R.id.keyguard_edit_mode_blur_effect`
  - `12`: `mNowBarContainer`
  - `13`: `mContinuityLockScreenContainer`
  - `14`: `provideComplication()` (widgets/complications container)
- **`com.android.systemui.keyguard.animator.ScreenOnAffordanceViewController`** & pre-wake scale:
  - `NotificationPanelViewController.resetViews(z, true)` → `KeyguardTouchAnimator.reset(true)` → `resetChildViewVI(0.95f)` (`m326x304bee4b(0.95f)`) pre-scales keyguard animated views **except index `0` (`mKeyguardStatusBar`) and `3` (notif icons)** to `scaleX = scaleY = 0.95f, alpha = 1.0f`.
  - Observes `PowerInteractor.screenPowerState`: when `AODParameters.mDozeUiState` is **false** (standard Screen-Off → Lockscreen wake), `ScreenPowerState.SCREEN_ON` fires `pluginKeyguardStatusView.onScreenOnOffAnimationStart()` and runs `SpringAnimation(SCALE_X/Y)` (`150.0f, 1.0f`) from `0.95f → 1.0f` across view indices `[7, 1, 2, 4, 5, 6, 12, 14, 9, 10]`.
  - When `AODParameters.mDozeUiState` is **true** (waking with Doze/AOD UI active), it skips the spring animation on `SCREEN_ON` (`"Do not animation in dozeUiState"`) and snaps views to `1.0f` immediately via `resetChildViewVI(1.0f)`.

---

## 5. Unlocked Screen-Off, Light Reveal & AOD ↔ Lockscreen Timing

### Unlocked Screen-Off Timeline (`AOD_FULLSCREEN`)
- **`SecUnlockedScreenOffAnimationHelper.onStartedGoingToSleep()`**: Sets `deviceInteractive = false`. If `!aodAmbientWallpaperHelper.isAODFullScreenMode()` or `!shouldPlayUnlockedScreenOffAnimation()`, immediately calls `playWallpaperAnimation()`; otherwise hides nav bar (`updateNavigationBarVisibility(false)`).
- **`t = 0 ms` (`UnlockedScreenOffAnimationController.startAnimation()`)**:
  - Starts `lightRevealAnimator` (`1.0f → 0.0f`, `Interpolators.LINEAR`) for **`650L` ms** (`500L` ms non-fullscreen) and sets `shouldAnimateInKeyguard = false`, `lightRevealAnimationPlaying = true`.
  - `onAnimationStart`: sets `needUpdateSetLockScreenShown = true`, calls `updateSetLockScreenShown(false)`, and triggers `playWallpaperAnimation()` (`semSendWallpaperCommand(..., "samsung.android.wallpaper.goingtosleep", ...)`).
- **`t = 300 ms` (`300L * animatorDurationScale`)**:
  - Fires **`NotificationPanelViewController.showAodUi()`** while `lightRevealAnimator` still has **`350 ms`** remaining (`600L` ms delay in non-fullscreen mode):
    1. Calls `setDozing(true, false)` (immediate `dozeAmount = 1.0f`, skipping `mDarkAnimator`).
    2. Sets `StatusBarState` to `1` (`KEYGUARD`) and dispatches `onDozeAmountChanged(1.0f, 1.0f)`.
    3. Calls `PluginAODManager.setIsDozing(true, false)` when `LsRune.AOD_FULLSCREEN && shouldControlScreenOff()`.
- **`t = 650 ms` (`onAnimationEnd`)**: Clears `lightRevealAnimationPlaying`, switches touch mode (`setTouchMode(DOUBLE)` if `AOD_TSP_CONTROL && isAODFullScreenMode()`), calls `pluginAOD.onUnlockedScreenOffAnimationEnd()`, `keyguardViewMediator.maybeHandlePendingLock()`, and `centralSurfaces.updateIsKeyguard(false)`.
- **Wake interruption (`SecUnlockedScreenOffAnimationHelper.onStartedWakingUp()`)**: Sets `deviceInteractive = true`, cancels coroutine `job` and `updateSetLockScreenShownRunnable`, and if `needUpdateSetLockScreenShown` was set, resets it and calls `updateSetLockScreenShown(true)` → `KeyguardViewMediator.setDozing(false)`.

### `LightRevealScrim` Curves, Max Alpha & Clock `setDarkAmounts` Handoff
- **`LightRevealScrimInteractor`**:
  - `maxAlpha` emits **`0.6f`** when `LsRune.AOD_FULLSCREEN && (aodAmbientWallpaperHelper.isAODFullScreenMode() || aodAmbientWallpaperHelper.isWonderLandAmbientWallpaper())` (preserving ambient wallpaper visibility underneath), vs **`1.0f`** otherwise.
  - `listenForStartedKeyguardTransitionStep`: **`650 ms`** when `from == KeyguardState.GONE` (`AOD_FULLSCREEN`), **`0 ms`** when `to == AOD` from `FOLD` or when `disableLightRevealScreenOn()` returns true (keyguard hidden/occluded, wake reason `103` / `9` / `12`, or folded without AOD shown), and **`500 ms`** otherwise (`FromAodTransitionInteractor`: `500 ms` to `LOCKSCREEN`/`GONE`/`PRIMARY_BOUNCER`, `550 ms` to `OCCLUDED`; `FromDozingTransitionInteractor`: `500 ms` default, `300 ms` `TO_DREAMING`, `200 ms` `TO_OCCLUDED`).
  - When `LsRune.AOD_FULLSCREEN && from == KeyguardState.OFF`, skips starting `revealAmountAnimator`; `listenForIsAwake` snaps `revealAmount = 1.0f` only if `!pluginAODManager.shouldControlScreenOff() && !dozeParameters.mAODParameters.mDozeUiState`.
- **`SecLightRevealScrimHelper`**:
  - `SecCircleReveal`: applies `SEC_LIGHT_REVEAL_INTERPOLATOR = PathInterpolator(0.45f, 0.18f, 0.35f, 1.0f)` with threshold `0.5f` (`1.0f - getPercentPastThreshold(interpolated, 0.5f)` for `revealGradientEndColorAlpha`).
  - `updateLinearRevealAmount`: drives `revealDimGradientEndColorAlpha` via `FAST_AOD_ENTRY_DIM_INTERPOLATOR = PathInterpolator(0.22f, 0.74f, 0.46f, 0.93f)` when `useFastAODEntryDim == true` (entering `DOZING`/`AOD`/`OFF`), and linear `1.0f - linearRevealAmount` when revealing (`useFastAODEntryDim == false`).
  - **Clock dark-amount ownership**: when `LsRune.AOD_LIGHT_REVEAL` is true, `SecLightRevealScrimHelper` collects `LightRevealScrimViewModel.revealAmount` to drive `PluginAOD.onUnlockedScreenOffAmountChanged(1 - revealAmount)`, `FaceWidgetContainerWrapper.setDarkAmounts(1 - linearRevealAmount, 1 - revealAmount)`, and `PluginLockStar.setDarkAmount(1 - revealAmount)`. In this mode, `NotificationPanelViewController.onDozeAmountChanged()` skips `setDarkAmounts(...)`.

### `StatusBarStateControllerImpl` Dark Animator & `AODOverlayContainer` Handoff
- **`NotificationPanelViewController.setDozing(boolean z, boolean z2)`**:
  - Chooses `mDozeInterpolator`: **`Interpolators.FAST_OUT_SLOW_IN`** when entering doze (`mIsDozing == true`), **`Interpolators.TOUCH_RESPONSE_REVERSE`** when exiting (`mIsDozing == false`).
  - When waking with `z2 == true` from `f == 1.0f` (`!mIsDozing`), immediately dispatches `setDozeAmountInternal(0.99f)` on frame 0 before starting `mDarkAnimator`.
  - `StatusBarStateControllerImpl.createDarkAnimator()` runs a **`500L` ms** `Interpolators.LINEAR` `ObjectAnimator` on `SET_DARK_AMOUNT_PROPERTY`, passing `(mDozeAmount, mDozeInterpolator.getInterpolation(mDozeAmount))` to all listeners (`NotificationWakeUpCoordinator` derives notification hide amount from `min(1 - visibilityAmount, outputDozeAmount)`, with `333L`/`500L` pulsing wake animators).
- **`PluginAODManager.setIsDozing(boolean z, boolean z2)`**:
  - Sets `AODOverlayContainer` to `View.GONE` (`8`) immediately on wake unless `z2 && !settingsHelper.isAODShown()` (Wake-on-Fingerprint / WOF without prior AOD: `"AODOverlayContainer is needed when wof. It will be gone by aodservice."`).
  - In `isFastWakeAndUnlockMode()` without fullscreen W&U VI and `!isAODShown()`, sets `SecLsScrimControlHelper.setFrontScrimToBlack(true)`.

### Dressroom Advanced Implications
- **Built-in vs live wallpaper motion tracking**: `WallpaperAnimator`'s `1.025f ↔ 1.0f` (`600 ms` / `240 ms + 600 ms`) display/visibility transitions use `SurfaceControl.Transaction.setGeometry(...)` without invoking `ImageEngine`'s `draw(Canvas)` path observed by [WallpaperMotionPublisher.java](../../app/src/main/java/dev/denver/dressroomadvanced/depth/WallpaperMotionPublisher.java) (`100 ms` `settled` wake pump) and [WallpaperMotionBinding.java](../../app/src/main/java/dev/denver/dressroomadvanced/compat/shared/WallpaperMotionBinding.java).
- **Clock/wallpaper opposing motion on Screen-On**: when waking from non-doze Screen-Off, lockscreen clock index `1` springs **up** (`0.95f → 1.0f`, `stiffness = 150.0f, dampingRatio = 1.0f`) while wallpaper `WallpaperAnimator` scales **down** (`1.025f → 1.0f`) alongside the `500 ms` `TOUCH_RESPONSE_REVERSE` dark fade; when waking from Doze UI (`mDozeUiState == true`), the clock snaps immediately to `1.0f` on `SCREEN_ON`.

---

## 6. Lockscreen & AOD Notification / NIO / NowBar / Media Z-Order, Positioning & Blur

### View Hierarchy & Z-Order
- **`res/layout/super_notification_shade.xml` (`NotificationShadeWindowView @+id/legacy_window_root`, bottom → top draw order):**
  1. `ImageView @+id/fake_wallpaper` (`gone`), `View @+id/communal_ui_stub` (`gone`)
  2. `CapturedBlurContainer @+id/captured_blur_container` (`gone`, alpha `0`), `SecPanelBackground @+id/notification_panel_background` (`gone`, alpha `0`)
  3. `ScrimView @+id/scrim_behind`, `ScrimView @+id/scrim_notifications` (`gone`)
  4. `FrameLayout @+id/lock_clock_blur` (`gone`), `FrameLayout @+id/nowbrief_lock_blur` (`gone`)
  5. `LightRevealScrim @+id/light_reveal_scrim`
  6. `<include layout="@layout/status_bar_expanded"/>` (`NotificationPanelView @+id/notification_panel`)
  7. `KeyguardRootView @+id/keyguard_root_view`
  8. `SharedNotificationContainer @+id/shared_notification_container` (`android:translationZ="1dp"`)
  9. `FrameLayout @+id/now_bar_rootview` (`android:layout_gravity="bottom"`, bound as `NotificationPanelViewController.mNowBarContainer` and exposed to FaceWidget via `FaceWidgetNotificationControllerWrapper.getNowBarRootView()`)
  10. `FrameLayout @+id/nowbrief_lock_vi` (`gone`) → `ScrimView @+id/scrim_in_front` → `keyguard_message_area_container` → `keyguard_bouncer_container` → `multi_shade_stub` → `low_light_animation_container` → `AuthRippleView @+id/auth_ripple` → `keyguard_mdm_overlay_container` → `keyguard_continuity_lockscreen_affordance_area` (`bringToFront()` in `NotificationPanelViewController`).
- **`res/layout/status_bar_expanded.xml` (`NotificationPanelView @+id/notification_panel`, bottom → top draw order):**
  - Index `0`: `@+id/keyguard_edit_mode_container`.
  - Index `1` (runtime-inserted in `PluginFaceWidgetManager` at `mView.indexOfChild(mEditModeContainer) + 1`): `FaceWidgetContainerWrapper.mFaceWidgetContainer` (Clock + FaceWidgets + NIO `iconContainer`).
  - `@+id/lockstar_view_container_stub` → `keyguard_long_press` → `plugin_frame`.
  - `NotificationsQuickSettingsContainer @+id/notification_container_parent` containing `qs_gradation_view`, `dock_info_overlay`, `sec_qs_new_blur_view`, `QSTouchAwareFrameLayout @+id/qs_frame` (`elevation="4dp"`), `NotificationStackScrollLayout @+id/notification_stack_scroller` (`res/layout/notification_stack_scroll_layout.xml`), `qs_detail` / `qs_detail_compose` / `qs_customizer_main`, `photo_preview_overlay`, `KeyguardStatusBarView @+id/keyguard_header` (`res/layout/keyguard_status_bar.xml` → `keyguard_carrier_text_nio_container` + `keyguard_notification_icon_container`), and `TapAgainView @+id/shade_falsing_tap_again` (`elevation="4dp"`).
  - `dock_info_bottom_area_overlay` → `SecLockIconView @+id/sec_lock_icon_view` → `keyguard_punch_hole_vi_view_stub` (`bringToFront()`) → `keyguard_fingerprint_guide_popup_stub` (`bringToFront()`) → `keyguard_bottom_area` → `keyguard_charging_button` → `@+id/aod_overlay_container` (`AODOverlayContainer`, inflated via `PluginAODManager.initAODOverlayContainer()`, hidden `View.GONE` via `setIsDozing(false)` on wake) → `keyguard_bottom_area_switcher`.
- **Key ordering consequences:** Both `NotificationPanelView` (clock + NSSL + AOD overlay) and `now_bar_rootview` sit **above** `LightRevealScrim` and `scrim_behind`; within `NotificationPanelView`, `NotificationStackScrollLayout` draws above `FaceWidgetContainer` (index `1`); and `now_bar_rootview` is a higher sibling in `NotificationShadeWindowView` rather than a child of `NotificationPanelView`.

### Positioning Pipeline (Clock, Notifications & NIO)
- **`NotificationPanelViewController.positionClockAndNotifications()`** (via `NotificationPanelViewController$$ExternalSyntheticLambda51` case `0`):
  - Computes `ClockSize.SMALL` when `mActiveNotificationsInteractor.getAreAnyNotificationsPresentValue() || mMediaDataManager.hasActiveMedia()`, else `ClockSize.LARGE`.
  - Calls `FaceWidgetPositionAlgorithmWrapper.setup(...)` / `.run(...)` (`PluginSecKeyguardClockPositionAlgorithm`) with dark amount `1.0f - mCurrentRevealAmount` during `mScreenOffAnimationController.shouldExpandNotifications() && LsRune.AOD_LIGHT_REVEAL`, else `mInterpolatedDarkAmount`.
- **NSSL padding (`getKeyguardNotificationStaticPadding()` & `requestScrollerTopPaddingUpdate()`):**
  - Top static padding reads `mClockPositionAlgorithm.getLockscreenNotifPadding()` (fallback `mClockPositionResult.stackScrollerPadding`, plus `DcmMascotViewContainer` height when `CscRune.KEYGUARD_DCM_LIVE_UX`).
  - On keyguard (`mBarState == 1 && !mAmbientState.mDragDownOnKeyguard`), top padding interpolates `MathUtils.lerp(staticPadding, mQsController.mMaxExpansionHeight, mQsController.computeExpansionFraction())` and writes `AmbientState.mTopPadding` + `SharedNotificationContainerInteractor._stackScrollerPadding`.
  - Keyguard bottom padding (`NSSL.mKeyguardBottomPadding`) comes from `PluginSecKeyguardClockPositionAlgorithm.getBottomMarginY()`, plus DCM mascot / USIM text, `Math.max`'d with `viewHeight * mNotiCardBottomPaddingRatio` (or `mNotiCardBottomPaddingLandRatio`) when `PluginLockMediator.isDynamicLockEnabled()`.
- **NIO Status-Bar Metrics (`KeyguardStatusBarNioLayoutRepository` & `LockscreenNotificationIconsOnlyController`):**
  - `KeyguardStatusBarViewController` binds `R.id.keyguard_carrier_text_nio_container` (`keyguard_status_bar.xml`) into `KeyguardStatusBarNioLayoutRepository.nioContainerView`.
  - Tracks `IndicatorGardenModel` (`paddingLeft`, `paddingRight`, `totalHeight`, `cameraTopMargin` → `topMargin`, `cameraBottomMargin` → `bottomMargin`, `maxWidthLeftContainer` → `containerEndX`, `containerStartX`), `IndicatorScaleGardener` (`iconSize`, `iconScaleRatio`), `numberOfNio`, `isMusicShown`, and Settings (`isShowNotificationOnKeyguard`, `isNotificationIconsOnlyOn`), forwarding updates via `PluginFaceWidgetManager.onNioLayoutUpdated(...)`.
  - `positionClockAndNotifications()` invokes `pluginNotificationController.updateNotificationIconsOnlyPosition()` whenever `(mDozing && mDozeParameters.getAlwaysOn()) || LockscreenNotificationManager.isNotificationIconsOnlyShowing() || (mCurrentNotificationType == 0 && mSettingNotificationType == 1)`.

### Notification Modes, Media Routing & NowBar Coordination
- **Lockscreen notification style (`SettingsHelper.INDEX_LOCKSCREEN_MINIMIZING_NOTIFICATION = "lockscreen_minimizing_notification"`):**
  - `0` = Cards (`isNotificationAsCard()`), `1` = Icons Only / NIO (`isNotificationIconsOnlyOn()`, default `1`), `2` = Dot (`isNotificationAsDot()`).
  - `LockscreenNotificationManager.updateNotificationType()` remaps `0`/`1` → `3` (landscape) or `1` (portrait), and `2` → `4` (landscape) or `2` (portrait) whenever **`mMusicItemExpanded`** is true OR `(mIsKeyguardSupportLandscapePhone && mCurrentOrientation == 2 && !mIsFolderStateOpen)`.
- **Filter & visibility split (`LockScreenNotiIconCoordinator`):**
  - When `mUpcomingState == 1`, `shouldFilterOut()` filters `Notification.CallStyle`, **Promoted Ongoing Activities** (`isOngoingActivity() && isPromotedState()`, setting `row.mSkipRemovalAnim = true`), and `isInsignificant()` unless `AmbientState.isNeedsToExpandLocksNoti()` holds (`!isNotificationAsCard()`, landscape phone/non-tablet, or `mPluginLockMode == 1`).
  - Remaining entries populate `LockscreenNotificationInfo` (`what = 101` → `LockscreenNotificationIconsOnlyController.onNotificationInfoUpdated()` → `PluginNotificationController` + `nioLayoutModel.numberOfNio`). When `isNotificationIconsOnlyShowing()` (`1..4`) and `!mAmbientState.mDragDownOnKeyguard`, NSSL hides card rows (`setVisibility(8)`).
- **Media (`KeyguardMediaController` / `MediaHierarchyManager` / NowBar):**
  - `KeyguardMediaController` initializes AOSP `MediaHost` at lockscreen location `2` (`mediaHost.init(2)`); `MediaHierarchyManager.calculateLocation()` only routes to location `2` when `!bypass && statusbarState == 1 && allowMediaPlayerOnLockScreen` (`"media_controls_lock_screen"`).
  - `FaceWidgetNotificationControllerWrapper` bridges `MediaDataManager.Listener` (`onMediaDataLoaded`/`Removed`) and `OngoingActivityDataHelper` (`onlyShownNowbarItemMap`, `mediaOngoingData`) to `PluginNotificationController` (`NowBarItem` / `PluginFaceWidgetMediaData` rendered inside `now_bar_rootview`), while `NSSL` hosts `SecMediaHost` (`MediaType.ENR` / `MediaType.OA`).
  - **`FaceWidgetKeyguardStatusCallbackWrapper.onMusicItemExpaned(boolean z)`** (triggered when Music NowBar expands/collapses):
    1. Sets `NPVC.mMediaNowBarExpandState = z ? 1 : 0` and `nioLayoutModel.isMusicShown = z`.
    2. Sets `LockscreenNotificationManager.mMusicItemExpanded = z` → runs `updateNotificationType()` (collapsing Card mode into NIO).
    3. Animates `NPVC.mStackScrollerAlphaAnimator` (`1 → 0` in `150L` ms expanding; `0 → 1` in `300L` ms with `150L` ms delay collapsing, `InterpolatorUtils.SINE_OUT_60`). Returning to keyguard with `mMediaNowBarExpandState == 1` enforces `NSSLController.setMaxAlphaForKeyguard("MediaNowBar keyguardShowing", 0.0f)`.
- **NowBar visibility & alpha sync (`NPVC`):**
  - Visibility is set via `PluginFaceWidgetManager.updateNowBarVisibility(mBarState == 1 ? 0 : 8)` (`updateDozingVisibilities`, `setKeyguardSecBottomAreaVisibility`, `resetDynamicLock`, `setFullScreenMode`); occlude transitions use `SecNowBarOccludeAnimationController`.
  - Alpha uses `NPVC.getFaceWidgetAlpha()` (`-1f` if `mKeyguardTouchAnimator.isViRunning() || mBouncerShowing`, `1 → 0` over `fractionToShade` `0..0.5`, `1f` in edit mode), synced in lockstep across `mNowBarContainer`, `mKeyguardSecBottomAreaViewController`, `mLockIconViewController`, `mContinuityLockScreenContainer`, and `mPluginLockStarContainer`.

### Blur & AOD (`mDozeAmount`) Card Behavior
- **Shade / panel blur vs Unlocked Screen-Off:** `SecQpBlurController.getCalculatedFraction()` returns **`0.0f`** whenever `UnlockedScreenOffAnimationController.lightRevealAnimationPlaying` is true; shade/bouncer blur applies `SemBlurInfo` on `NotificationShadeWindowView` (`SecPanelWindowBlurInteractor`/`Binder`) and captured/RenderEffect blur on shade containers.
- **Per-Notification Card Window Blur (`NotiRune.NOTI_STYLE_ENR_WINDOW_BLUR`):**
  - Requires `NotiRune.NOTI_ONEUI_8_5 && SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_3D_SURFACE_TRANSITION_FLAG`; disabled for colorized/custom (non-ongoing)/media notifications, Ultra Power Saving, OpenTheme/Theme Designer, Reduce Transparency, `mPluginLockMode == 1`, or DLS flag bit `16` on keyguard (`NSSLController.updateDlsStateBlur()`). (`SettingsHelper.getLockNoticardOpacity()` returns `80` in dark mode, `92` in light mode; `ActivatableNotificationView.usesTransparentBackground()` returns `mIsBlurSupported && !mOnKeyguard`.)
  - `ActivatableNotificationView.initBlurBackground()` configures `SemBlurInfo.Builder(0)` onto `mBackgroundNormal.semSetBlurInfo(...)` (`BackgroundBlurDrawable`, with `ViewRootImpl.setBlurAfterPreDraw(true)` in `NSSL`), and `NSSL.setAlpha` syncs blur alpha via `setBlurAlphaFromParent(fMin)` → `updateBlurDrawableAlpha(...)`.
  - **Luminance-adaptive lockscreen preset:** When `mOnKeyguard && mDimmed`, `SharedNotificationContainerBinder` pushes `lockscreenLuminanceFlow` → `AmbientState.mLockscreenNotificationAreaLuminance` → `ActivatableNotificationView.updateBlurColorCurve()` → `noticolorpicker.NotificationColorPicker.getColorCurvePreset(luminance)`:
    - `0.0 <= d <= 23.0`: dark mode `129`, light mode `114` (overriding `[2] = 60.0f`, `[5] = 181.7f`, `[6] = 245.4f`).
    - `23.0 < d <= 64.0` or `d < 0.0`: dark mode `124`, light mode `114`.
    - `d > 64.0`: dark mode `123`, light mode `113`.
    - Whenever `d >= 0.0`, blur radius `preset[0]` is overridden to **`180.0f`**.
- **AOD fade multiplier (`NotificationStackScrollLayout`):** On keyguard, non-heads-up notification rows and `NotificationShelf` fade out with `1.0f - MathUtils.constrain(ambientState.mDozeAmount * 3.0f, 0.0f, 1.0f)` (reaching `alpha = 0` by `dozeAmount == 0.333f`), while heads-up rows use `1.0f - ambientState.mHideAmount`.

### Additional Dressroom Advanced Implications
- **Compositing ceiling below `NotificationShadeWindowView`:** Because `WallpaperService` sits beneath `NotificationShadeWindowView`, a wallpaper-side foreground cannot draw over `FaceWidgetContainer`, `NotificationStackScrollLayout`, or `now_bar_rootview` (`R.id.now_bar_rootview`); true foreground occlusion requires SystemUI-side participation or confining subject separation below the active clock/notification/NowBar bands.
- **ENR glass blur over dynamic wallpaper motion:** Lockscreen cards (`mOnKeyguard && mDimmed`) sample live `180px` `BackgroundBlurDrawable` presets tuned to `mLockscreenNotificationAreaLuminance` (`23` / `64` thresholds), so bright high-contrast subject elements entering the NSSL band shift card blur presets dynamically whereas default NIO (`1`) keeps only top-left status bar icons + bottom `now_bar_rootview` active.

---

## 7. Built-In Wallpaper Engines (`ImageEngine`, `VideoEngine`, `MultipackEngine`, `CanvasEngine`) & Bundled `live.sdk` Internals

### Engine Dispatch & Surface Geometry Sync (`ImageWallpaper` + `WallpaperEngine`)
- **`ImageWallpaper.onCreateEngine(int which)`** extends `com.samsung.android.wallpaper.live.sdk.service.LiveWallpaperService` and instantiates **`ImageWallpaper.IntegratedEngine(which, WallpaperManager.semGetWallpaperType(which))`** running on a dedicated `HandlerThread("ImageWallpaper")` (`mWorker`):
  - `mWallpaperType == 0` → `ImageEngine`, `1` → `MotionEngine`, `3` or `1000` → `MultipackEngine`, `4` → `AnimatedEngine`, `5` → `GifEngine`, `8` → `VideoEngine` (`VideoSource` type `1`).
  - Legacy **`ImageWallpaper.CanvasEngine`** + **`ImageWallpaperCanvasHelper`** (backed by `WallpaperManager.useWallpaperBitmap` + `HighlightFilterHelper`) remain inside `ImageWallpaper.java` (`mCanvasEngineList`) alongside the modern `IntegratedEngine` stack.
  - `IntegratedEngine.onCommand(...)` filters out `"samsung.android.wallpaper.blocktoucharea"` before forwarding to `mSubEngine.onCommand(...)`, and then calls `super.onCommand(...)` (`LiveWallpaperService.BaseEngine`) so SDK `DisplayStateMonitor` processes lifecycle/AOD commands.
- **`WallpaperEngine`** (abstract base under `com.android.systemui.wallpaper.engines`):
  - `shouldWaitForEngineShown()` returns `this instanceof ImageEngine`.
  - `setVisibleRectOfSurface(Rect)` reflects `WallpaperService.Engine.mBlastBufferQueue` (`BLASTBufferQueue`), calls `syncNextTransaction(true, callback)` via `ImageWallpaper$IntegratedEngine$2$$ExternalSyntheticLambda0`, and inside the transaction callback executes `transaction.setGeometry(surfaceControl, surfaceFrame, visibleRect, 0)` + `apply()` under `mSurfaceHolder.mSurfaceLock`.

### `ImageEngine.drawFrameOnCanvas` & `DownScaledSourceBitmapManager`
- **Two-phase progressive redraw (`onSurfaceRedrawNeeded`):**
  1. Clears any previous surface geometry crop (`setVisibleRectOfSurface(null)`), compares `estimateDrawStateToDraw(sourceWhich)` against `mLastDrawnState` (`which`, `surfaceWidth`, `surfaceHeight`, `darkMode`), and refreshes `mWallpaperSource` if surface pixel area changed.
  2. Looks up a downscaled bitmap in `DownScaledSourceBitmapManager` (`512px` target short side when `Process.getAdvertisedMem() <= 6500000000L`, else `Math.max(1024f, Math.max(display.x, display.y) * 0.5f)` when `scale <= 1.0f`). If present, draws the downscaled frame synchronously on the calling thread (`drawFrameOnCanvas(..., item.mScale)`) and posts full-resolution `drawFrameSynchronized` to `ImageWallpaper.mWorker`.
- **Verified `ImageEngine.drawFrameOnCanvas(Canvas, long, Rect, Bitmap, ArrayList, float bmpScale)` pipeline (from JADX source + smali):**
  1. Validates `WallpaperUtils.isValidBitmap(bitmap)` and `isSurfaceCreated()` (unless `customCanvas != null`).
  2. **Intelligent crop:** Queries `IntelligentCropHelper.getNearestCropHint(Point(dest.width(), dest.height()), cropRects)` (minimum `|(destH/destW) - (hintH/hintW)|`); if found, scales the `Rect` by `bmpScale` and intersects with `(0, 0, bitmap.getWidth(), bitmap.getHeight())`.
  3. **Aspect-fill matrix:** Computes `scale = Math.max(destW / srcW, destH / srcH)`, sets `matrix.setScale(scale, scale)`, applies `matrix.preTranslate(-srcRect.left, -srcRect.top)` when cropped, and centers via `matrix.postTranslate(Math.round((destW - srcW * scale) * 0.5f), Math.round((destH - srcH * scale) * 0.5f))`.
  4. **Filters & dark-mode dim:**
     - If `mWallpaperSource.getSupplier().getFilterData()` is non-empty, renders a filtered copy via `ColorDecorFilterHelper.createFilteredBitmap(...)` (recycled immediately after draw).
     - Resolves `getDimFilterColor(sourceWhich)` → `ImageDarkModeFilter.getWallpaperFilterColor(context, hints)` (`#000000` with `getDarkModeDimOpacity()` capped at `0.25f` in dark mode, else adaptive dim opacity; suppressed when `LsRune.SUBSCREEN_LARGE_FRONT_SUB_DISPLAY` + flag `16`), setting `PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER)` on `mBitmapPaint`.
  5. **Hardware surface lock & HWUI teardown:** Draws directly to `customCanvas` if provided (used as fallback by `onGetScreenshot`), or under `mSurfaceLock` calls `surface.lockHardwareWideColorGamutCanvas()` (if `mWallpaperManager.wallpaperSupportsWcg(bitmap)`) else `surface.lockHardwareCanvas()`, invokes `canvas.drawBitmap(bmp, matrix, mBitmapPaint)`, calls `surface.unlockCanvasAndPost(canvas)`, and **immediately calls `surface.hwuiDestroy()`** unless `(mWallpaperSource.isMultipack() && LsRune.LOCKUI_AOD_PACKAGE_AVAILABLE)`.
  6. `drawFullQualityFrame` calls `IntegratedEngine.reportEngineShown(false)` and releases cached wallpaper bitmaps (`WallpaperManager.forgetLoadedWallpaper()`) once `mBitmapUsages <= 0`; `drawFrameSynchronized` stamps `Settings.System` `"wallpaper_finish_drawing"`.

### `VideoEngine`, `MultipackEngine`, `TransitionEngine`, & Sibling Engines
- **`VideoEngine` & `VideoController` (`com.android.systemui.wallpaper.engines.video`):**
  - Wraps `SemMediaPlayer` (`setParameter(35004, 1)`, `setParameter(37000, 1)`) behind a `CommandQueue` (`PLAY`, `PAUSE`, `PAUSE_N_SEEK_TO_0`, `SEEK_TO`, `RELEASE_SESSION`) executed on `ImageWallpaper.mWorker`.
  - Computes `getVisibleRectOfSurface()` by center-cropping `mVideoSource.getCropHint(surfaceFrame)` to the surface aspect ratio and mapping from `mVideoSize` into `surfaceFrame`, then committing via `setVisibleRectOfSurface(...)`. For custom pack videos (`(mVideoSource.mType & 2) == 2`), `onSurfaceAndPlayerReady()` calls `IntegratedEngine.recreateSurfaceControl()` (`semCreateSurface(true, 1.0f)` with `500L` ms cleanup).
  - **Main/Sub display AOD state machine (`updatePlayerState$1` / `dispatchCurrentStateForMainOrSubDisplay`):** Releases `SemMediaPlayer` session (`pauseAndSeekToFirstFrame()` + `releaseMediaPlayerSession()`) when `!isWindowVisible()` or `(!isVisible() && DisplayState.OFF)`; pauses immediately on `ON` if `mBouncerVisible`, `mPlayerPausedForcefully`, or keyguard `(which & 3) == 2` with `mIsGoingToSleepFromNonKeyguard || mIsUnlockingFromScreenOffOrAod || isLockScreenDisabled`; delays pause by **`100ms`** (watchface) or **`400ms`** (main/cover) on `ON → AOD_WITH_WALLPAPER`, and pauses with release on `AOD_WITHOUT_WALLPAPER` / `OFF`.
- **`MultipackEngine` & `TransitionEngine` (`com.android.systemui.wallpaper.engines.multipack`):**
  - Listens to `CoverWallpaperController` (watchface / virtual display) or `PluginWallpaperController` (lockscreen) and swaps `mSubEngine` dynamically (`VideoEngine` for types `2, 8, 13, 23`; `GifEngine` for `12, 22`; `ImageEngine` otherwise).
  - When `willShowAodWithWallpaper$1()` and `!mIsFirstTransition`, grabs `onGetScreenshot(new ScreenshotOptions("prev"))` before destroying the old sub-engine and `"next"` from the new engine, then mounts **`TransitionEngine`**, which uses `IntegratedEngine.C96572`'s `Choreographer.FrameCallback` loop to crossfade (`mPrevThumbnail` → `mNextThumbnail`) on `lockHardwareCanvas()` over **`500L` ms** (`TRANSITION_ANIMATION_DURATION`) before swapping in the real target sub-engine. Without `TransitionEngine`, switching to `VideoEngine`/`GifEngine` on lock/virtual display clears the surface to `#FF000000` (`makeSurfaceBlack`).
- **Sibling engines:** `GifEngine` renders `AnimatedImageDrawable` via `lockHardwareCanvas()` + center-crop `Matrix` (drawing `mFirstFrame` when hidden/`OFF` and playing only when `isVisible() && DisplayState.ON`); `MotionEngine` hosts `MotionWallpaper` View + gyro `SensorManager` listener; `AnimatedEngine` draws `FrameLayout` + `ComplexAnimationBuilder` via `Choreographer`.

### Bundled `com.samsung.android.wallpaper.live.sdk` Internals (in SystemUI)
- **`LiveWallpaperService.BaseEngine` & `LiveWallpaperEngineManager`:**
  - `BaseEngine` registers itself as a `WeakReference` in `LiveWallpaperEngineManager.mEngineList` (`onCreate`/`onDestroy`) and reflects hidden `WallpaperService.Engine` members (`semGetWallpaperFlags`, `semGetExtras`, `semIsFixedOrientationRequested`, `semSetFixedOrientation`, `WallpaperService.Engine.mVisible`).
- **`DisplayStateMonitor` Hierarchy (`service.displaymonitor.*` & `utils.DeviceStateMonitor`):**
  - `BaseEngine.onCreate` selects **`FullAodDisplayStateMonitor`**, **`SeamlessAodDisplayStateMonitor`**, **`BasicAodDisplayStateMonitor`**, or **`NoAodDisplayStateMonitor`** from `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN`, flip/watchface subdisplay floating features, and `SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_AOD_ITEM`.
  - Combines `PowerManager.isInteractive()`, `PowerManager.isDozeAfterScreenOff()`, `DeviceStateManager` dynamic-proxy fold callbacks (with a `10ms`-step, `50ms`-timeout interactive wait on state `0`), a `ContentObserver` on `Settings.System.aod_show_state`, and `onCommand` (`android.wallpaper.wakingup`, `android.wallpaper.goingtosleep`, `samsung.android.wallpaper.goingtosleep`, `android.wallpaper.aodstate`) into `DisplayState` (`NONE`, `OFF`, `ON`, `AOD_WITH_WALLPAPER`, `AOD_WITHOUT_WALLPAPER`).
- **IPC Provider (`LiveWallpaperProvider` → `LiveWallpaperProviderCallDispatcher` → `ProviderCallDispatcher`):**
  - `ImageWallpaperProvider` subclasses `LiveWallpaperProvider`, whose `call(...)` enforces **platform signature** (`checkSignatures(1000, callingUid) == 0`) and dispatches `"get_screenshot"` / `"capture_surface"`, `"get_thumbnail"`, `"get_background_region"`, `"get_engine_running_state"`, `"get_state_shot"`, `"get_wallpaper_index"`, `"get_video_current_position"`, `"prepare_wallpaper"`, `"get_scene_id"`, and `"get_thumbnail_by_backup_id"`.
  - `onGetScreenshot` calls `BaseEngine.onGetScreenshot(ScreenshotOptions)` (falling back to `DisplayUtils.copySurfaceToBitmapSync` via `PixelCopy` on `"wallpaperScreenshot"` `HandlerThread`) and returns an encoded PNG/JPEG `ParcelFileDescriptor` under key `"image_file_descriptor"`; SystemUI’s `ProviderCallDispatcher` adds `"bg_region"` scaling (`BgRegionGenerator` + `ImageSource` crop alignment) and `ThumbnailGenerator` JPEG pipes.
