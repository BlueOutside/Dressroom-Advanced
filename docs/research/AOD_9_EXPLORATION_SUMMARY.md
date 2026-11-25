# AOD 8.9.66.1 exploration summary (2026-11-24 session)

Reference APK loaded in jadx: `com.samsung.android.app.aodservice` 8.9.66.1
(versionCode 896601000), 15,426 classes. **273 renames applied via the jadx MCP**
across 17 batches  -  chronological record with evidence: `JADX_RENAME_LOG.md`;
canonical alias tables: `AOD_9_ALIASES.md` (passes 1-12); hidden-feature inventory:
`HIDDEN_FEATURES.md`.

## What got renamed (by area)

- **Clock blur pipeline** (`aod.tx0/cq0/dq0/ec0/xb0/pv2/ac0/bc0/zb0/mb0` +
  helpers): `CombinedBlurStrategy`, `ClockBlurManager`, `ClockBlurStateMachine`,
  `ClockBlurApplyResult` family, `ClockBlurParameters`, `ClockBlurDebugOverlayView`,
  `WallpaperBrightnessInfo`, `ColorCurveParams`, `AodDebugFlags` members, etc.
- **Clock view layer**: `AbsClockView` palette/shadow/blur surface (74 members),
  `ModuleClockView` widget-host fields, `ClockFaceView`/`ClockFaceCustomView`/
  `Digital*`/`Analog`/`City` methods.
- **Stretch & Now Bar**: `RunStretchMeasureProcessUseCase`, `ClockStretchPhysicsAnimator`,
  `NowBarStackCardCallbacks`, `SetNowBarMediaExpandStateUseCase`,
  `ClockAnimationStateManager`, `SubLauncherClockActions`, layout listener `aod.xp0`.
- **Settings & policy infra**: `aod.rt6` → `SettingKeyEntry`, `z82` cache methods,
  `SettingsHelper.settingsCache` + readers, `aod.df6` → `ScpmAodPolicyManager`,
  `aod.AbstractC0814t1` → `AodCommonSettingsUtils`, `aod.ha6` → `DeviceFeatureFlags`
  (26 flags), `aod.bj1` → `AodDebugFlags` (28 flags), `aod.xi1` → `BuildTypeFlags`.
- **Settings UI / test**: `aod.C0336g7` → `AODSettingsMainPrefsFragment`,
  `aod.ti8` → `TestModeSettingFragment`, `aod.iz6` → `SleepStateManager`,
  `aod.C0409i6` → `RunestoneManagerDump`, `aod.nb2`/`ga2`/`tn1`/`s66`/`la2`/
  `C0372h6`/`jz6` → settings fragments & helpers (batch 17).

## Hidden stuff found (details in HIDDEN_FEATURES.md)

- 107 unique registered Settings keys; only ~11 exposed in search index
- `now_bar_aod_enabled` + 13 per-app `key_now_bar_*` toggles; Knox AI card gated by
  `SemEnterpriseDeviceManager` app restrictions (`com.samsung.android.knox.galaxyai`
   -  internal-only package, not public)
- SCPM (Samsung Cloud Policy Manager) flow: `aod-common-policy-8f16` XML with
  `nowbar_commute` tag at `Secure/scpm_aod_common_policy`
- eng/userdebug-only debug wall (`AodDebugFlags`), hidden test UI
  (`TestModeSettingActivity` + `TestModeSettingFragment` with `aod_stretch_anim_*`,
  `aod_star_*`, `fix_aod_position`/`aod_clock_type`/`locale_index`/`calendar_index`
  extras; gate keys `aod_test_mode_only`, `aod_hijri_test_mode`)
- Rubin/Runestone on-device AI: persona/context providers → Now Bar cards;
  `RunestoneSleepApi` context fences → AOD sleep/wake (`aod_sleep_state` etc.)
- `dls_state` bitmask (LockStar/SGG: bits 16/64/8192), Rubin fence receiver,
  AR sticker provider, Dressroom editor provider, clockpack `lock_settings/*` lists

## Deliberately not renamed

Samsung mega utility classes (`aod.p67`, `aod.w06`, `aod.gv8`, `aod.z82`, `aod.x38`,
`aod.al1`, `aod.ui1` …)  -  only their feature-specific members were aliased, same
convention as the previous sessions. Synthetic `KProperty[]` fields and members with
no distinguishing evidence were left raw and noted in the log.
