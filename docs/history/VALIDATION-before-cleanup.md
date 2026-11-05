## Current: 0.14.0 artwork/stretch optimization — smoother motion user confirmed

Installed versionCode 105; SystemUI startup and exact-font/gradient-outline hooks confirmed under PID 19404. Build/v3 signing, 361 Android contract checks and eight native cache/outline checks pass. The user confirms smoother motion; compact flash remains resolved. Isolated contrast sampling improves from 62.95–107.03 ms to 2.05–4.48 ms per 20,000 calls. User exercise windows show median frame 13→10 ms and GPU p95 14→11 ms, but frame p95 25→29 ms and missed-deadline ratio 5.01→5.93%; unequal holding times, startup and idle frames prevent an overall jank/FPS claim. Full results/limits are in RELEASE_0.14.0.md. Temporary probe removed; keep-awake remains 0; no saved-setting changes.

## Historical: 0.13.11 compact-clock flash — user confirmed fixed

Installed versionCode 104. The user confirms “Flash is gone” after leaving the full player open and closing by the usual physical tap. The 0.13.10 trace captured the native compact BackgroundBlurDrawable being restored at +785 ms, before the correct stretched outline at +823 ms. 0.13.11 leaves that drawable attached and suppresses the host alpha, retaining the latest native alpha requests. Build/v3 signing and all 222 Android contract checks pass; startup/provider/artwork hooks confirmed under SystemUI PID 28804, with no fatal exception or unavailable hook in the inspected logs. Keep-awake remains 0; no wallpaper/settings changes. Four earlier real Samsung cache checks cover the unchanged native glyph guard. Stock non-fullscreen media and other devices are not newly certified. See RELEASE_0.13.11.md.

## Historical: 0.13.9 native glyph-cache guard — failed visual verification

Installed versionCode 102; SystemUI startup and native hook installation confirmed under PID 15834. Build/v3 signing, all 212 Android contract checks and four real Samsung glyph-cache/guard checks pass. Actual AOD ClockNonePaddingTextView returns its old cached path marked ready after a size change until onDraw. The runtime submission guard covers cq0.c and its direct callers, not only setClockTextBlur. The user still reproduced the compact-clock flash. The temporary native probe app was removed and keep-awake restored to 0. See RELEASE_0.13.9.md.

## Historical: 0.13.8 unresolved compact-clock flash

Installed versionCode 101. Build/signing and 212 isolated Android checks pass. The user's physical tap-to-close recording still reproduces the compact clock for roughly 64 ms over the already-stretched clock, after leaving the player open. 0.13.7 and 0.13.8 must not be described as visually fixed. The resumed review uses JADX MCP and the module source; no new build/restart has been performed. See MEDIA_RETURN_INVESTIGATION.md for source facts, rejected assumptions and remaining questions. USB keep-awake was restored to its original 0 after the recording.

## Historical candidate: 0.13.7 compact media blur retirement — failed user verification

VersionCode 100: build/v3 signing and 204 Android checks pass. 0.13.6's bounded trace shows a valid stretched native blur submission late in return, supporting stale compositor content as the remaining candidate; it does not identify every native callback. 0.13.7 invokes the native blur strategy's clear operation at saved-manual-height return without disabling blur. Installed and SystemUI reloaded. A >6-second-open Back-collapse test logs compact-outline cleanup, after-draw rebuild and saved-height restoration. Fine-frame inspection and user confirmation remain pending. Temporary trace code removed, USB keep-awake restored to 0. See RELEASE_0.13.7.md.

## Historical: 0.13.5 media-return glyph/blur synchronization

VersionCode 98: build/v3 signing and 200 Android checks pass, but the user reports the bug remains after the player is open for several seconds. Baseline recording reproduces the late compact-clock artifact. The user reported 0.13.4's pre-draw deferral still failed. Targeted native text-view inspection shows cached blur paths are updated inside onDraw; 0.13.5 coalesces the return rebuild until after child drawing. Runtime hook and manual restoration are logged under PID 329 after install/reload. Stock player unverified. USB keep-awake restored to 0. No wallpaper edits. See RELEASE_0.13.5.md.

## Historical: 0.13.3 rendering allocations and cache cleanup

