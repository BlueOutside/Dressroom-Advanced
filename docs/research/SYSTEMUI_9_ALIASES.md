# One UI 9 SystemUI research aliases

Reference: com.android.systemui, Android 17, version code 37.
The clock-host source, xrefs and wrapper alpha/dark smali were inspected
through JADX MCP. See [ownership map](CLOCK_OWNERSHIP_MAP.md).

## Clock host and animation batch (2026-11-24)

These are JADX display aliases verified in this pass. The previous-name
column can contain JADX-generated identifiers; compare raw DEX descriptors
and call/field contracts before using any result in a runtime shim.

| Kind | JADX owner / previous name | Alias |
| --- | --- | --- |
| class | `com.android.systemui.shade.NotificationPanelViewController$$ExternalSyntheticLambda43` | `ClockAndNotificationHitTest` |
| class | `com.android.systemui.doze.PluginAODManager$$ExternalSyntheticLambda1` | `AodFoldStateListener` |
| class | `com.android.systemui.shade.NotificationPanelViewController.C68564` | `KeyguardTouchCallback` |
| class | `com.android.systemui.shade.NotificationPanelViewController.C68619` | `StatusViewHostCallback` |
| method | `com.android.systemui.keyguard.animator.KeyguardTouchAnimator.m322x3e50492b` | `getAnimationParent` |
| method | `com.android.systemui.keyguard.animator.KeyguardTouchAnimator.m325x6c4411ab` | `isChildAnimationRunning` |
| method | `com.android.systemui.keyguard.animator.KeyguardTouchAnimator.m326x304bee4b` | `resetChildViewTransforms` |
| method | `com.android.systemui.keyguard.animator.ScreenOnAffordanceViewController.access$showScreenOnAffordanceAnimation$tryAnimationEnd` | `finishScreenOnAnimationIfReady` |

## Pass 2026-10  -  AOD/lockscreen areas (SystemUI 16.4006)
No obfuscated top-level classes found in doze/dreams/keyguardimage/power.feature.aod/statusbar.lockscreen
(only C-named inner lambdas  -  skipped). Key named anchors for the mod:
- `com.android.systemui.doze.{AODMachine,AODUi,AODParameters,AODScreenBrightness,AODIntentService,
  AODManagerWrapper,AODOverlayContainer,AlwaysOnDisplayPolicy,PluginAODManager(143KB),DozeMachine}`
- `com.android.systemui.plugins.aod.{PluginAOD,PluginAODFaceWidgetManager,PluginAODNotificationManager,
  PluginAODParameter,PluginAODSystemUIConfiguration,PluginTspEvent}` + `plugins.keyguardstatusview.PluginAODStateProvider`
- `com.android.systemui.keyguardimage.KeyguardImageProvider` (+ Clock/Wallpaper/Left/RightShortcut ImageCreators)
- `com.android.systemui.power.feature.aod.AodTrigger{Policy,Controller,Feature,Executor}` (cover/wireless triggers)
- `com.android.dream.lowlight.*` + `ambient.lowlight.*` (low-light AOD), `lowlightclock.LowLightClockDreamService`
- `com.samsung.android.knox.lockscreen.*` (Knox LSO overlay widgets)
- Settings keys: see HIDDEN_FEATURES.md §SystemUI (`aod_mode*`, `aod_show_*`, `aod_tap_to_show_mode`,
  `aod_light_reveal_alpha`, `aodlock_support_hijri/lunar`, `doze_always_on`, `always_on_display_constants`)
