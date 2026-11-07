# AOD complication rendering and depth ownership

Traced through the JADX MCP on 2026-10-26 after the user reported no visual improvement in 0.6.4. The installed AOD APK's relevant methods and fields were checked independently on the phone. Follow-up: 0.6.6 handles fully covered blur surfaces and is user-confirmed working; see RELEASE_0.6.6.md.

## Creation and container ownership

`ComplicationWidgetPluginController.initialize(ViewGroup root)` stores the actual complication container in its `container` field. The controller also owns a `KeyguardClockProvider`, which holds the selected native `clockItem` and its font. This provides a stable identity independent of any individual temporary clock view.

`WidgetLayoutManager.setupRootContainer(root)` calls `ComplicationGridUIController.setRootContainer(root, "LOCK_COMPLICATION_GRID_CONTAINER", WidgetDisplayType.Lock)`. The grid controller delegates to `ComplicationContainerManager`, adds a `GridContainerView`, and registers item/drop listeners and widget listening. In the recorded SystemUI hierarchy the layout is:

```text
KeyguardComplicationWidgetContainer
  GridContainerView (grid_layout)
    GridContentContainer (grid_item_container)
      GridItemView
        FrameLayout (shadow_container)
          CardView (contents_container)
            ComplicationWidgetHostViewContainer
              View (tag: BlurView)
              aod.l11: ComplicationWidgetHostView
                RemoteViews content from the widget app
```

`ComplicationGridUIController.addGridItem` puts the decorated widget into the grid item's content container. Its `forEachWidgetView` and `allComplicationHostViewForEach` walk those native item holders, rather than searching an arbitrary clock view's root.

## Content is a normal AppWidgetHostView

`aod.l11` extends `aod.il6`, which extends Android `AppWidgetHostView`. The host receives `RemoteViews` through `updateAppWidget`; the inspected subclasses do not replace drawing with a mountain bitmap or a special AOD widget texture.

The host's `onMeasure` applies its `WidgetSizeSpec` scale and pivot. `onLayout` calls `nn1.i -> nn1.j`, which resets rounded outline providers on the host and its background. `nn1.h -> tb9.l` recursively recolors text, images, progress bars and backgrounds; it does not implement subject detection or depth. These normal content updates must remain native.

## Backgrounds are a separate surface

`ComplicationWidgetBackgroundDecorator.applyBlurBackground` wraps the host in `ComplicationWidgetHostViewContainer`, copies wallpaper/AOD/music flags, adds the content, and calls `addBgViews`. The container owns a separate plain view tagged `BlurView`; it can also own dim and preview background views.

`addBgViews` calls `e70.d` for a preset blur. `refreshBlur` calls `e70.c` with custom radius/color attributes and a dim/background color. Both submit `SemBlurInfo` to the background view. Native reduced-transparency mode clears the compositor blur and uses an ordinary background color instead. A single canvas mask on the AppWidget content cannot cover all these representations.

## AOD and waking are distinct updates

| Native callback | Rendering work |
| --- | --- |
| `ComplicationWidgetPluginController.setDozing` | `WidgetStateManager` forwards dozing to grid hosts and controls widget listening. |
| `setDarkAmount` | Stores the previous amount, then `BlurEffectManager.updateDarkAmount -> GridUIController.updateDarkAmount -> container.setDarkAmount`; this updates background blur/dimming or reduced-transparency backgrounds. |
| `requestAODState` | Conditionally forwards to `updateBlurByAODState`; on the blur-enabled path it ignores requests when not dozing. |
| `onFinishedWakingUp` | Re-evaluates the blur preset from the clock color and wallpaper brightness. |
| `animateContents` | On the blur-enabled path it animates host content and dim views separately, rather than the whole background/content container. |
| `refreshComplicationGridAndWidgets` | Reconfigures the grid, reloads the native widget list, and updates the size table. It is not merely an invalidate call. |

Music expansion can disable blur, submit null blur info and hide content. Wallpaper-on-AOD, Now Bar, transparency and profile transitions have their own observers. A depth refresh must preserve these native states and must not resurrect a blur surface that Samsung has disabled.

## Concrete module ownership bug

Before 0.6.5, `RuntimeClockHooks.apply` updated the *shared* widget grid from each clock view. Its no-profile branch called `DepthEffects.applyWidgets(view, false, null, false)`; profiled clocks could also write enabled/disabled state for that shared grid. The clock's root was searched for the first `grid_layout`, without proving that this clock was the selected owner.

Samsung creates multiple clock views. Earlier captured logs include the saved type 2/font 803 clock alongside an unprofiled type 8/font 803 `DigitalHorizontalHmsClockView`. Therefore an unrelated clock's initialization/layout could clear the widget mask, while another clock later re-enabled it. Redraw and outline changes alone could not correct that ownership error. The possibility of this overwrite is established by source; whether it explains every reported fragment remains subject to visual validation.

## 0.6.5 integration

`RuntimeWidgetHooks` binds depth to each native complication controller's actual `container`. It reads the selected `keyguardClockProvider.clockItem` type/font and the committed wallpaper-scoped settings. Runtime clock view hooks no longer write or clear widget depth.

Controller initialization, doze, dark amount, wake, layout replacement and completed widget updates refresh this owner. Native selected-clock/font changes and module settings changes also refresh it. Missing native records during initialization defer the lookup rather than treating a temporary clock as an authoritative disable. Release and container replacement clear only that controller's own old root. Dark-amount frames reuse the selected profile rather than making a settings IPC on each frame.

The existing subject-path outlines and final blur-info subtraction now inherit their mask from the stable native complication container, including newly added widget children. Clock glyph depth and the confirmed saved-height restoration remain unchanged.

## Validation and limits

The signed APK compiled and passed v3 verification. The phone probe passed the exact controller fields and transition methods, the selected clock record, native font handoff, Samsung clip/blur copying, corner preservation and full-occlusion outline checks. The prior offscreen GPU pixel test was terminated and is not counted as a visual pass.

The user confirmed persistence in 0.6.3 but reported widget failure through 0.6.5. Its owner logs were correct; the remaining fully covered background needed null blur info rather than an empty clip path. The user confirmed 0.6.6 after lockscreen/AOD captures verified that correction. Mask extraction can miss parts of the mountain. Dressroom's main preview remains deferred.

Source snapshots: `work/clock-audit/ComplicationWidgetHostViewContainer.java`, `ComplicationWidgetPluginController.java`, `BlurEffectManager.java`, `WidgetStateManager.java`, `WidgetLayoutManager.java`, `ComplicationAnimationManager.java`, `WidgetObserverManager.java`, `KeyguardClockProvider.java`, `aod.l11.java` and `aod.il6.java`.
