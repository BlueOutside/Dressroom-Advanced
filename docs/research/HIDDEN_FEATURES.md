# Hidden / gated features found in AOD 8.9.66.1 (2026-11-24)

Found while hunting for well-hidden functionality in
`com.samsung.android.app.aodservice` 8.9.66.1 via the jadx MCP. All names below
are the new jadx aliases (see `JADX_RENAME_LOG.md` batches 10-11).

## 1. Debug flag wall: `aod.AodDebugFlags` (was `aod.bj1`)

Every debug flag is gated behind `aod.BuildTypeFlags.isEngOrUserdebugBuild`
(`Build.TYPE` == eng/userdebug; was `aod.xi1.f34316b`), plus
`isNonShipBuild` (`!ro.product_ship`). On production (`user`) builds all are
off/0. Notable flags (all read from `SemSystemProperties`):

| Alias | Property | Meaning |
| --- | --- | --- |
| `debugTapToShow` | `persist.debug_aod_taptoshow` | tap-to-show AOD testing |
| `debugEdgePosition` / `debugEdgePositionRange` | `debug.aod.edge_position[_range]` | edge-position experiments |
| `debugClockMovableArea` | `debug.aod.clock_movable_area` | movable clock area debug |
| `clockBlurStateDebugEnabled` | `debug.aod.clock_blur_state` | clock blur state logs + debug overlay |
| `debugAllowClockProvider` | `debug.allow_clock_provider` | allow external clock providers |
| `debugOverlayOnWindow` | `debug.aod.overlay_on_window` | overlay rendering mode |
| `debugFaceWidgetTransitionTest` | `debug.aod.face_widget_transition_test` | face widget transition test |
| `subdisplayTestMode` | `persist.debug.subdisplay_test_mode` | sub display test mode |
| `debugLiveClockSelfMoveInterval` | `debug.aod.live_clock.selfmove_internal` | live clock self-move (depth-adjacent) |
| `logModule*` (7 flags) | `debug.aod.log_module` contains X | per-module log filters |
| `debugPreviewSaveBitmap` | `debug.aod.preview.save_bitmap` | save preview bitmaps |

## 2. Hidden `Settings.System` keys (227 registered in `SettingsHelper`)

`SettingsHelper.settingsCache` (was `z82.f39619e`) holds
`aod.SettingKeyEntry` (was `aod.rt6`) records: `settingsTable`/`key`/
`dataType`/`defaultIntValue`, read reflectively from
`Settings$System/Secure/Global` via `loadValueFromResolver`, cached in
`cachedIntValue`/`cachedStringValue`.

Interesting ones (table/key = default):

- `System/now_bar_aod_enabled = 1`  -  **Now Bar on AOD toggle** (see below)
- `Global/brief_now_bar_need_to_unlock = 1`  -  Now Bar on lock screen requires unlock
- `System/aod_erase_background = 0`  -  erase-background (wallpaper cutout) flag
- `System/aod_show_lockscreen_wallpaper = 1`, `System/aod_show_cover_screen_wallpaper = 1`
- `System/aod_tap_to_show_mode = 1`, `System/aod_show_for_new_noti = 0`
- `System/aod_brightness_test_mode = 0`, `System/debug_aod_brightness = -1`
- `System/face_widgets_option = 0`, `System/face_widgets_cover = 1`
- `System/facewidget_music_transparency = 80` (+ `_dark_mode = 40`)
- `Secure/plugin_lock_clock = -1`  -  plugin clock pack selection
- `System/lock_clock_adaptive_colors`, `System/lock_adaptive_color[_sub]`
- `System/sub_display_system_wallpaper_transparency = -1`
- `System/aod_multi_fold_sub_display_mode = -1` + start/end times (foldables)
- `System/clock_weather_temperature`, `System/clock_weather_location_exist`
- `System/custom_wallpaper_color_areas_lock/home`  -  CMF color areas
- `Secure/scpm_aod_common_policy`  -  Knox SCPM policy blob for AOD

## 3. Now Bar on AOD  -  the hidden one

Flow (all verified in source):

```
Settings.System "now_bar_aod_enabled" (default 1)
  -> SettingsHelper.isNowBarAodEnabled() (was m15243x; also checks
     cj1.isFullScreenAODSupported() + media-card-expanded-on-top observer)
  -> WidgetObserverManager.isNowBarAodEnabled lambda
     -> ComplicationWidgetHostViewContainer.setNowBarOnAODEnabled
  -> ComplicationWidgetBackgroundDecorator applies it together with
     WidgetBlurInfo (BlurEffectManager.createWidgetBlurInfo -> isNowBarAodEnabled)
```

Backup/restore key: `/AOD/ShowNowBarEnabled` in `AODBackupRestoreProvider`
(writes `Settings.System.putInt(resolver, "now_bar_aod_enabled", v)`).
So it is a plain hidden system setting: `adb shell settings put system
now_bar_aod_enabled 0|1`, and it survives backup/restore.

Related Now Bar classes already aliased: `aod.NowBarStackCardCallbacks`,
`aod.NowBarItemMainHandler`, `aod.NowBarUtils`, `aod.SetNowBarMediaExpandStateUseCase`,
`aod.NowBarContentsBroadcastReceiver`, `aod.NowBarCardStackChangeListener`,
plus `com.samsung.android.uniform.nowbar.*` (MediaLightingView, MediaMaskingFrameLayout).

## 4. Where the remaining obfuscation lives

Outside the `aod.*` mega-package, the APK is essentially readable
(`common.grid.rule`, `common.provider.p003DB`, `facewidget.*`,
`uniform.widget.*` are Samsung-named). Hidden behavior is concentrated in
`aod.*` + the `Settings.System` keys above.

---

# Full hidden-stuff inventory (2026-11-24, sweep pass)

## A. Device/region feature wall: `aod.DeviceFeatureFlags` (was `aod.ha6`, 135 flags)

Sources: `SamsungFeatureReader` (`SEC_FLOATING_FEATURE_*`), `SemSystemProperties`
(country ISO, `ro.product.vendor.device`, `ro.csc.*`), CSC features, sysfs nodes,
AI config version. Aliased flags (batch 12):