VersionCode 96: build/v3 signing and 180 checks across eleven Android probe suites pass. Same-Dex isolated 20,000-call lookup benchmark, three trials each: previous build 5.48/5.48/5.54 ms and 3,211,264 B allocated; candidate 1.94/1.92/1.96 ms and 0 B reported by the Java runtime allocation counter. Geometry watchers reuse buffers while detecting parent transforms/scroll, dimensions and source changes. Shadow graph caching passes all input-change and cleanup checks. Installed and SystemUI reloaded once; build 0.13.3 logged. The foreground was another app, so further live phone UI tests were left for the user. No wallpaper edits or schema migration. Keep-awake remains 0. No GPU/frame-rate or global memory improvement is claimed. See RELEASE_0.13.3.md.

## Historical: 0.13.2 code-only bug audit

VersionCode 95: build/v3 signing and 142 checks across nine Android probe suites pass. Reproduced the fill-rule cache collision against the previous APK, then verified the fix. New coverage includes all primitive reflection arguments and optional detail-pass failures, alpha coverage and buffer ownership. APK installed and SystemUI reloaded once. Build 0.13.2 is logged in both scoped processes; the editor renders saved clock/widget depth. Dressroom was not force-stopped; fresh forced detection remains untested. No schema migration or saved-setting rewrite. See RELEASE_0.13.2.md.

The user confirms 0.13.1 substantially improves animation smoothness. No FPS gain is claimed from the earlier lookup microbenchmark.

## Historical: 0.13.1 media animation and render hot paths

VersionCode 94 installed; build/v3 signing and 77 Android checks pass. Native media-return animation from 0.13.0 is user-confirmed, with lag reported. This pass caches reflection resolution and rejects disjoint mask cuts without reducing rendering quality. The artwork blur display-list invalidation fix from 0.13.0 remains. Same-process lookup microbenchmark: 5,000 inherited calls, uncached 3.7175 ms versus cached 1.50776 ms. This is not a transition FPS measurement. SystemUI reloaded once, visible lockscreen depth and expanded media verified, manual-return completion logged. Independent AOD-process reload and subjective smoothness remain unverified. USB keep-awake restored to 0. See RELEASE_0.13.1.md.

## Historical: 0.12.10 World Clock idle depth

Installed versionCode 92: build/v3 signature and 40 Android checks pass. Final on-phone tests verify native World Clock drag/release with depth enabled, unchanged placement after enabling depth, idle clipping, and normal clock restoration on switching styles. Native test drafts discarded, original wallpaper reopened, USB keep-awake restored to 0. No SystemUI restart. See RELEASE_0.12.10.md.

## Historical: 0.12.9 selected World Clock identity

VersionCode 91: build/v3 signature and 40 Android checks pass. Selected type/font now follow the native editor model despite its CityClockView renderer. World Clock clip targets the actual visible city container. Visual verification is pending unlock. User confirms wallpaper-style detection for the current subject. 0.12.7/0.12.8 World Clock fixes failed. See RELEASE_0.12.9.md.

## Historical: 0.12.7 World Clock owner

VersionCode 89: final build/v3 signature and 36 Android checks pass. Adds active-controller renderer identity to prevent visible city providers clearing the World Clock profile. Post-fix World Clock visual confirmation is pending; the phone was on AOD. See RELEASE_0.12.7.md.

## Historical: 0.12.6 clock styles and weather source

Installed versionCode 88. Final signed APK passes eight weather/source ownership checks, eight clock clipping/ownership checks and 20 existing depth contracts. Calendar and Analog idle depth were visually verified; grayscale detection completes. Native rounded-frame layout was exercised. World Clock reuses the subject but still fails idle clipping after dragging. Weather integration, arbitrary frames and other-style runtime/AOD remain unverified. See RELEASE_0.12.6.md and the 0.12.4 startup incident recorded in RELEASE_0.12.5.md.

## Historical: 0.12.3 section separators and tinted blur review

Installed versionCode 85; build/v3 signature and SystemUI startup rendering pass after reloading scopes. Nine actual AOD wallpaper-tone checks and 16 Android touch/charging/widget lifecycle checks pass. Source traces establish the custom-tint versus wallpaper blur-curve difference; the user confirms section-only separators but still reports weak visible blur at radius 300. Earlier 100 JVM checks cover unchanged helpers. See RELEASE_0.12.3.md.

## Historical: 0.12.2 native settings pages and color/blur review

Installed versionCode 84. Build/v3 signature pass; all 100 JVM checks pass, including nine new opacity cases. Clock Layout/Effects and widget Layout were visually inspected on the phone. Color/blur is still under visual review. SystemUI/AOD rendering passed on 0.12.0 (same runtime code; later builds change UI). Landscape/tablet/foldable/A-series support is not newly claimed. See RELEASE_0.12.2.md.

## Historical: 0.11.5 charging liquid restoration

