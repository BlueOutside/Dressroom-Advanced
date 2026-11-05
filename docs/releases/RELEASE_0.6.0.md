# 0.6.0 lifecycle and depth integration

**0.6.1 follow-up (versionCode 43):** after 0.6.0 the user confirmed that clock depth now persists while idle. Weather still reloaded incorrectly inside Dressroom while AOD had the saved value. Version 0.6.1 restores both `ClockData.showWeatherInfo` and `ClockAssistData.showWeatherInfoReservedValue` at clock construction, then publishes native remeasurement. A hook on the native weather use case updates the editor's restore value for changes made in either tab. The user confirmed that weather restores correctly after Apply and reopening. 0.6.1 is compiled, signature-verified, installed, and awaiting post-reboot AOD/lockscreen and broader artifact results.

Built, signature verified, and installed as versionCode 42. Dressroom was force-stopped to load the new hooks on reopening. SystemUI/AOD need a reboot before their 0.6.0 behavior can be assessed.

## Changes

- Depth now subtracts the saved subject region from Samsung's combined glyph/weather blur path before `setBackgroundClipPath`. This covers the separate native blur host, in addition to normal clock/widget drawing. The mask-to-path conversion is retained with the active entry; subject detection does not run per frame.
- Removed the experimental subject-foreground overlay. It is not used on the lockscreen or AOD.
- Editor settings are staged by scene/type/font. Saved shadow/manual values are restored once when a selection loads; opening Advanced no longer writes the stored values back into the model.
- Weather changes use the actual `vo.g` transaction used by the native control: reserved value, pre-layout measurement, actual native value, and changing/idle flags. Date-order changes use the corresponding native transaction. Hiding/showing date requests native weather-row remeasurement.
- Advanced always reacquires the current provider renderer before applying changes. Retired renderer refresh work is canceled. New editor/scene generations invalidate old depth and refresh callbacks.
- Removed the provider-timer construction and temporary fixed-time manipulation from editor redraws. Refresh uses layout/invalidation and native text-blur regeneration after the native edit transaction.
- Native Apply flushes pending Advanced edits and freezes the extras snapshot; successful save commits that snapshot, not a later draft. Invalid panel/selection identity skips committing stale extras while preserving native save behavior.
- Preferences are keyed by wallpaper ID plus clock type/font, with native scene metadata checked for editor reads. Legacy settings migrate once to the current wallpaper. A wallpaper identity lookup falls back to the system wallpaper ID if the lockscreen shares that wallpaper. Old running clients without the new query field are supported by the provider during the reboot transition.
- Runtime hooks are selected by execution process, so loading AOD's provider APK inside Dressroom does not activate committed-runtime overrides in the editor.
- AOD/SystemUI depth rebinds on new clocks, layout, committed settings, and doze transitions. A cached temporary-font fallback cannot cross wallpaper IDs.
- Custom shadow is composed with the latest native RenderEffect and restores that effect on disable. Editor radius/offsets use the same scale normalization as native binding/runtime. Restore snapshots explicitly isolate the nested shadow value.
- Depth records whether it created a manual hold. Disabling depth releases a hold it created while preserving a pre-existing manual hold. Older saved depth/manual combinations are preserved conservatively because their original ownership is unknown.
- Optional blur hooks roll back partial installation so a failed installation does not leave half-installed hooks registered as complete.

## Verification

- Reflection contract: 6 checks passed.
- Identity stretch holds: 6 checks passed.
- Save snapshot / wallpaper scope / scene matching: 14 checks passed.
- On the actual phone, an `app_process` probe passed Samsung `SemBlurInfo.Builder.setBackgroundClipPath` availability and native `Path.Op.DIFFERENCE` geometry.
- The phone probe verified the installed Dressroom weather coroutine, clock-panel change method, native save entry, and the installed AOD blur-owner/path hook signatures.
- Java compilation, DEX generation, APK packaging and v3 signature verification passed.
- On reopening Dressroom, logcat confirmed 0.6.0, native blur subject clipping installation, and provider manual/blur hooks. It then recorded `Depth applied to native blur path: DigitalHorizontalClockView` and successful native-save extras commits. The user confirmed idle editor depth. The 0.6.1 weather reload correction is also user-confirmed. Broader artifact and post-reboot runtime depth results are pending.

## Remaining limits

- This implementation targets the inspected Dressroom/AOD versions and the primary static lockscreen wallpaper. Scanning an unapplied candidate wallpaper, live wallpapers, and automatic multipack scene changes are not validated.
- Wallpaper ID scoping prevents an old subject mask from silently following a different wallpaper. A wallpaper reapplied outside this editor can receive a new ID; it may need the extra settings reapplied. Full native profile identity discovery across every external profile switch is not implemented.
- The editor validates scene metadata, but the current persistent key has one extra-settings profile per wallpaper/type/font. Independent multipack scenes sharing that identity are not supported.
- The detected subject contour quality is unchanged; the corrected render integration cannot recover details the detector missed.
- Native idle delays/animations remain. Testing must check the settled state as well as the interaction itself.

Rollback artifacts: original installed `dressroom-advanced-0.5.23-alpha.apk`, source backup `work/before-lifecycle-fixes-0523.zip`, and preference backup `work/settings-before-060.xml`.