| Alias | Source | Gates |
| --- | --- | --- |
| `aodFullscreenLevel` | `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN` (int 0-3) | `cj1.isFullScreenAODSupported()` |
| `supportsLockscreenTile` / `supportsAodTile` / `supportsVirtualDisplay` | tile-type config string contains `LOCKSCREEN`/`AOD`/`VIRTUAL_DISPLAY` | `br1.m2292d()` = lockscreen tile || flag O |
| `isChinaDevice` / `isChinaCarrier` / `isKoreaDevice` / `isUsDevice` / `isIranDevice` | country ISO / sales code / `ro.csc.countryiso_code` | region features |
| `isE5SeriesDevice` / `isB4qE4qDevice` | `ro.product.vendor.device` contains `e5` / is `b4q`/`e4q` | device-specific UI |
| `nativeAiEnabled` / `nativeAiDisabled` | `SEC_FLOATING_FEATURE_COMMON_DISABLE_NATIVE_AI` | native AI features |
| `aiBriefSupported`, `aiConfigVersionGte20263`, `aiConfigVersionLt20263` | `SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION` (>=20251/20263), AI Brief feature | Galaxy AI Brief on AOD |
| `selfPokeDrawLockEnabled` | `persist.enable.self.poke.draw_lock` (default true) | self-poke lock drawing |
| `foldPerformanceDebug` | `debug.aod.fold_performance` | foldables |
| `hasClockPackVersionOverride` | config contains `clockpackversion`; `defaultClockPackage` = ClockPack vs AOD | clock provider choice |
| `hasBatteryTimeToFull` / `hasLedPattern` / `isAmoledDisplay` | `/sys/class/power_supply/battery/time_to_full_now`, `/sys/class/sec/led/led_pattern`, AMOLED feature | charging UI / LED |
| `semVersionGte50000` / `semVersionGte80000` / `frameworkVersionGte110500` | SEM/framework version ints | version gates |
| `supportsDisabledMenuN40` | `SEC_FLOATING_FEATURE_COMMON_SUPPORT_DISABLED_MENU_N40` | N40 menu |
| `aodFullscreenLevel` consumers |  -  | fullscreen AOD (Now Bar media expand etc.) |

Un-aliased remainder: mostly carrier branding (ATT/CHC/CHM/CTC/CTC...), dualdisplay/
font/edgeclock/hwtimesync/coverscreentimeout/coverboldfont config-list tokens, and
more SEM version gates.

## B. Hidden content providers / cross-app endpoints used by AOD

- `content://com.samsung.android.app.dressroom.provider`  -  **Dressroom editor IPC** (read by `aod.ec6`, `aod.ql5`, `aod.rd8`, `aod.sb7`, SubScreenClockStyleSettingActivity, SubUIWallpaperUtils)
- `content://com.samsung.android.app.clockpack.provider/lock_settings/...`  -  `sub_tile_enable_list`, `sub_tile_all_list`, `sub_tile_scpm_list` (**SCPM/Knox-gated tiles**), `complication_enabled_list`, `complication_widget_host_change`, `multiple_widget_info_list`, `*_bnr_called` (backup flags)
- `content://com.nhn.android.search.nowbarsettingprovider`  -  **Now Bar settings (NHN/Naver)** read by `aod.b05`, `aod.h05`
- `content://com.samsung.android.rubin.persona.*` and `rubin.context.*`  -  **Rubin AI persona/context feeds** (preferredmusic, commutingpattern, drivingevent, refreshingevent, generic) consumed by ~10 `aod.*r5/h5/s2` classes → feeds Now Bar cards
- `content://com.android.systemui.keyguard.image/...` (+ `portrait?type=cover_wallpaper`)  -  SystemUI keyguard/cover image (see `NowBarStackCardCallbacks.loadCoverKeyguardImage`)
- `content://com.samsung.android.stickercenter.provider/sticker/TypeD2/`  -  **AR stickers** (ties to `aod.setting.ar.ARBroadcastReceiver`, `samsung.stickercenter.intent.PROCESS_COMPLETE`)
- `content://com.samsung.android.scpm.policy/` + `com.sec.knox.provider/RestrictionPolicy2`  -  SCPM/Knox policy
- `content://com.sec.android.log.diagmonagent.sa/...`  -  diag logging

## C. Manifest receivers with hidden surface

- `AODFenceReceiver` (exported, no permission)  -  `com.samsung.android.rubin.fence.CONTEXT_FENCE_STATUS_CHANGED` → **Rubin geofence drives AOD** (see `System/aod_context_fence_not_working` key)
- `ScpmReceiver` (Knox perm)  -  SCPM policy updates: `aod-common-policy-8f16`, `large-coverscreen-app-policy-test-52b6`, `coverscreen-app-tray-package-policy-f98d` (note the `-test-` policy name!)
- `AODBackupRestoreReceiver`  -  `REQUEST_BACKUP/RESTORE_EDGESCREEN_AOD_SETTING` (wssnps perm); contains `/AOD/ShowNowBarEnabled`
- `AODBroadcastReceiver` (signature perm)  -  PSM_APPLY, emergency mode, theme apply, BOOT_COMPLETED, HQM_UPDATE_REQ
- `ResetReceiver`  -  demo reset / soft reset / package data cleared
- `SubLauncherFavorite2x2/4X2AppsWidgetProvider`  -  cover-screen favorite-app widgets

## D. Related keys

`System/aod_context_fence_not_working`, `Secure/scpm_aod_common_policy`,
`System/sub_tile_scpm_list` reads in `aod.df6`/`aod.ge8`, Now Bar NHN provider
in `aod.b05`/`aod.h05`, Dressroom provider in `aod.ec6` + 4 more.

---

# Settings & policy deep-dive (2026-11-24, sweep pass 2)

## E. SCPM = Samsung Cloud Policy Manager (Knox `com.samsung.android.scpm` app)

Confirmed: `aod.ScpmAodPolicyManager` (was `aod.df6`, log tag "ScpmManager_AOD")
registers with the SCPM app via `ConfigurationApi`/`RegistrationApi` using the
app **signature**, receives `com.samsung.android.scpm.policy.UPDATE.aod-common-policy-8f16`
broadcasts (`ScpmReceiver`), and stores the received XML at
`Secure/scpm_aod_common_policy` (also `scpm_subscreen_preload_policy_payload`).
`aod.AodCommonSettingsUtils` (was `aod.AbstractC0814t1`, tag "AODCommonSettingsUtils")
parses it: policy XML contains feature tags like `nowbar_commute`,
`device_policy` / `getKeyguardDisabledFeatures`.

