# Current validation

## Native 8.5 media depth: 0.20.1 / versionCode 132

The user reports portrait media clock cuts on the P613. Source inspection confirms that 8.5 updates the native clock media flag without reaching the module's 9.0 artwork-driven depth suspension. The fix follows the native 8.5 flag and actual page return animator; it does not modify saved depth profiles or add artwork/controls layers. The signed build, 152 JVM checks, 17 shared tablet Android suites (457 checks), Clock Style binding probe and read-only 8.5 AOD media binding checks pass. Ten new return cases cover suspension, delayed native animator creation, completion, interruption/reopening, absent animator and failed lookup. Version 132 is installed on the tablet. SystemUI starts normally and confirms the 8.5 native media depth lifecycle hook is installed, with no hook exception in the inspected startup log. The user confirms the player clock has no wallpaper-subject cuts in portrait or landscape, and wallpaper depth returns correctly after closing the player. No Dressroom draft was discarded. See [the 8.5 media map](research/MEDIA_8.5.md).

## Initial 8.5 AOD shim: 0.20.0 / versionCode 131

Core source/measurement, depth-blur ownership and widget depth mappings are added for AOD 8.9.32.95. The matching Dressroom 2.9.60.51 and the AOD APK are loaded read-only from temporary files on the tablet, not installed. Their binding checks pass, including the different `FaceWidgetViewModel.y()` stretch check, `FaceWidgetClockPage.w()` measurement, `aod.Lv` blur owner, `rF.c` clipping and `yi` widget submissions. The reference 9.0 source/blur/widget declarations also resolve after extracting the shared blur contract. The provider probe exercises native strategy-to-clock field traversal and rejection of non-view holder contents for both AOD APKs without constructing services. Clock Style passes its existing binding probe and 17 shared Android suites (447 checks); the AOD-only shader suite is excluded. All 152 JVM checks pass. This is ABI and shared-logic evidence, not a working AOD-device certification. No tablet app or SystemUI process was changed for these checks. Custom clock blur controls, glyph-cache optimizations, keep-stretch AOD transitions and fullscreen artwork remain unported on 8.5.

## Clock Style backport: 0.19.1 / versionCode 130

The signed build and 152 JVM checks pass. The P613 passes 17 shared Android suites (447 checks); the reference AOD-only song shader suite is excluded. Read-only binding resolution against its installed Dressroom/Clock Style APKs confirms the source getters, wallpaper reset and six measurement dependencies. Source inspection in JADX confirms the 8.5 measurement/refresh callers. This does not establish visual behavior after update, rotation or media transitions. The build is installed on the P613. The new SystemUI process starts normally and logs successful native font source/measurement synchronization plus the saved widget depth mask; no hook exception appears in that startup log. The user confirms saved clock stretch and widget depth remain correct through portrait/landscape rotation and lock/unlock, without opening Advanced or reapplying. Dressroom was not restarted, so its open draft is preserved. See [the change record](releases/RELEASE_0.19.1.md) and [shim folders](compatibility/SHIMS.md).

## Native media motion: 0.19.0 / versionCode 129

The user confirms 0.18.11 also behaves correctly with the framed player, apart from widgets appearing early during close and the fullscreen renderer missing Samsung's song-change motion. The follow-up coordinates framed-player widget contents/backgrounds with native artwork return and mirrors the installed native reveal/zoom/lighting in fullscreen, including its controls blur. Signed build, 11 JVM suites (152 checks), 18 Android suites (463 checks), and read-only reference APK binding resolution pass. The actual installed shader is exercised by GPU pixel checks; the first check caught a frozen uniform snapshot, which was corrected before installation. Installed on the reference phone; SystemUI restarted normally and its new process reports the runtime provider/artwork bindings without a hook exception or AndroidRuntime crash. The user confirms the framed-player closing widget fade and the fullscreen song-change reveal, including matching controls blur. Two follow-ups are explicitly deferred: widgets still disappear immediately on opening, and the fullscreen shimmer/reveal needs visual tuning for its larger area; the framed version currently looks better to the user. No saved profile was edited and the USB keep-awake setting remains at its original 0. The clock source/animation files are unchanged. See [the change record](releases/RELEASE_0.19.0.md).

## Closing reveal follow-up: 0.18.11 / versionCode 128

