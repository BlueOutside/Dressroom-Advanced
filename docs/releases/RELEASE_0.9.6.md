# Extras and fullscreen media — 0.9.6 alpha

Installed versionCode 69. This continues the stable 0.8.4 runtime discovery and existing clock/widget functionality. Extras is a second native tab in the bottom-toolbar Advanced panel.

## Added extras

- Keep charging card: hold the native Now Bar charging view after its completion callbacks, while allowing the charging UI state and doze session to finish. Unplugging or disabling the option releases the view. The user confirmed it remains after the native timeout; unplug cleanup still needs explicit confirmation.
- Full-screen artwork: a separate shade background follows Samsung's native media artwork spring. Text, artist, clock, controls and hitboxes retain their native layouts. Only artwork pixels, reveal/ripple layers and covering backgrounds are suppressed temporarily. Settings Off or closing the player restores those properties.
- Keep clock stretched on AOD: remove only HEGT from the doze interpolation axis list during the native callback, preserving weight/color transitions. Media expansion is excluded so the native expanded-player clock arrangement remains usable. Hook installation is verified; the settled AOD option still needs direct confirmation.
- Custom Now Bar text: use Samsung's external-view NowBarItem route, with separate compact/expanded views and bounded Unicode title/message. Native card visible in phone captures; broader card switching/priority behavior remains to check.

Extras are device-wide, default Off, and use their own editor draft. They commit after native Apply succeeds without depending on opening the clock's Advanced tab. Settings are cached in runtime processes and refreshed by a content observer. No settings IPC runs repeatedly during animation frames.

## Media research and corrections

The reference FullscreenPlayer mod was inspected only to understand background layer placement and selective stock-layer hiding. Its implementation, blur processing, tuning scheme and delayed fade were not copied. Our layer follows native media spring geometry and lifecycle callbacks directly.

Samsung's hierarchy places notification_panel and now_bar_rootview in sibling branches. The artwork animation target U also contains title and artist: hiding that whole target hid the text in the earlier experiments. The source image and reveal/ripple layers must be suppressed individually.

The awake lock_clock_blur background covered the separate artwork layer while AOD did not. Temporarily clearing that background, alongside keyguard_status_view's covering background, restored the awake artwork without relocating clock or controls.

Expansion temporarily reparents U into ViewOverlay. That overlay is omitted from ordinary root-child traversal, and View.isShown()/getRootView() can reject its detached-style parent chain. Native n82.invoke case 5 identifies the precise overlay owner and initial scale. The backdrop tracks that owner, maps local spring geometry into the shade, and explicitly visits the artwork subtree. Native x0 restores normal ownership; g0/v0 provide equivalent collapse tracking. Earlier root-only lookup made the ordinary frame visible until cleanup, then snapped fullscreen.

Samsung's existing controls-blur view sampled the flower wallpaper rather than the separate album-art layer in the 0.9.5 trial. That trial is removed. Version 0.9.6 applies Android RenderEffect blur to a cached RenderNode of the actual artwork, fading it into the controls area. The upper artwork stays sharp. Bitmap processing is not performed on the CPU; the display list changes only with source/geometry changes.

## Verification and remaining checks

- APK compiles, verifies with v3 signing, installs over the existing signature.
- SystemUI startup guard passed after reload; no AOD force-stop is used.
- All 66 existing JVM checks pass (reflection, identity holds, lifecycle, layout and deferred discovery). These do not validate graphics.
- 0.9.4/0.9.6 awake captures show fullscreen artwork with native clock, title, artist and controls in front.
- 0.9.5 recording reproduced the frame-to-fullscreen snap and the wrong wallpaper-sampling controls blur. It is not a successful animation baseline.
- 0.9.6 expansion was recorded after the user selected the native music card. Samples in work/media096-final-expand.png and work/media096-complete-expand.png show the artwork morphing from the thumbnail toward the full shade during the native spring, with native clock/title/artist/controls retained. The earlier ordinary square-artwork stage followed by an end-of-animation fullscreen replacement is absent in these samples.
- The same recording shows the lower artwork blurred behind controls while the upper artwork stays sharp. The flower wallpaper sampling seen in 0.9.5 is absent.
- Collapse is not verified. Outside taps, Back and swipe trials did not reliably invoke Samsung's collapse; some recordings instead caught wake/doze or other native pages. Those trials are not passing collapse evidence. Awaiting the user's usual collapse gesture. No implementation change was made during this verification turn.
- Also check bright artwork, page changes, interrupted springs, option Off restoration, unlock, landscape and extended AOD performance.

Known unrelated cyan/depth rim remains deprioritized by the user. One UI version compatibility work remains deferred.