- `isNowBarCommutePolicyEnabled` (m11184A): `policy.contains("nowbar_commute")`
- `isGoogleMapsNowBarCommuteEnabled` (m11185B): `!isChinaDevice && f12840R0 && commutePolicy && Settings.System "key_now_bar_com_google_android_apps_maps_internal" == 1`
- `isGoogleFinanceNowBarEnabled` (m11186C): `key_now_bar_..._googlequicksearchbox_finance`
- `isGoogleQuickSearchBoxNowBarEnabled` (m11187D): `key_now_bar_..._googlequicksearchbox`
- SCPM policy broadcast names seen in manifest: `aod-common-policy-8f16`,
  `large-coverscreen-app-policy-test-52b6` (a *test* policy), `coverscreen-app-tray-package-policy-f98d`

## F. Per-app Now Bar toggles: hidden `key_now_bar_<sanitized package/feature>` family

Each defaults to 1, read via `Settings.System.getInt(resolver, "key_now_bar_" + pkg.replace('.','_'), 1)`:

- `key_now_bar_com_google_android_apps_maps_internal` (Maps commute card)
- `key_now_bar_com_google_android_googlequicksearchbox` (Google app / Now Brief)
- `key_now_bar_com_google_android_googlequicksearchbox_finance` (finance)
- `key_now_bar_com_nhn_android_search` (Naver Now Bar  -  matches the NHN provider)
- `key_now_bar_com_samsung_android_knox_galaxyai` (Knox Galaxy AI)
- `key_now_bar_com_samsung_android_wallpaper_res`
- `key_now_bar_com_samsung_android_providers_context` (Samsung Context/Rubin)
- `key_now_bar_com_samsung_android_app_notes`, `key_now_bar_com_sec_android_app_sbrowser`
- `key_now_bar_com_skt_prod_dialer`, `key_now_bar_com_lguplus_aicallagent` (carrier call agents)
- `key_now_bar_com_sec_feature_spen_usp` (S Pen), `key_now_bar_com_samsung_feature_aod_ramless`
- `key_now_bar_com_samsung_android_app_aodservice_search`

Consumers: `aod.b05`, `aod.la2`, `aod.FaceWidgetUiTaskRunnable`, `aod.s66`,
`aod.vm0`, `AODApplication`, `SettingsHelper`, `SettingsSearchProvider`, backup provider.

## G. `System/dls_state` bitmask (LockStar / Good Lock state)

- bit 16 (`& 16`) → `ClockBlurController.setSggActive(...)`  -  matches the
  `SGG_ACTIVE` block reason in `ClockBlurBlockReason` (read in `aod.C0820t7` / `FaceWidgetClockPage`)
- bits 64 / 8192 → `PluginLockStarManager` requests disabling LockStar ("SGGLockStarManager")

## H. Full SettingsHelper registry  -  107 unique keys

- `Global/animator_duration_scale`  -  String, default null
- `Global/battery_protection_threshold`  -  Int, default 80
- `Global/brief_now_bar_need_to_unlock`  -  Int, default 1
- `Global/device_provisioned`  -  Int, default 0
- `Global/edge_lighting_aod_brightness`  -  Int, default 0
- `Global/force_resizable_activities`  -  Int, default 0
- `Global/low_power`  -  Int, default 0
- `Global/protect_battery`  -  Int, default 0
- `Global/remove_animations`  -  Int, default 0
- `Global/show_brief_without_unlock`  -  Int, default 0
- `Global/zen_mode`  -  Int, default 0
- `Secure/badge_app_icon_type`  -  Int, default 0
- `Secure/cover_app_tray_list`  -  String, default null
- `Secure/cover_screen_show_notification_tip`  -  Int, default 0
- `Secure/cover_screen_show_notification`  -  Int, default 1
- `Secure/doze_enabled`  -  Int, default 1
- `Secure/fingerprint_always_on_type`  -  Int
- `Secure/knox_finger_print_plus`  -  Int, default 0
- `Secure/lock_screen_show_notifications_on_keyguard`  -  Int, default 1
- `Secure/multistar_app_tray_list`  -  String, default null
- `Secure/n_digits_pin_enabled`  -  Int, default 0
- `Secure/navigation_mode`  -  Int, default 0
- `Secure/notification_badging`  -  Int, default 0
- `Secure/plugin_lock_clock`  -  Int, default -1
- `Secure/scpm_aod_common_policy`  -  String (SCPM XML, see E)
- `Secure/scpm_subscreen_preload_policy_payload`  -  String
- `Secure/selected_search_engine`  -  String, default null
- `Secure/show_navigation_for_subscreen`  -  Int, default 0
- `Secure/sub_home_app_list`  -  String, default null
- `Secure/subhome_apptray_preload_packages`  -  String, default null
- `Secure/turn_on_cover_screen_for_notification`  -  Int, default 1
- `Secure/ui_night_mode`  -  Int, default 1
- `System/accessibility_reduce_transparency`  -  Int, default 0
- `System/accessory_cover_uri`  -  String, default null
- `System/add_info_music_control`  -  Int (default depends on N40 flag)
- `System/additional_information_val`  -  Int, default 0
- `System/ai_flex_window`  -  Int (default from `f12846U0`)
- `System/allow_to_use_calendar_reminder_data`  -  Int, default 0
- `System/aod_brightness_test_mode`  -  Int, default 0
- `System/aod_context_fence_not_working`  -  Int, default 0 (Rubin fence, see C)
- `System/aod_display_mode_auto`  -  Int, default 0
- `System/aod_erase_background`  -  Int, default 0 (wallpaper cutout)
- `System/aod_mode` / `_start_time` / `_end_time`  -  AOD schedule
- `System/aod_multi_fold_sub_display_mode` / `_sub_mode_start_time` / `_sub_mode_end_time`  -  foldables
- `System/aod_show_cover_screen_wallpaper` / `aod_show_lockscreen_wallpaper`  -  Int, default 1
- `System/aod_show_for_new_noti`  -  Int, default 0
- `System/aod_show_state`  -  Int, default 0
- `System/aod_sleep_pattern_wakeup_time`  -  String; `System/aod_sleep_state`  -  Int, default -1
- `System/aod_tap_to_show_mode`  -  Int, default 1
- `System/buds_enable`  -  Int, default 0
- `System/clock_weather_location_exist` / `clock_weather_temperature`
- `System/colortheme_app_icon`, `wallpapertheme_color`, `wallpapertheme_state`, theme packages
- `System/cover_app_tray_enabled`  -  Int, default 1; `cover_screen_demo_mode` -1; `cover_screen_timeout` 10
- `System/custom_wallpaper_color_areas_lock` / `_home`  -  CMF color areas
- `System/debug_aod_brightness`  -  Int, default -1
- `System/dls_state`  -  Int bitmask, default 0 (see G)
- `System/double_tap_to_sleep`  -  Int, default 0
- `System/dualclock_menu_settings`  -  Int, default 0
- `System/easy_mode_switch`  -  Int, default 1
- `System/emergency_mode`, `ultra_powersaving_mode`, `minimal_battery_use`  -  Int, default 0
- `System/face_widgets_cover`  -  Int, default 1; `System/face_widgets_option`  -  Int, default 0
- `System/facewidget_music_transparency`  -  80 (+ `_dark_mode` 40)
- `System/favorite_list_changed`  -  Int, default 0
- `System/gemini_space_commute_recap`  -  Int (Gemini Space commute recap on AOD)
- `System/homecity_timezone`, `System/time_12_24`
- `System/kg_multiple_lockscreen`  -  Int, default 1
- `System/large_cover_screen_apps` / `_navigation`  -  Int, default 0
- `System/lock_adaptive_color` / `_sub` / `lock_clock_adaptive_colors`
- `System/lock_noticard_opacity`  -  80 (+ `_dark_mode` 40)
- `System/lock_screen_allow_rotation`  -  Int (default from `f12807B`)
- `System/lock_shortcut_type`  -  String
- `System/lockscreen_minimizing_notification`  -  Int, default 1
- `System/lockscreen_wallpaper_transparent`  -  Int (default from WallpaperManager)
- `System/multi_fold_continuity_display_scaling`  -  Int, default 0
- `System/now_bar_aod_enabled`  -  Int, default 1 (Now Bar on AOD, see §3)
- `System/remove_animations`, `screen_brightness_mode` -1, `sub_screen_brightness` 0 / `_mode` -1
- `System/sub_display_system_wallpaper_transparency`  -  Int, default -1
- `System/theme_font_numeric`  -  String
- `System/turn_on_screen/time_while_unfolding_for_smart_cover`  -  smart cover