The user confirms 0.18.10 keeps the cover above the pill, and identifies early shortcut appearance plus incoming compact-title overlap. The follow-up gates those native content layers from the close-state callback and fades them in during the final part of artwork return, preserving the latest native opacity. Signed build, 11 JVM suites (152 checks), 17 Android suites (447 checks), repository/layout/inventory and whitespace checks pass. Installed on the reference phone; the user confirms delayed shortcuts, improved compact-title reveal and no noticeable flash. The clock fix remains confirmed. Original USB keep-awake was restored to 0 and phone-only captures were cleaned after preserving local evidence. Clock source/animation code remains the confirmed 0.18.8 version. See [the change record](releases/RELEASE_0.18.11.md).

## Artwork follow-up: 0.18.10 / versionCode 127

Keeps the user-confirmed clock fix from 0.18.8. The previous artwork candidates did not resolve the user's report. A recording of the physical close was extracted on-device and reviewed, including the controls fade and shrinking artwork crossing the pill. The foreground exclusion now follows inherited opacity and the blur band retains its measured cover-relative position during collapse. Signed build, 11 JVM suites (152 checks), 17 Android suites (438 checks), layout/repository/inventory and whitespace checks pass. Installed on the reference phone; visual confirmation is pending. See [the change record](releases/RELEASE_0.18.10.md).

## Native source follow-up: 0.18.8 / versionCode 125

Reverts the rejected 0.18.7 clock timing override and thumbnail-only crop. The native source getter now receives committed manual heights, so native stretch eligibility and height measurement can agree with the visible clock. The artwork return copy is inside the stable native card parent, below expanded and normal content, and does not inherit the expanded content fade. Signed build, 11 JVM suites (152 checks), 17 Android suites (435 overlapping checks), repository/layout, whitespace and generated inventory checks pass. The production return view is exercised beneath real Android child views, including the expanded-content fade. Installed on the reference phone; SystemUI startup is clean. The user confirms the clock animation is fixed. The artwork rectangle over controls and artwork going behind the pill remain; these are not fixed by the card-parent copy. No editor edit/Apply has been performed during this update test. See [the change record](releases/RELEASE_0.18.8.md).

## Media transition follow-up: 0.18.7 / versionCode 124

The reference phone passes 11 JVM suites (152 checks) and 17 Android suites (634 checks, with overlap between suites). The new media cases cover progress in both directions, stale fractions after settling, doze precedence, and the actual foreground artwork return view rendered into a bitmap. The drawing-failure test now expects the retained crash guard to contain a broken consumer inside dispatchDraw. Repository/layout and generated inventory checks pass; the APK verifies with the existing v3 signing key.

Version 124 is installed on the reference phone and SystemUI restarted normally. No clock edit or Apply was performed. The first opening capture contains an intermediate stretched clock and then the settled compact clock. Native logs show the artwork-running flag dropping at about 0.09 clock progress, while the clock's own animator continues through intermediate values to 1.0. This confirms the independent timelines on this device. The user subsequently reported both original issues remain, plus a broken thumbnail return and a vertical clock bounce. The candidate is rejected; fractional log progress did not prove visual correctness. Its clock timing override and thumbnail crop are being reverted. the injected close tap did not close the player and its captures are not evidence of a successful close. The earlier pre-update trace was captured after the user had touched the clock to repair it temporarily. See [the change record](releases/RELEASE_0.18.7.md).

## Widget lifecycle audit: 0.18.5 / versionCode 122

The signed build passes 10 JVM suites (129 checks), 16 Android suites (407 checks) and the updated read-only tablet binding probe, including the native wallpaper-color handoff. Twelve new JVM cases cover refresh coalescing, cancellation across reinitialization, reentrant callbacks and recovery from a failed refresh. Five new real-view Android cases cover root listener rebinding, replacement and release. Native wallpaper callback/context declarations also match the original 9.0 AOD DEX; the phone has not executed this build.

Version 122 is installed on the P613, with permission to discard the editor draft and reload Dressroom/SystemUI. The new SystemUI process logs successful Clock Style/core hook installation and reads the saved widget mask; no module startup exception appears in that process's inspected log. On 2026-11-04, the user confirmed widget size, placement and depth remain correct after repeated editor opens, portrait/landscape rotation and lock/unlock on the P613. External wallpaper replacement without a clock-type change still needs device validation. See [the source audit](research/AUDIT_8.5_LIFECYCLE.md).

## Editor panel follow-up: 0.18.4 / versionCode 121

The user confirmed 0.18.3 portrait-first Apply/reopen works. Its widget depth policy controls were not visible: the 8.5 panel created only two tabs, while Appearance remained assigned to page 2. The follow-up adds the Effects tab and rejects unreachable page groups. The color mismatch was traced by the user to automatic color selection; native colors remain unchanged.