Installed versionCode 81, signed APK and SystemUI startup rendering guard pass. The user confirms 0.11.4 charging text survives card cycling. On the final APK, 18 real SystemUI Lottie asset checks and 16 Android touch/text/widget lifecycle checks pass. The user confirms stable liquid without the odd redraw and visible percentage text after card cycling. See RELEASE_0.11.5.md.

## Historical: 0.11.4 Now Bar and lifecycle corrections

Installed versionCode 80; signature/build and SystemUI startup rendering guard pass. User confirms gap swipe-to-unlock/direct pill dragging and reduced clock effects flashing in 0.11.3. 91 JVM checks and 16 new Android touch/charging/widget lifecycle checks pass. Final charging-text restoration after card cycling awaits user confirmation. Existing depth probes passed on 0.11.3. Blue rim report resolved as wallpaper content. See RELEASE_0.11.4.md.

## Historical: 0.11.2 automatic source-space depth

Installed versionCode 78, signed with the existing key; SystemUI rendering guard passed after reloading SystemUI/AOD. User confirms automatic detection, the recovered flower branches, live pan/zoom, and manual stretch/custom grid surviving Apply/reopen without opening Advanced. All 77 JVM checks pass; final candidate passes 20 new Android depth contracts, 16 Region checks and 7 clipping/blur groups. The actual file provider passed a 1.2 MB roundtrip with profiles unchanged. Broader AOD FPS, rim, other subjects/devices and video are not claimed verified. See RELEASE_0.11.2.md.

## Historical: 0.10.4 rendering/lifecycle audit

Installed versionCode 75. Build and v3 signature verified; SystemUI rendering guard passed. The user confirms the empty-gap Now Bar drag fix, smoother media blur/widget reveal, adaptive clock/glyph contrast, smooth return colors and restored depth after closing. All 66 JVM checks pass, with 16 Android Region checks, 7 Android clipping/blur groups, and 10 mask/contrast checks, including a final Android probe run against 0.10.4. A residual native blur redraw remains. See BUG_AUDIT_0.10.md for findings and explicit coverage limits.

## Historical: 0.9.7 media restoration

Historical installed build: 0.9.7, versionCode 70. SystemUI reload passed the rendering guard. The 0.9.6 recording verifies expansion and artwork blur behind controls. The 0.9.7 phone recording exercised the user's outside/clock close gesture: shrinking artwork stays visible across the compact bar, and the saved tall manual heights return afterward. The user confirmed both corrections. All 66 existing JVM checks pass. Broader transition cases remain pending. See RELEASE_0.9.7.md.

Historical 0.9.6: versionCode 69. Awake capture shows fullscreen artwork behind native clock/title/artist/controls. Recorded expansion shows artwork growing during the native spring; the controls blur the artwork instead of sampling the flower wallpaper. The user subsequently reported shrinking artwork disappearing behind the bar and manual stretching remaining flattened after opening media; corrected in 0.9.7. Charging-card retention is user-confirmed; custom native text card and global settings persistence were inspected on the phone. See RELEASE_0.9.6.md.

Historical 0.8.4 verification: installed versionCode 62. Deferred installation completed in SystemUI and AOD main/provider processes; SystemUI renders again and screen-off/on completed. All 66 JVM checks pass. Existing 16 Android geometry checks cover unchanged geometry. Cyan rim and detailed live-mask/manual transition checks remain open. See RELEASE_0.8.4.md.

## Historical: 0.8.3 recovery, runtime hooks bypassed

User confirms SystemUI recovered. Installed versionCode 61. Runtime effects in SystemUI/AOD are inactive; saved settings retained. 0.8.2 caused a startup stall and must not be considered runtime validated. See RECOVERY_0.8.3.md.

## 0.8.2 animation and mask edges

Direct Region cuts reduce the saved-mask first-cut probe from about 37 ms to 1.6 ms; 16 Android checks pass. Cropped detection is restored after the 0.8.0 single-leaf regression. Nested canvas mask passes are suppressed. Actual animation, live-crop and cyan-rim checks remain pending. See RELEASE_0.8.2.md.

## 0.8.0 live masks and performance

Installed versionCode 58; 54 JVM checks and 16 native Android geometry checks passed. User confirms noticeably smoother AOD transitions. Live editor crop and immediate depth/manual transitions await direct verification. See RELEASE_0.8.0.md for benchmark scope and remaining checks.

## 0.7.8 detailed subject depth — user confirmed