Exposed in Settings search index (`SettingsSearchProvider`): only
`aod_mode`, `aod_tap_to_show_mode`, `aod_show_lockscreen_wallpaper`,
`aod_show_cover_screen_wallpaper`, `subscreen_settings_title`, `subscreen_clockstyle`,
`subscreen_widget`, `subscreen_flex_window`, `subscreen_timeout`,
`cover_app_tray`, `cover_screen_notification`  -  everything else above is unexposed.

---

# Key → feature consumer map (2026-11-24, sweep pass 3)

Traced via code search + consumer sources. New aliases in batch 14.

| Key | Feature it drives | Consumers |
| --- | --- | --- |
| `System/now_bar_aod_enabled` | Now Bar cards on AOD | §3 flow; `SettingsHelper.isNowBarAodEnabled` |
| `System/key_now_bar_<pkg>` ×13 | per-app Now Bar toggles (Maps commute, Google app/finance, Naver, Knox AI, Notes, S Browser, SKT/LGU call agents, S Pen…) | `AodCommonSettingsUtils.m11185B-D` + `aod.b05/la2/vm0/s66` |
| `Secure/scpm_aod_common_policy` | SCPM policy XML (`nowbar_commute`, `device_policy`, keyguard disabled features) | `ScpmAodPolicyManager` (was `df6`) writes; `AodCommonSettingsUtils` parses |
| `System/aod_erase_background` | "Erase background" AOD setting (wallpaper cutout behind clock; string `settings_aod_erase_background_summary`) | `aod.AODSettingsMainPrefsFragment` (was `C0336g7`) writes/reads preview |
| `System/gemini_space_commute_recap` | Gemini Space commute recap session flag (default from `f12840R0`) | `AppSearchDBHelper` writes on commute enablement change |
| `Global/brief_now_bar_need_to_unlock` | Now Brief/Bar on lock screen requires unlock | `NowBarCardViewModel`, `SubLauncherNowBarController`, observer `aod.k33` |
| `Global/show_brief_without_unlock` | Brief visible without unlock (registered; read via SettingsHelper constant) | SettingsHelper only |
| `System/allow_to_use_calendar_reminder_data` | China-only calendar reminder consent (servicebox_calendar) | `aod.bb2` (consent dialog), `FaceWidgetCalendarManager` |
| `System/aod_context_fence_not_working` | records Rubin context-fence failure | `aod.SleepStateManager` (was `iz6`) writes; `AODFenceReceiver`, `AODConfigurationController`, dump `aod.RunestoneManagerDump` (was `C0409i6`) |
| `System/aod_sleep_state` / `aod_sleep_pattern_wakeup_time` | sleep/wakeup fence state machine ("SleepStateManager" wakeupAOD) | `SleepStateManager`, handler `aod.C0372h6`, `AODFenceReceiver` |
| `System/aod_tap_to_show_mode` | tap-to-show mode | `AodCommonSettingsUtils`, observer `aod.x38` case 12; search-exposed |
| `System/face_widgets_option` | "where to show" face widgets switch (`R.id.where_to_show`) | `aod.nb2`/`lb2` settings UI, `aod.bb2` toggle, `aod.vm0` |
| `Secure/plugin_lock_clock` | selected plugin clock pack (served to Dressroom/clockpack as `plugin_lock_clock` column) | `LockContentProvider.setPluginLockClockType()` |
| `System/kg_multiple_lockscreen` | multiple lockscreen widgets (guide lines) | `ComplicationManager`, `KeyguardComplicationObserver`, `SettingsHelper.isMultipleLockscreenEnabled` (was `m15241v`) |
| `Global/edge_lighting_aod_brightness` | edge lighting brightness on AOD when folded (`f12839R && isFolded()`) | `MODManager`, `manager.C1219a` |
| `System/lock_noticard_opacity` (80, dark 40) | notification card opacity on keyguard | `aod.z52` (noti card builder), `aod.s13` (GtsSupplier tags) |
| `content://.../lock_settings/sub_tile_scpm_list` | SCPM-disabled sub-tile list (provider URI, not a setting) | `aod.ge8.f11554t`; notified by `ScpmAodPolicyManager` |
| `System/custom_wallpaper_color_areas_lock/home` | CMF custom wallpaper color areas | reset by `aod.s66` ("ResetUtils"); observed by `aod.C0820t7` / `FaceWidgetRectManager` |
| `System/theme_font_numeric` | theme numeric font style | observer `aod.x38` case 19, read by `aod.q38` |
| `System/facewidget_music_transparency` (80, dark 40) | music widget card transparency seekbar | `aod.ga2` (applies alpha), `aod.tn1` (writes), `RestoreInstanceIgnoringSeekBar` |
| `System/aod_brightness_test_mode` / `debug_aod_brightness` | AOD brightness test mode | settings switch `aod.ti8`; `AlwaysOnDisplayEx` / `AODUiController` |