Signed build, 117 JVM checks and all 15 Android suites (402 checks) pass on the P613. Eight new real-view checks reproduce a measured scroll viewport clipped by its parent and verify that its last complete row can reach the visible bottom, including after scrolling and repeated resizing. The final source passes repository/layout, generated inventory and whitespace checks. Version 121 is installed and Dressroom restarted with permission to discard the draft. SystemUI was left running. On 2026-11-04, the user confirmed that Widgets ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ Effects exposes Subject depth and Depth applies to, the controls work, and the longer Clock ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ Advanced tabs scroll far enough to show the complete last row. These UI fixes are user-validated on the P613; this does not extend validation to other devices.

## Orientation follow-up: 0.18.3 / versionCode 120

The user confirmed 0.18.2's saved portrait and landscape results work in Clock Style after applying from landscape, but reported manually enabling portrait depth and an unreliable portrait-first save. Inspection confirms distinct saved portrait/landscape clock and widget records, each clock record retaining its own baked mask and source reference. This does not prove the portrait-first Apply path.

The follow-up rechecks native preview orientation at Apply and refreshes after configuration changes without requiring clock recreation. Date-preview identity and widget Reset retain their orientation. Legacy inheritance now retains enabled depth and the reusable source subject for a second layout while dropping its incompatible baked mask. A shared widget depth policy supports portrait only, landscape only or both, while separate mode preserves independent choices.

Signed build, all 117 JVM checks and 14 Android suites (394 checks) pass. The 33 orientation checks include source inheritance, scope gating and shared policy reads preserving distinct layout scales. Installed on the P613 and scopes reloaded. User testing of portrait-first Apply/reopen and new policy visuals remains pending.

## Orientation profiles: 0.18.2 / versionCode 119

Signed build and 117 JVM checks pass. The P613 passes 14 Android suites (380 checks), including 19 new detached checks for layout keys, migration ownership, preserved legacy records and orientation frozen across a late Apply. The updated native binding probe passes against its installed editor/provider APKs; the original 9.0 editor's configuration callback was also confirmed by direct DEX declaration inspection. Version 119 is installed on the P613 and Dressroom/SystemUI reloaded with permission to discard the draft.

Runtime logs confirm the landscape widget owner loads enabled=true/mask=true, switches to the unconfigured portrait profile (false/false), and restores the landscape profile on rotation. The old landscape profile is retained under its legacy key and migrated to its landscape key. The temporary user_rotation and accelerometer_rotation overrides were restored. Editor Apply/reopen with independently changed settings in both layouts, portrait depth detection and foldable behavior remain pending; helper checks do not establish those visual results.

## Clock Style widget lifecycle: 0.18.1 / versionCode 118

All 117 JVM checks and 13 Android suites (361 checks) pass on the P613. The read-only binding probe resolves the added widget initialization, layout completion, wake and display-state callbacks against its installed Clock Style APK. Version 118 is installed and SystemUI reloaded; Dressroom was left running to preserve its draft. The runtime log confirms enabled=true and mask=true for the native ComplicationContainer. ADB captures show widget contents and rounded backgrounds clipped behind the saved subject on the actual landscape lockscreen, both before and after a screen-off/on cycle. User confirmation and portrait/rotation coverage remain pending. These checks do not certify other 8.5 providers.

The user confirmed that 0.18.0 clock depth survives Apply/reopen and is aligned on the actual lockscreen. Widgets clip in Dressroom but not on the lockscreen. The follow-up covers native callbacks that run after the outer widget container is initialized and restores clipping when Samsung replaces its outline.

## Initial Clock Style depth port: 0.18.0 / versionCode 117

The signed build and all 117 JVM checks pass. The P613 passes all 13 Android regression suites (361 checks), plus read-only SDK/editor/provider binding resolution including the private crop drawable accessor. Both ArcSoft libraries required by the SDK's native support check are present. Version 117 is installed; Dressroom's draft was discarded and the app restarted with the user's permission, and SystemUI was reloaded. The user opened the editor and reported that it appears to work. Logs from the actual Dressroom process confirm successful full-image and visible-detail SDK passes on two test wallpapers; an editor capture shows clock clipping behind the detected shape. Apply/reopen, actual lockscreen alignment and independent widget policy still need confirmation. The separate 8.5 AOD provider remains gated.

## Remove device blur forcing: 0.17.2 / versionCode 116

At the user's request, remove the unsuccessful experimental Now Bar/widget force-blur feature and archive its source and findings. The controls and production hook references are removed; the settings schema ignores old force flags. The signed build passes all 117 JVM checks, 13 Android Extras checks (including rejection of obsolete flags), repository/layout checks, inventory and whitespace checks. Version 116 is installed on the P613 and SystemUI was reloaded. Dressroom itself was left running to preserve any draft; its removed menu controls disappear after a full process restart. Native backgrounds require a process reload to shed already-installed hooks. Established clock blur and native widget/media behavior remain separate. See [the experiment record](research/FORCED_BLUR_EXPERIMENT.md).

