# Dressroom Advanced 0.14.0 alpha

Current installed build: [0.14.0 artwork and stretch reuse](../releases/RELEASE_0.14.0.md), versionCode 105. **The user confirms smoother cover/clock motion**, with the compact-clock flash still resolved. Reuses contrast pixels, temporary geometry, stable clock masks, exact native font requests and equivalent gradient-pass glyph outlines. Build/v3 signing, 361 contract checks and eight native checks pass. Frame observations show lower median/GPU times but mixed frame-tail results; no overall FPS/jank gain is claimed. The [0.13.11 live blur-host fix](../releases/RELEASE_0.13.11.md) remains unchanged.

Previous build: [0.13.3 rendering allocations and cache cleanup](../releases/RELEASE_0.13.3.md). Reuses clock/widget geometry buffers and shadow effect graphs, adds a no-argument reflection fast path, and releases unnecessary subject/drawable references. 180 Android checks pass. Three isolated lookup benchmarks report about 5.5 → 1.9 ms and 3.2 MB → 0 reported Java allocations per 20,000 warm calls; this is not an FPS measurement. Installed and SystemUI reloaded. A late compact-clock artifact was subsequently reported; see the current return fix.

Previous build: [0.13.2 code-only bug audit](../releases/RELEASE_0.13.2.md). Fixes a reproduced depth-cache fill-rule collision, preserves a valid full-image subject when optional detail detection fails, cleans up detection failure paths, and completes primitive reflection matching. 142 Android checks pass. No preference migration or quality reduction.

Previous build: [0.13.1 media return and render hot paths](../releases/RELEASE_0.13.1.md). The user-confirmed native clock return animation from 0.13.0 is retained. Cache reflection lookups, skip disjoint mask cuts, and reuse the artwork blur display list when only controls change. 77 Android checks pass; installed and SystemUI reloaded. The user confirms substantially smoother animation; no measured FPS gain is claimed.

Previous build: [0.12.10 World Clock idle depth](../releases/RELEASE_0.12.10.md). World Clock native drag/release, idle clipping and switching back to the normal clock are visually verified. Selected model identity and composite container clipping are used; manual-stretch restoration now respects native eligibility. Calendar/Analog depth was verified earlier, and wallpaper-style detection is user-confirmed for the current subject. Frames, fresh weather detection and other-style saved AOD remain open.

Previous build: [0.12.3 section separators and tinted blur](../releases/RELEASE_0.12.3.md). Keeps native secondary tabs and compact sliders; separators now divide major sections only. Module-forced custom-color blur uses Samsung's wallpaper-based blur tone while retaining the selected foreground tint. Section-only separators are user-confirmed; blur still appears weak and remains under review. Landscape, tablet/foldable and A-series work remains tracked in [the compatibility audit](../releases/RELEASE_0.12.2.md).

Previous build: [0.11.5 charging liquid restoration](../releases/RELEASE_0.11.5.md). The user confirmed gap swipe-to-unlock, reduced clock effect flashing, and charging text surviving card cycling in [0.11.4](../releases/RELEASE_0.11.4.md). Held charging cards now restore the liquid's completed native frame and clear intro wave blur; the user confirmed stable liquid and visible percentage text after card cycling. Released widget roots stop inheriting media-cover clipping. The blue rim report was traced to the source wallpaper.

The confirmed [0.11.2 automatic depth and edge quality](../releases/RELEASE_0.11.2.md) remains: static-image detection combines full-image/detail results and follows editor crop/zoom. Saved masks use the private file bridge and a runtime screen-space fallback. Manual stretch/custom grid restore without opening Advanced. Subject editing and video remain deferred.

Runtime baseline: [0.8.4](../releases/RELEASE_0.8.4.md), with deferred runtime installation. SystemUI and AOD hooks are active again; startup and screen-off/on rendering verified. Earlier runtime enhancements remain, with cached depth geometry, synchronous manual hold and live crop tracking for new detections. The user confirms improved AOD transition performance. Adds group dragging, hides the native rotation guide, and scans the currently edited wallpaper. Free/snap dragging and native picker taps were tested on the phone. The user confirmed flower detection and idle depth after the detailed-contour fallback in 0.7.8. The [0.7.4 baseline](../releases/RELEASE_0.7.4.md) covers confirmed scrolling, native rotation, saved vertical restoration and lockscreen/AOD arrangement. The [native widget inventory](../research/WIDGET_NATIVE_FEATURES.md) records the Samsung functionality used.

Last fully confirmed widget-depth baseline: **0.6.6**, fixing [fully covered widget blur surfaces](../releases/RELEASE_0.6.6.md). The user confirmed widget depth through the lockscreen/AOD transition, following the earlier saved placement, manual stretch and depth restoration fix. The [JADX widget pipeline audit](../research/AOD_WIDGET_PIPELINE.md) records native widget ownership and separate blur/content paths. Earlier clock work is covered by the [clock lifecycle audit](../research/CLOCK_PIPELINE_AUDIT.md) and [AOD clock rendering trace](../research/AOD_RENDER_PIPELINE.md).

An API 102 LSPosed module for the Samsung Dressroom lockscreen editor. It adds an **Advanced** tab beside Font and Color and Style, using Dressroom's One UI controls and live clock preview.