---

# Hidden activities & test-mode knobs (2026-11-24, sweep pass 4)

## I. "Knox AI" explained

`key_now_bar_com_samsung_android_knox_galaxyai` is **not** a Settings read  - 
`aod.b05.m1841h` fetches it via
`SemEnterpriseDeviceManager.getApplicationRestrictions("com.samsung.android.knox.galaxyai")`.
So it's the Knox-MDM **app-restriction bundle** for the Galaxy AI app
(`com.samsung.android.knox.galaxyai`), mirrored locally as a `key_now_bar_*`
toggle: enterprise policy decides whether the Galaxy AI Now Bar card shows on
AOD/lock screen.

## J. Hidden / test activities (59 total, 45 exported)

Internal (not exported, but present):

- `com.samsung.android.app.aod.setting.TestModeSettingActivity`  -  reads intent
  extras `fix_aod_position`, `aod_clock_type`, `locale_index`, `calendar_index`
  (force clock type / position / locale / calendar in previews!) and hosts
  `aod.TestModeSettingFragment` (was `aod.ti8`, 88 KB)
- `com.samsung.android.app.aod.setting.FontTestMainActivity`  -  font test UI
- `com.samsung.android.app.aod.setting.ClockScaleTestMainActivity`  -  clock scale test
- `com.samsung.android.app.aod.setting.sticker.StickerSettingsActivity`  -  AR sticker settings

Exported (launchable via `adb shell am start -n <pkg>/<activity>`):

- Now Bar per-app managers: `GoogleCommuteManagerActivity`,
  `NaverSportsFavoriteManageActivity`, `GoogleSportsFavoriteManageActivity`,
  `GoogleFinanceManageActivity`  -  each writes its `key_now_bar_<pkg>` toggle
- `FaceWidgetNowBarSettingsActivity` / `FaceWidgetNowBarSettingsMain`,
  `NowBarLiveNotificationSettingsActivity` / `...Main`, `FaceWidgetMusicSettingsActivity`
- `aodservice.settings.opreditor.ImageOprEditActivity`  -  image OP editor
- `CameraCoverClockStyleSettingActivity`, `ClockPackChooserActivity`, clock-style activities

## K. TestModeSettingFragment hidden test keys (aod.ti8 → aod.TestModeSettingFragment)

- `aod_brightness_test_mode`, `debug_aod_brightness`, `cover_screen_demo_mode`
- stretch animation tuning (relevant to the mod's stretch feature!):
  `aod_stretch_anim_accel_up`, `aod_stretch_anim_accel_down`,
  `aod_stretch_anim_max_speed`, `aod_stretch_anim_speed_scale`,
  `aod_moving_range_width`
- "AOD star" debug overlays: `aod_star_main`, `aod_star_clock`, `aod_star_noti`,
  `aod_star_additional_info`, `aod_star_charging_info`, `aod_star_clock_charging_info`
- `aod_light_reveal_alpha`, `aod_bold_font_test_mode`

---

# Rubin / Runestone (2026-11-24, sweep pass 5)

"Rubin" is Samsung's on-device AI engine (SDK `com.samsung.android.rubin.sdk`,
internal codename **Runestone**  -  see `RunestoneSleepApi`, `RunestoneManagerDump`).
In AOD it shows up as content providers + a fence broadcast API:

- Persona feeds (read via `content://com.samsung.android.rubin.persona.*`):
  `preferredmusic`, `commutingpattern`, `general.generic`  -  Uri holder classes
  `aod.ch5` etc.; consumed by ~28 `aod.*` classes → feed Now Bar cards
- Context events: `content://com.samsung.android.rubin.context.drivingevent`,
  `...refreshingevent`
- Fences: `com.samsung.android.rubin.fence.CONTEXT_FENCE_STATUS_CHANGED`
  broadcast → `AODFenceReceiver` (exported), which calls
  `rubin.sdk.module.inferenceengine.sleep.RunestoneSleepApi` /
  `SleepPattern` and writes `aod_sleep_state`, `aod_sleep_pattern_wakeup_time`,
  `aod_context_fence_not_working` → sleep/wake prediction drives AOD wakeup
  (`aod.SleepStateManager` = was `iz6`)

## Test-mode gate (confirmed)

`aod.wt6.isAodTestModeEnabled` (was `m12972e`) = `Settings.System "aod_test_mode_only" == 1`;
`isHijriTestModeEnabled` (was `m12975h`) adds `aod_hijri_test_mode`. The toggle UI
lives in `aod.AbstractActivityC0524la`, which writes `aod_test_mode_only` and
toasts the state  -  reachable when the build looks unreleased, then stays on
until disabled. Both keys are hidden Settings.System ints (adb-settable).

---

# Remaining key traces + Rubin→Now Bar wiring (2026-11-24, sweep pass 6)

## L. More traced keys