## Extras and force blur: 0.17.0 / versionCode 114

Initial 8.5 Extras implementation includes normal Apply/cancel ownership, charging retention and custom text cards. Separate native force-blur overrides target widgets and the Now Bar on the inspected 8.5 Clock Style and 9.0 AOD provider paths. Media artwork and AOD stretch-transition controls remain hidden on 8.5. The signed build passes, along with all 117 JVM checks and 15 Android Extras preference checks. Read-only binding probes pass against the tabletÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s actual SystemUI/Clock Style/editor APKs and the phoneÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s reference AOD/editor APKs. Version 114 is installed on both devices. After SystemUI reload, the tablet logs confirm 8.5 charging/custom-card and scoped force-blur hooks installed; the phone logs confirm its reference hooks installed. The inspected new processes show no startup exception. The tabletÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s native dump reports Now Bar supported=true and blur supported=false. The user confirmed that Extras appears and the widget-blur switch survives Apply/reopen. Its visual blur remains unconfirmed. Force Now Bar blur instead makes the tablet pill transparent with dark text; this is a confirmed failure, not a successful blur port. Charging retention and custom-card rendering remain pending. A separate hardware-accelerated test Activity on 2026-11-02 shows that Samsung window blur does not blur a separate underlying checkerboard window; Samsung canvas blur works with content in its own window but does not sample that other window. Android RenderEffect works on its own content. Both test configurations were captured, and the temporary test app was removed. These results concern this custom ROM and do not establish A-series behavior. No global blur flags were changed.

## Reference phone update: 0.16.3

On 2026-10-31, the One UI 9.0 SM-S948B was updated in place from 0.14.0 to 0.16.3 (version 113). The read-only widget binding probe passes against its installed Dressroom 3.0.10.38 and AOD 8.9.66.1 APKs. SystemUI was reloaded and resumed with the reference provider, grid, media, blur/depth and glyph hooks reported installed; no new startup exception appeared in that processÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s inspected log. DressroomÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s editor is open. Widget visual sizing, live dragging and Apply/reopen validation on this phone remain pending.

## Live scale preview: 0.16.3 / versionCode 113

The user confirmed that 0.16.2 sizes widget contents and backgrounds correctly and improves group dragging on the tablet. Widget scale still updated only on release. The follow-up uses a temporary view transform while scrubbing, then performs the native resize on release. Preview transforms are cleared on panel rebuild/detach, reset and save. The signed build and all 117 JVM checks pass, as do repository/layout and inventory checks. The APK is installed on the P613. The user confirmed on 2026-10-31 that widget size follows the slider live and settles correctly on release. This validates the tested P613 editor path; actual 9.0 phone execution remains pending.

## Nested widget hosts and live drag: 0.16.2 / versionCode 112

The user confirmed 0.16.1 group dragging works but is delayed, while scaling still affects spacing only. The follow-up traverses nested native hosts, tracks saved-to-preview identities, updates the 9.0 hostÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s measured resize record, and uses the native immediate-position binding during dragging on 8.5 and 9.0. The six-case Android nested-host/identity probe passes on the P613. The signed build and all 117 JVM checks pass. The final binding probe resolves the revised 8.5 paths against the installed tablet APKs and the reference 9.0 widget paths against read-only copies of the original APKs, loaded on the tablet. The copies were removed afterward; they were not installed. 0.16.2 is installed only on the P613. Visual validation and actual 9.0 phone execution remain pending; no visual fix is claimed from the helper checks alone.

## Widget content scaling and dragging: 0.16.1 / versionCode 111

The signed follow-up builds and verifies; all nine JVM suites pass (117 checks). It addresses the user-confirmed 0.16.0 spacing-only scale behavior and adds group dragging for custom placement. The updated read-only binding probe also passes against the tablet, including the host-container sizing methods and editor drag prerequisites. The APK is installed on the P613; visual and interaction checks are pending. The user confirmed that the 0.16.0 panel and position sliders work; this does not validate physical content scale or lockscreen widget persistence.

## Initial tablet widget port: 0.16.0 / versionCode 110