The widget expansion is documented in the [widget customization audit](../research/WIDGET_CUSTOMIZATION_AUDIT.md): native toolbar integration, picker eligibility, larger grids, sizing, placement, save/cancel behavior, independent depth, and blur. Open the bottom-toolbar Advanced button for Widgets. See [0.7.4 changes and validation limits](../releases/RELEASE_0.7.4.md).

The tab provides size, position, color, font weight, corner rounding, date/weather options, manual stretch segments for compatible clocks, blur controls, a subject depth effect, and custom shadow softness, offset, and color. Controls are grouped into native category pages. Some controls depend on the selected clock's native capabilities.

Custom shadows now persist when Dressroom's native Apply/Done save succeeds. SystemUI and AOD read the committed settings; the lockscreen renderer adds a blurred silhouette behind the rendered clock, including custom fonts that ignore native text shadows. A content observer refreshes attached clock views after a save.

## Use

Enable all three scopes in LSPosed: Samsung Wallpaper and style (`com.samsung.android.app.dressroom`), SystemUI (`com.android.systemui`), and Always On Display (`com.samsung.android.app.aodservice`). Reboot, open Dressroom's lockscreen editor, tap the clock, and select Advanced. Use Dressroom's Apply/Done action to commit changes.

## Confirmed on device

- The third tab renders with native controls and live preview.
- Independent manual stretch values visibly produce unequal digit heights on a supported clock.
- Position sliders use Samsung's position repository; the user reported they work.
- The user confirmed the custom shadow appears on the actual lockscreen and AOD in 0.3.0. Device logs show the native save callback committing settings and the SystemUI renderer applying the committed shadow to `LOCK_SCREEN`.
- API 102 hooks installed in scoped processes; APK was built, signature-verified, and installed on the connected device.

The 0.5.0 source was rebuilt from the original 0.4.7 behavior before the blur work. The rebuilt baseline decompiled identically to the 0.4.7 APK. The blur controls use Samsung's renderer: Clock blur can force it on compatible views, Blur strength changes its radius (Samsung's default is 200), and Custom text visibility adjusts the text over the blurred area. The extra values are committed after Dressroom's native save. The user confirmed that the Weather information switch saves, but its editor visual can require a retoggle. The pale duplicate artifact also recurred with Custom shadow off; its cause remains under investigation.

Saved manual stretch heights are restored before Dressroom validates them or runs subject-aware resizing; the user confirmed that wallpaper resizing no longer overrides manual stretch in 0.4.4.

## Current limits

- This is a device-specific alpha targeting Dressroom 3.0.10.48 (versionCode 301038000). Other builds may differ.
- Size changes use Samsung's native scale/position updater; manual stretch heights use the current clock scale. On 0.3.2, the user confirmed both position and stretch match the preview after Apply and reopening. 0.3.3 removes temporary geometry logging without changing this behavior.
- Committed extra settings currently follow the clock type and font across profiles using that combination.
- Blur requires the clock's `ClockBlurExtension` and usable text paths. A clock without that renderer cannot be forced, and some clocks with the extension may still lack paths for blur. The new radius and visibility hooks are experimental and require the module to remain enabled on the lockscreen and AOD.
- The user confirmed the date-toggle overlap no longer appeared in 0.5.2, and Weather information and Manual stretch retained their values across tab switches. The artifact later recurred even with Custom shadow off. Its root cause is not established; native idle blur and the weather/layout transaction are documented in the lifecycle audit. Dressroom's main preview may differ from the saved weather-only layout; the user has deprioritized that preview.
- The first depth effect uses Samsung object capture on a static lockscreen wallpaper. Tap **Detect wallpaper subject** in Advanced, then Apply to save the mask. It clips clock pixels independently of manual stretch. It is experimental until editor, lockscreen, and AOD alignment are checked on device; live wallpapers cannot be scanned with this path. See `DEPTH_RESEARCH.md`.
- The user confirmed idle clock depth in Dressroom in 0.6.0, correct weather restoration and post-reboot lockscreen/AOD depth in 0.6.1. Native blur-path clipping covers the separate Samsung clock blur host; 0.6.2 adds complication background clipping, pending visual validation. Subject extraction around the mountain remains imperfect. An initial AOD clock appearance issue resolved after reapplying, and remains recorded for startup investigation.
- Date hiding uses an LSPosed override, so Samsung's native weather setting persists without the module, but the hidden-date choice itself requires the module to stay enabled.
- Samsung's native placement and scale clamps remain. This build does not add independent date/time sizing, arbitrary formatting, widget policy overrides, or every LockStar option.

Disable the module and reboot to remove hooks. Disabling it does not revert values already saved in Samsung's model.

## Build

Use JDK 17+, Android SDK platform 36, and build-tools 36.0.0:

```powershell
.\build-local.ps1 -Sdk 'C:\path\to\Android\Sdk'
```

The script compiles against the official API 102.0.0 AAR, packages modern `META-INF/xposed` metadata, aligns, signs, and verifies the APK. Keep the signing key in the chosen BuildDir for future update installs. The source ZIP excludes the key and build caches. The supplied Gradle project is an alternative development path.

`tests/ReflectContractTest.java` and `tests/IdentityLocksTest.java` passed their 12 checks; device logs and user testing cover the Android behavior described above.

Official API: https://libxposed.github.io/api/