| Key | Drives | Consumer |
| --- | --- | --- |
| `System/aod_mode` (+`_start_time`/`_end_time`) | AOD on/off + schedule | `FaceWidgetSettingsMainPrefFragment` toggle (semGetIntForUser==1); observed by `SleepStateSettingObserver` |
| `System/aod_display_mode_auto` | AOD display mode "auto" radio (5-min hop; `..._no_hop_summary`) | `aod.C0114a5` display-mode prefs; gated by `f12825K || isMultiFoldMainDisplay()` |
| `System/aod_show_state` | AOD currently-shown state | `FaceWidgetViewModel`, `DigitalWatchfaceEffectAnimationClockView` |
| `System/aod_show_for_new_noti` | wake AOD for new notifications | `aod.AODPolicyFactory` (unless policy-blocked) |
| `System/aod_show_cover_screen_wallpaper` | cover-screen wallpaper on AOD | `AODSettingsMainPrefsFragment`, gated by `f12839R` |
| `System/cover_app_tray_enabled` | cover app tray switch | `aod.l38` settings, observer x38 case 3 |
| `System/large_cover_screen_apps` / `_navigation` | large-cover app list / nav | multi-widget settings adapters (`aod.jn4`/`qt4`) |
| `System/accessory_cover_uri` | smart-cover accessory attached | p38 case 22 |
| `Secure/cover_screen_show_notification_tip` | cover notification tip shown-once | p38 case 4, `aod.zy4` |
| `System/aod_multi_fold_sub_display_mode` (+start/end) | multi-fold sub-display mode (value 2 = ?) | `aod.MultiFoldSubDisplayModeController` (was `rv2`), `AodCommonSettingsUtils` |
| `Secure/scpm_subscreen_preload_policy_payload` | SCPM subscreen preload JSON (`package_list`) | `aod.ScpmSubscreenPolicyHandler` (was `v10`, log "ScpmSubscr") |
| `System/buds_enable` | Buds state on music Now Bar card | `FaceWidgetMusicViewModel` |
| `System/ai_flex_window` | Galaxy AI flex-window toggle | `aod.l38` + CVS106/CVSE1061 analytics |
| `System/sub_display_system_wallpaper_transparency` | sub-display wallpaper transparency (==2 check) | `aod.wt6` "SettingsUtils" |
| `System/aod_show_lockscreen_wallpaper` | wallpaper visible on AOD | `aod.C0114a5` switch |
| `System/lock_clock_adaptive_colors`, `System/face_widgets_cover` | read via SettingsHelper constants (no literal consumers found) |  -  |

Generic Android keys in the registry (no AOD-specific wiring needed): `zen_mode`,
`low_power`, `protect_battery`, `battery_protection_threshold`, `animator_duration_scale`,
`remove_animations`, `force_resizable_activities`, `accessibility_reduce_transparency`,
`notification_badging`, `badge_app_icon_type`, `navigation_mode`, `ui_night_mode`,
`doze_enabled`, `emergency_mode`, `ultra_powersaving_mode`, `minimal_battery_use`,
`device_provisioned`, `time_12_24`, `homecity_timezone`, `easy_mode_switch`,
`double_tap_to_sleep`, `fingerprint_always_on_type`, `n_digits_pin_enabled`,
`knox_finger_print_plus`, `selected_search_engine`, theme packages, etc.

## M. Rubin → Now Bar card wiring

- Uri holder classes: `aod.ch5` (persona.preferredmusic), `cz0` (commutingpattern),
  `ds2` (general.generic), `eh5` (preferredplaces), `fd5` (placepattern),
  `fs2` (preferredcontent), + context: drivingevent/refreshingevent holders
- `aod.h05`: maps package → Now Bar card + manager activity
  (`com.google.android.apps.maps_internal` → commute card / `GoogleCommuteManagerActivity`,
  quicksearchbox → `GoogleSportsFavoriteManage`, finance, NHN search…)
- `aod.ct5` = R layouts: `nowbar_commute_chip_view`, `nowbar_commute_expand_view`,
  `common_layout_ongoing_commute_normal/expand`, `common_layout_ongoing_finance_chip`,
  `nowbar_call_layout`, `google_commute_settings`, `google_finance_settings`
- `aod.b05`: builds the Now Bar app list, filters by Knox app restrictions
  (`key_nowbar_settings` bundle, `grayout`) + `selected_search_engine`
- `NowBarItemManager` (facewidget.domain, 73 KB): the item source  -  userId-checked
  NowBarItem updates from notifications/media/etc.

## Dressroom 3.0.10.38  -  ADB debug clock override channel (hidden)
- `…lockscreen.editor.item.core.util.debug.DebugClockSupportKt`: intent extra keys
  `debug_default`, `debug_reset`, `debug_clock_type`(int), `debug_clock_scale`(float),
  `debug_clock_font_type`(int), `debug_clock_blur_enabled`(int/bool), `debug_clock_font_weight`(int)
  + `getValue(Intent, DebugClockKey)` / `putAnyExtra(...)` helpers.
- `…editor.item.core.util.debug.DebugClockKey`: enum of those 7 keys with types.
- `…dressroom.presentation.preview.debug.AdbDebugCommandApplier.applyDebugCommand(Intent)`:
  normalizes any present debug extras into a new intent (returns null if none).
- `…preview.activity.EditMainActivity.mo3341e0` (onCreate, bundle==null): if
  `p189j3.AbstractC4097v.f13928g` (debug gate) and applyDebugCommand(getIntent()) != null →
  `setIntent(...)` + logs "Debug command detected from ADB". Also special-cases
  extra `from = "com.android.shell"` (adb shell am start …) with a 4s delayed runnable.
- `…editor.item.domain.usecase.debug.ApplyDebugClockDataUseCase.invoke(Intent)`: gated by
  `p292oc.AbstractC5795e.f18020a` (= Build.TYPE "eng"/"userdebug" check) + clock repo enabled;
  applies overrides onto ClockData (clockType, scale, fontType, blurEnabled, fontWeight) or resets.
- => Hidden feature: `adb shell am start -n com.samsung.android.app.dressroom/.presentation.preview.activity.EditMainActivity --es debug_clock_type 2 --es debug_clock_scale 1.5 --es debug_clock_blur_enabled 1 --es debug_clock_font_weight 700` on eng/userdebug builds live-overrides the AOD clock. Also two Dressroom-only settings keys: `high_text_contrast_enabled`, `minimal_battery_use` (repos `be.HighTextContrastSettingRepositoryImpl`, `be.MinimalBatterySettingRepositoryImpl`).

## Dressroom 3.0.10.38  -  exported entry points (29 exported components, all permission-gated except noted)
Hidden/unusual ones:
- `presentation.preview.activity.EditBaseActivity` actions: `SHOW_VIDEO_WALLPAPER_EDITOR`
  (video wallpaper editor UI!), `SHOW_LOCK_EDITOR`, `SHOW_LOCK_SETTINGS`, `SHOW_LOCK_FROM_LOCK_STAR`,
  `SHOW_LOCK_ACCESSIBILITY`, `SHOW_MULTIPACK_PREVIEW`, `TEST_LIVE_WALLPAPER_PREVIEW`,
  `SHOW_PRELOADED_LIVE_WALLPAPER_PREVIEW` (test/preview live wallpapers without setting them).
- `accessory.WallpaperCustomService`: `SMART_CARD_ATTACHED/DETACHED/REMOVE_CONTENT`,
  `WALLPAPER_CUSTOM_SET/UNSET/UPDATE`  -  cover-screen "smart card" accessory wallpaper channel.