Signed build succeeds and verifies. Nine JVM suites pass (117 checks). The updated read-only binding probe passes against the P613ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s installed Dressroom .50 and Clock Style APKs, including the toolbar, exact editor sizing/placement operations, attached-host traversal and native provider-size notification. The APK is installed on the P613; version 110 / 0.16.0-alpha is verified. Visual scale/placement, Apply/reopen and lockscreen behavior are pending. This is not certification of AOD phones or custom grid capacity/depth/blur.

## Tablet core controls: 0.15.2 / versionCode 109

Installed on the SM-P613 One UI 8.5 port with Dressroom 2.9.60.50 and Clock Style 8.9.35.60. On 2026-10-30 the user confirmed the Advanced controls work after restarting Dressroom, following the size/shadow preview check. The empty-panel and missing-weather-property blockers are resolved for this tested editor path. Nine JVM suites (117 checks) and the updated native binding probe pass.

The user subsequently confirmed that saving applies the tested settings to the lockscreen and reopening Dressroom restores them. Core preview, native Apply, lockscreen application and editor persistence are therefore user-confirmed on this tablet. The unported features and One UI 8.5 AOD phones remain separate validation tasks.

## Adapter build: 0.15.0 / versionCode 107

Signed build succeeds and verifies with v3 signing. All nine JVM suites pass (117 checks), including 17 adapter/rollback/save-ownership checks. Repository/layout, whitespace and generated inventory checks pass.

The read-only `VersionBindingProbe` passed on the SM-P613 port (API 36, One UI property 80500), resolving its installed Dressroom 2.9.60.50 editor/tab/save/date roles and Clock Style 8.9.35.60 stored-font, identity, render, shadow and height-normalizer roles. The module APK is installed on this tablet. Scope activation, editor visuals, Apply/reopen and lockscreen validation are still pending. This does not certify the unported 8.5 features or the reference phone runtime. See [the implementation boundary](compatibility/IMPLEMENTATION.md).

## GitHub preparation

The initial Git index was exported into a separate directory, without the private signing key or local build outputs. That source-only export successfully built using `build-local.ps1 -Unsigned` and passed all eight JVM suites (100 checks). Repository/layout checks and staged whitespace checks pass. The repository guard also rejected a deliberately forced staged signing-key path in the disposable export.

The GitHub workflow has been added but has not yet run on GitHub. No new device tests, module installation or SystemUI restart were performed for this preparation. Runtime changes are comments only; the build script adds an explicit unsigned packaging path.

## Cleanup build: 0.14.1 / versionCode 106

- Default project-local build succeeds without the former `work/` layout.
- V3 signing certificate matches installed 0.14.0. Generated manifest matches Gradle: version 106 / 0.14.1-alpha, min SDK 29, target/compile SDK 36.
- Eight JVM suites pass (100 checks); thirteen Android suites pass (361 checks). Three shared cases overlap, so these are not additive coverage totals.
- Eight actual Samsung/Android native glyph and outline checks pass. The temporary instrumentation package was removed afterward.
- Production JAR contains no probe, trace or test classes.
- `scripts/check-layout.ps1` checks PowerShell syntax, project layout and local Markdown links.

The phone remains on **0.14.0**. Cleanup verification did not install the new module, restart SystemUI or change saved profiles. Helper contracts do not certify every Samsung hook or device.

## Runtime baseline and evidence

0.13.11 removed the compact-clock flash by keeping the live native blur drawable attached. The user confirms smoother media/clock motion on 0.14.0. Its frame observations remain mixed; no overall FPS gain is claimed. See [0.14.0](releases/RELEASE_0.14.0.md). Raw frame evidence is retained locally under `docs/validation/0.14.0/`.

Fresh runner output lives under `.build/tests/<suite>/results.txt`; curated cleanup results are retained locally in `docs/validation/0.14.1/`. Raw device evidence is ignored by Git. [Historical validation](history/VALIDATION-before-cleanup.md) preserves earlier successful and failed builds. [Archive map](ARCHIVE.md) locates old `work/...` evidence. Historical test counts are not automatically revalidated results.

The first phone editor test still showed spacing-only scaling and no live scale preview. ADB then established that Dressroom PID 16583 predated installation (about 25 minutes of uptime versus roughly two minutes since the new SystemUI process). That editor was still running old hooks. The first report cannot validate the 0.16.3 editor changes; a full Dressroom process restart is required, with any draft handled first.

After restarting Dressroom, the user confirmed on 2026-10-31 that both reported phone editor issues are fixed: widget contents/backgrounds scale correctly with live slider preview, and group dragging works correctly. This confirms the tested reference 9.0 editor paths alongside the P613 8.5 results. Post-change Apply/reopen persistence and lockscreen/AOD sizing remain separate checks; they were not explicitly confirmed in this reply.
