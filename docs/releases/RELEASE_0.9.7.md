# Media collapse and manual-stretch restoration - 0.9.7 alpha

Installed versionCode 70. Builds on the 0.9.6 separate artwork layer, native spring tracking and lower artwork blur.

## Corrections

- The user confirmed the native close gesture is a tap outside the original expanded player frame, including the clock. Fullscreen artwork does not change that original hitbox.
- Shrinking artwork previously drew underneath the Now Bar, then reappeared only when Samsung restored the small thumbnail. A temporary foreground pass now paints the same spring-driven artwork only where it crosses the final compact bar. It is clipped to that bar's native height; the rest of the artwork remains behind the native clock/text/controls. Native v0 cleanup restores thumbnail alpha and removes the foreground pass in the same draw cycle. There is no separate return animation or timer.
- Opening media flattens the rendered clock via g0 while T0 can retain the original manual font data. The height-preservation option can remove the only doze axis, causing the native restore callback to return without drawing that saved font again. Comparing T0 alone therefore missed this stale rendered state.
- On media exit, the module now explicitly restores the saved manual/depth heights through the renderer and refreshes layout. If the clock is temporarily detached during the native close action, the pending restore waits until reattachment. A reopened media player is respected; a dozing clock is not forcibly stretched when Keep stretch is Off. No editor settings are rewritten by this correction.

## Verification

- APK compiled and v3 signature verified; installed with the existing module signer.
- SystemUI reload passed the rendering guard. No AOD force-stop was performed.
- All 66 existing JVM checks pass. These checks do not verify graphics.
- On-phone recording work/media097-clock-close.mp4 exercised expansion, tapping the clock to close, and returning to the normal lockscreen. The sampled sequence work/media097-clock-close.png shows the shrinking artwork crossing the compact Now Bar and returning to its native thumbnail.
- The same capture and work/media097-after.png show the saved tall manual clock heights restored after closing. The runtime reported `Manual clock heights restored after media` at 15:16:01 on the device.
- User visual confirmation remains pending. Other artwork shapes, repeated interruptions, other Now Bar cards, landscape, and media exit while already dozing are not exhaustively verified.

The native expanded player hitbox remains unchanged. Expanding it into a full-screen interaction surface would remove the user's current outside-tap close affordance and is not part of this correction.