- `receiver.DressRoomReceiver`: `REQUEST/RESPONSE_BACKUP|RESTORE_WALLPAPER_SETTING` (Smart Switch),
  `REQUEST/RESPONSE/PROGRESS_PREPARE|BACKUP|RESTORE_OSMOSIS_WALLPAPERS` (Osmosis = cloud wallpaper sync),
  `com.samsung.sea.rm.DEMO_RESET_STARTED` (retail demo mode), `RESET_WALLPAPER_SNAPSHOT_ACTION`,
  `INIT_FAVORITE_LIST`, `FAVORITE_LIST_CHANGED`, `LAZY_BOOT_COMPLETE`, theme-center apply hooks.
- `provider.gts.DressRoomGtsProvider`: `com.samsung.android.gts.action.GTS_CELL`  -  feeds GTS
  (Galaxy "task suggestion") cells; WallpaperSettingActivity also answers `GTS_SETTINGS`.
- `lockscreen.provider.ClockProvider`: `com.samsung.android.sdk.routines.v3.ROUTINE_PROVIDER`  - 
  AOD clock is controllable from Samsung Routines/automation.
- `provider.DressRoomEpisodeProvider`: action `com.samsung.android.intent.action.SCENE` (Episodes/scenes).
- `presentation.SetAsWallPaperActivity` + `utils.SetAsWallPaperActivity`: NO permission  -  exported
  `SET_AS_WALLPAPER`/`ATTACH_DATA*` handlers (set lockscreen wallpaper via intent).
- Others: `KeyguardCropActivity` (SET_LOCKSCREEN), `ShortcutPickerActivity` (SET_COVER_WALLPAPER*),
  `EditDexActivity` (SET_DEX_WALLPAPER*), `MyWallpapersActivity`, `FavoriteSettingActivity`,
  `ColorThemeSettingActivity` (COLOR_THEME_SETTINGS), `PersonPickerActivity`, `GenWallpaperActivity`
  (AI gen wallpaper), `WeatherLandingActivity` (WEATHER_LANDING / CHANGE_AS_WEATHER),
  `WallpaperLibraryActivity(+Settings)`, `WallpaperDetailSelectableActivity` (DYNAMIC/GRAPHIC/GALLERY/
  COVER_EMOJI/AI_WALLPAPER/SPECIAL/DEX/LOCAL_STORE/DOWNLOADED details), Settings search providers.
- Odd: an exported component literally named `com.samsung.android.intellivoiceservice` (WRITE_SECURE_SETTINGS)
  answering SCS ASR langpack/asset actions  -  voice-service shim inside Dressroom.

## Dressroom 3.0.10.38  -  local SharedPreferences keys (data.preferences.LocalPreferenceDataSourceImpl, ex-C2339a)
Hidden/test-ish: `is_daily_gradient_test_mode` (daily-gradient wallpaper test mode),
`test_multi_pack_max` (multi-pack preview limit), `ui_thread_monitor_trace`, `theme_is_trial`,
`trial_count`, `prompt_last_request`, `launch_color_theme`, `last_consent_checked_time`,
`weather_landing_page_count`, `history_id_list`/`history_key_list`/`history_option_map`,
`applied_color_themes(_groups)_(lock|home)(_sub)`, `is_standard_color_themes`,
`color_palette_app_icon_bnr`, `cached_bucket_data`, `theme_state_id`.

## Dressroom 3.0.10.38  -  hidden debug menu in WallpaperSettingActivity (eng/userdebug only)
`onCreateOptionsMenu` inflates `setting_options_menu`, then if `p453wb.DebugMenuVisibilityChecker.m9844p()`
(= `va.AbstractC7208a.m9501a() || m9502b(ctx)`  -  eng/userdebug or debuggable check) posts a runnable
that adds extra items handled in `onOptionsItemSelected`:
- `action_weather_test` → fires intent `com.samsung.android.wallpaper.intent.action.WEATHER_TEST`
- `action_daily_gradient_test` → toggles pref `is_daily_gradient_test_mode` + toast ON/OFF
- `action_category_ui_test_mode` → toggles pref `category_test_ui_enabled`
- `action_save_category_log_to_storage` → copies `category_log` dir to getExternalFilesDir (AutoCuration logs)
Also Knox lock profile: `…entity.profile.LockProfileData` (53 KB)  -  enterprise AOD lock profile with
clockType/scale/palette/adaptiveColor/fontType+weight+stretch/visibility/notifications/shortcuts/
contactInfo/gravity/margin  -  applied via p013ah.LockProfile*RepositoryImpl, gh.BuildProfileDataUseCase,
p103eh.LockProfileSolution, p128fh.SubProfileSolution, lb.RestrictionManagerImpl (Knox restrictions).

## Dressroom 3.0.10.38  -  Settings/Secure/System keys read by Dressroom (harvested from code literals)
`aod_state`, `aod_image`, `aod_background`, `aod_clock_frame_feature`, `aod_clock_informative_module_feature`,
`aod_notification_color_feature`, `aod_clock_support_weather`,
`lockscreen_wallpaper_transparent`, `lockscreen_notifications`, `lock_screen_show_notifications`,
`lockscreen_minimizing_notification`, `lockscreen_show_shortcut`, `lock_application_shortcut`,
`lock_application_shortcut_external`, `lock_data_bundle`, `lock_clock_color_data`, `lock_shortcut_color_data`,
`lock_widget_color_data`, `multi_test`, `clock_type`, `clock_color`, `clock_weather_location_exist`,
`cover_clear_view_rounded_corner`, `cover_theme_clock_date_color`, `cover_theme_clock_date_color_black`,
`cover_theme_use_dark_font_as_default`, `cover_category`, `cover_theme_use_image_clock`,
`theme_use_image_clock`, `current_sec_active_themepackage`, `current_theme_support_flipcover`,
`dualclock_menu_settings`, `nowbar_visible`, `wallpaper_image`, `wallpaper_preview_fake_view`,
`shortcut_default_component`, `set_shortcuts_mode`,
`knox_profile_id`, `knox_configuration_default_channel`, `knox_configuration_hun_channel`.

## Dressroom 3.0.10.38  -  video wallpaper editor + aodservice SDK surface
- Video lockscreen wallpaper editor: manifest action `SHOW_VIDEO_WALLPAPER_EDITOR` handled by
  `presentation.preview.KeyguardCropActivity` (+p284o4.C5702o, p286o6.AbstractC5735h);
  trim UI = `editorcomponent.component.wallpaper.video.view.TimelinePanel` → `VideoTrimmerPanel`
  + `SnapTrimPopup` (snap-to-point trim), impls in p393t7/p472x7.