Signed versionCode 57 installed. The user confirmed the unapplied flower is detected correctly and depth persists after release. Device trace reports native Path intersection failure for the detailed flower mask; the normal bitmap draw path had worked, while native blur previously fell back to an uncut clock. Region subtraction now handles that failure. Eight minimal Android rasterizer checks passed on the phone, in addition to 54 JVM checks. Free/snap widget dragging and native picker taps were tested; dock-offset Apply/reopen and all runtime transitions still need broader checks. See RELEASE_0.7.8.md. The 0.7.6 binding-only attempt was user-reported unsuccessful.
## 0.7.6 widget dragging and candidate wallpaper scan

Signed versionCode 55 installed with a permitted Dressroom restart. All 54 Java checks passed. Phone tests verify free group dragging, sliding along Right attachment and tap-to-open native picker. The user confirmed detection follows the unapplied flower in 0.7.5; reading the previously applied wallpaper was the wrong-source cause. The user also reported interaction-only depth; 0.7.6 adds replacement clock-owner binding and inherited idle-blur mask lookup, pending visual confirmation. See RELEASE_0.7.6.md.
## 0.7.4 native widget integration

Signed versionCode 53 installed; SystemUI reloaded. All 51 Java checks passed. On-phone UI and logs verify scrolling, native 2×4 vertical rotation of existing 2×1/1×1 widgets, unchanged host IDs, both native saved coordinate sets, fresh-editor restoration, and vertical arrangement on settled AOD and awake lockscreen. Independent widget depth remains Off. The expanded standalone app_process probe was killed and is not counted as passing. Other provider variants, all custom geometry combinations and cover screens remain unverified. See RELEASE_0.7.4.md for scope and capture evidence.
## 0.7.1 widget customization

Installed versionCode 50. Java checks: 30 existing plus 16 widget layout checks; Samsung device probe: 53 checks. Runtime logs confirm widget hooks installed and selected profile reads depth Off (mode=2). The user reported broken vertical sizing in 0.7.0; 0.7.1 corrects the baseline scaling and container dimensions, but visual confirmation is pending. See RELEASE_0.7.1.md.

## 0.6.6 confirmed widget depth

The user confirmed 0.6.6 working. Before the fix, native owner/clip logs were correct but both fully covered blur backgrounds remained visible. The update removes the native blur surface when subject subtraction produces an empty visible path, retaining its original info for restoration. Signed build and on-phone full-occlusion/restore checks passed. After SystemUI reload, captures verified the awake lockscreen without the two leaked rectangles and the settled AOD with retracted, partially visible widgets. See RELEASE_0.6.6.md.

## 0.6.5 native widget ownership

The user reported no improvement in 0.6.4. A new JADX MCP trace establishes that widget content/background updates have their own controller, while the module allowed temporary clock views to clear the shared widget mask. 0.6.5 removes those clock-to-widget writes and binds runtime depth to the native controller's actual container and selected clock record. Signed build and on-phone native controller/transition/provider signature checks passed. Visual validation after reload is pending. See AOD_WIDGET_PIPELINE.md and RELEASE_0.6.5.md.

## 0.6.4 cached widget content and AOD transitions

The user confirmed that saved clock placement, manual stretch and depth restore in 0.6.3. Widgets still retained UI fragments across AOD transitions. 0.6.4 replaces their runtime canvas cutout with a render-node outline clip, updates it on geometry changes, and reconnects replaced view-tree observers. Signed build and on-phone outline/path/full-occlusion/native blur/font checks passed. An offscreen GPU pixel probe was terminated and supplied no visual result. End-to-end visual testing is pending; see RELEASE_0.6.4.md.

## 0.6.3 widget transitions and runtime font restoration

0.6.2 failed the user's widget background check. The user reported different occlusion while entering AOD versus waking. A settings backup and SystemUI dump show saved depth/stretch records intact, but the native startup font handoff has null heights. 0.6.3 restores saved heights into that handoff and clips final complication blur submissions with independent copies of native blur info. Build/signature checks and all 30 Java contract checks passed; on-phone framework/font checks exercise the actual Samsung classes. Visual checks after process reload are pending. See RELEASE_0.6.3.md.

## 0.6.0 / 0.6.1 lifecycle and native blur depth

Java contract checks passed: reflection 6, identity locks 6, frozen saves / wallpaper scoping / scene matching 14 (26 total). The Samsung phone platform probe passed native Path subtraction, background clip API availability, and installed Dressroom weather/save plus AOD blur-hook signatures. Both APKs compiled, passed v3 signature verification and installed successfully.

The user confirmed that 0.6.0 keeps clock depth while idle in Dressroom. Logcat confirms that the native combined blur path was clipped. The remaining weather reload mismatch was corrected in 0.6.1 by restoring both native weather values and synchronizing the restore value with the native use case; the user confirmed that weather restores correctly after Apply/reopening.

The user confirmed post-reboot depth on both lockscreen and AOD, and no duplicate after date/weather/blur/shadow edits and tab changes. AOD's initial clock appearance corrected after reapplying; complication contents were masked but their native blur backgrounds remained in front of the subject.

0.6.2 targets those complication backgrounds. The on-phone geometry probe passed rounded background subtraction and non-overlap preservation, and verified installed native complication methods. The 26 Java contract checks and signed APK build passed. Visual validation after reboot is pending; see RELEASE_0.6.2.md.
# Validation — 0.5.6 alpha, 2026-10-24

On 0.5.5 the user reported that the gray duplicate still appeared after turning Custom shadow off and that the Weather information switch showed Off after reopening while AOD weather remained On. The captured editor showed saved shadow offset 27 and softness 4. Inspection of `DigitalHorizontalClockView.d0` showed that it clears native shadows only when passed `null` or a style with color zero; 0.5.5 left a nonzero color when its radius became zero. Version 0.5.6 explicitly clears that native shadow path. It also migrates older weather-only records that lack the new editor switch mirror. Both fixes await the user's visual check.

The 0.5.5 redraw helper now temporarily removes Dressroom's fixed-time guard during a synchronous provider update, then restores it. `AbsClockView.g()` otherwise skips the update. This path compiled and installed, but its visual effect has not been isolated from the native shadow issue.

The user confirmed on 0.5.2 that Weather information and Manual stretch retain their values across tab switches, and the date-toggle overlap no longer appears. Version 0.5.3 broadened the surface refresh to every edit and tab entry; the user reported a new first-tab artifact and lost weather-switch state. Version 0.5.4 reverts only that broad refresh, restoring the 0.5.2 implementation with a higher versionCode for update installation.

Version 0.5.1 introduced an editor redraw that invokes the AOD provider's time-update method directly with the preview's fixed time, bypassing the fixed-time early exit in `forceRefresh()`. Version 0.5.2 then separates shadow removal and restoration across frames; the user confirmed the date overlap disappeared.

The original 0.4.7 APK was decompiled locally and compared with the rebuilt baseline. Every module source class decompiled identically after restoring the 0.4.7 date-toggle and clock-binding behavior and removing unfinished 0.4.8 blur edits. The new blur work starts from that verified baseline.

The 0.5.0 APK compiled, passed Android signing verification, and installed as versionCode 18 on the connected Samsung. The 12 reflection and identity-lock checks passed. Radius and text-visibility controls follow the AOD provider's `aod.tx0.b` and `aod.cq0.d` methods inspected through JADX MCP. Runtime blur rendering on the lockscreen and AOD awaits the user's visual check after reboot.

The weather-only mismatch in Dressroom's main preview remains deferred at the user's request.

The user reported both position and stretch matched the preview after Apply and reopening on 0.3.2. The fix combines Samsung's native scale/position updater, current-scale stretch calculation, and an editor hook that restores the saved manual stretch guard before the clock renderer loads its font. 0.3.3 removes temporary per-slider geometry diagnostics only. It built and installed successfully. The date and weather controls write Samsung's native `ClockData` fields; Dressroom's save converter writes them into the native clock item, so they do not depend on the module's shadow settings provider.

The build was signature-verified and installed as an update on the connected Samsung device. LSPosed loaded the Dressroom editor hook and runtime clock provider hooks. The device log recorded:

```text
Native Advanced tab hook installed, build 0.3.0
Provider manual stretch hooks installed: DigitalHorizontalClockView
Advanced settings committed after native save
Committed clock shadow applied to LOCK_SCREEN
```

The user confirmed the shadow is now visible on the actual lockscreen and AOD. Previously, it rendered only in Dressroom's preview. This validates the save callback and runtime rendering path together.

The user also confirmed the native third tab displays correctly, independent manual stretch values yield visibly different digit heights on a supported clock, and the position controls work. Earlier builds had a shadow failure on the user's font; 0.3.0 adds the runtime rendered-silhouette effect for that case.

Build checks: javac, D8, aapt, zipalign, APK signature verification (v3), and ADB update install succeeded. Reflection and identity stretch-hold tests passed (12 checks total). The module uses API 102 metadata and scopes Dressroom, SystemUI, and Always On Display.

Compatibility remains scoped to the inspected Dressroom 3.0.10.48 and Samsung clock provider. See README.md for current limits.