- `sdk.aodservice` public SDK model (targets AOD app `com.samsung.android.app.aodservice`):
  `LockDataBundle` of `LockScreenItem`s = ClockItem | NotificationItem | ShortcutItem |
  ComplicationWidgets | ContactInformation | WallpaperBundlesItem | UnknownItem, with
  `StateIds`, signature tags (EditableColor/Position/Rotation, ItemID/ItemKey, LockStarItem,
  Removable/NonEditable), attrs (Display AOD, gravity, dims incl. RelativeArea/RootDimension),
  colors (AdaptiveColor(+HasValue/Shadow), ClockPresetColor, StaticColor), fonts, settings
  (ShortcutComponentData/ShortcutJsonData). Serializers in `store.serializer`,
  bundle builders in `store.access.usecases`.
- `clockpack.plugins.clock.util.ClockConstantsKt` feature flags: `aod_clock_frame_feature`,
  `aod_clock_informative_module_feature`, `aod_notification_color_feature`.

## Dressroom 3.0.10.38  -  wallpaper library data sources + editor panels
- Library sources (p072d4, all `extends AbstractC2962i`): DataSourceThemeStudio, CategoryDataSourceBase,
  DataSourceAnimals, DataSourceDownloaded, DataSourceTiltClockWallpaper (flip-cover emoji wallpapers),
  DataSourceGallery (bucket cache), DataSourceColorWallpapers, DataSourceFlipCoverInformative.
- Editor design panels (p098e7, extend EditorPanelBase): CoverDesignPanel, MainDesignPanel, GraphicalDesignPanel.
- Image filters in p043c7.ImageFilterPanel: Original, Cinepea, Sunbeam, Chill, Crystal (+more).
- Multi-pack preview: p333q7.MultiplePreviewPanelController.

## Dressroom 3.0.10.38  -  clock-pack plugin host (3rd-party AOD clocks)
- `clockpack.plugins.LockItemManager`: loads clock-pack APKs via broadcast action
  `com.samsung.android.clockpack.action.PLUGIN_LOCK_ITEM`, gated by SemFloatingFeature
  `SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_CLOCKPACK_ITEM` + `clockpackversion`; wraps them in
  `PluginContextWrapper` (own ClassLoader) and exposes `getTileFacade`/TileManager (tiles!).
- `clockpack.plugins.clock.ClockManager` (51 KB): queries plugins for ClockInfo  -  clockType, clockGroup,
  clockCategory, frameType/frameIndex (frame clocks), palette controller (adaptive colors/gradient count),
  font lists (isFontChangeableClock), graphical clock backgrounds, weather-info support, linear widget
  support, scale pivot. => Clock packs can ship fonts/palettes/frames/weather/graphical bgs to AOD.

## SystemUI 16.4006  -  AOD/lockscreen surface (pass 2026-10, mostly named code, few obf inner classes)
- AOD core in SystemUI is AOSP-named: `doze.AODMachine/AODUi/AODParameters/AODScreenBrightness/
  AODIntentService (exported; forwards intents to PluginAODManager)/AODManagerWrapper/AODOverlayContainer,
  doze.AlwaysOnDisplayPolicy (reads Settings key `always_on_display_constants`: prox_screen_off_delay,
  prox_cooldown_*, wallpaper_fade_out_duration, wallpaper_visibility_timeout, screen_brightness_array,
  dimming_scrim_array, wallpaper_dimming_scrim_array), power.feature.aod.AodTrigger* (CoverClosed /
  FullOrProtected / WirelessNoMove triggers), dreams.* (DreamOverlayService etc.).
- Plugin API `plugins.aod.*`: PluginAOD, PluginAODBaseManager, PluginAODFaceWidgetManager,
  PluginAODNotificationManager, PluginAODParameter, PluginAODSystemUIConfiguration, PluginTspEvent(+Callback)
  + plugins.keyguardstatusview.PluginAODStateProvider  -  the AOD app (com.samsung.android.app.aod)
  implements these; SystemUI consumes. `doze.PluginAODManager` (143 KB) also drives SubScreenManager/
  CoverScreenManager/KeyguardWallpaperController/LockStar data (getAODLockStarData)/SUB_ROOM_NOTIFICATION.
- Exported AOD-ish components: `doze.AODIntentService`, `keyguardimage.KeyguardImageProvider`
  (perm `com.samsung.systemui.permission.KEYGUARD_IMAGE`; register/unregister/set_dedicated_wallpaper_touch_regions;
  serves image/jpeg+png; creators: ClockImageCreator, WallpaperImageCreator(+Inject), Left/RightShortcutImageCreator,
  AbsShortcutImageCreator) + `wallpaper.video.VideoFileSaveService` (same perm, `com.android.systemui.provider.sepwallpaper`),
  `subscreen.SubHomeActivity` (action `com.samsung.android.app.aodservice.sublauncher.LAUNCH_WIDGET`),
  `dreams.homecontrols.system.HomeControlsRemoteService` (BIND_DREAM_SERVICE).
- Hidden dreams: `DessertCaseDream` (easter egg), `lowlightclock.LowLightClockDreamService` +
  `com.android.dream.lowlight.*` (LowLightDreamManager/LowLightTransitionCoordinator  -  low-light AOD mode
  driven by ambient light, `ambient.lowlight.AmbientSuppressionLowLightAction`).
- Knox LSO in SystemUI: `com.samsung.android.knox.lockscreen.*`  -  LockscreenOverlay/LSOContainerView/
  LSOItemWidget/LSOImage/LSOItemText/LSOItemSpace/ILockscreenOverlay + EmergencyPhoneWidget/BootBanner
  (enterprise lockscreen overlay widgets).
- Settings.Secure keys read by SystemUI (`util.SettingsHelper`): `aod_mode`, `aod_mode_start_time`,
  `aod_mode_end_time`, `aod_show_state`, `aod_tap_to_show_mode`, `aod_show_for_new_noti`,
  `aod_show_lockscreen_wallpaper`, `aod_light_reveal_alpha`, `aodlock_support_hijri`, `aodlock_support_lunar`,
  `lockscreen_wallpaper(_sub)`, `lockscreen_wallpaper_transparent`, `lockscreen_show_shortcut`,
  `lockscreen_minimizing_notification`, `lockscreen_sounds_enabled`; tuner `doze_always_on` (TunerServiceImpl),
  DozeParameters `doze_always_on`.
