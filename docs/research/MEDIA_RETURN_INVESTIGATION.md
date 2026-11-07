# Compact clock on media return - investigation

Status: **user confirmed fixed on 0.13.11** after the long-open physical-tap test. The real native cache defect was not the complete cause: 0.13.10 traced our artwork layer restoring a compact BackgroundBlurDrawable at +785 ms before the correct stretched submission at +823 ms. 0.13.11 leaves that native drawable attached and suppresses host alpha while artwork is active, preventing stale-drawable restoration. See RELEASE_0.13.11.md. Earlier failed candidates and the investigation sequence remain below for context.

## Reproduction and evidence

Leave the full music player open for several seconds, then close it by tapping the clock/outside the artwork. Immediate open/close can avoid the problem. The user confirmed the compact clock appeared during the recorded physical close on 0.13.8.

Recording: phone `/sdcard/media0138-user-close.mp4`. Local samples: `work/media0138-user-fine/frame-50.png` through `frame-53.png`, sampled at 16 ms from video offset 25,000 ms. These correspond to approximately 25,800-25,848 ms. Frame 49 is clean and frame 54 is clean: roughly a 64 ms flash. The stretched clock is already visible beneath the small bright clock and its weather. The normal large clock brightens later. This establishes overlapping rendered output, but does not establish the owner of the compact output.

0.13.4/0.13.5 timing changes, 0.13.7 compositor-outline clear, and 0.13.8 readiness guard have not resolved the user's reproduction. Passing helper tests do not prove visual correctness.

## Source facts

- SystemUI `super_notification_shade.xml` declares `lock_clock_blur` as a full-screen FrameLayout sibling before the notification/keyguard content. It is not a child of the rendered clock. AOD `tx0.d()` finds it by tag, makes it visible and uses it for runtime native clock blur.
- `PluginFaceWidgetManager.getAODClockView` delegates to the AOD plugin. `FaceWidgetContainerWrapper` retains the plugin's content containers; its first content container is the clock container. The inspected wrapper adjusts visibility/alpha and does not construct a separate compact clock itself. This does not rule out clocks in other owners.
- SystemUI's captured `applyBlur(View,int)` can set a BitmapDrawable background on the supplied view. Our media layer wraps several native backgrounds, including the clock-blur host, in `EmptyBackground`, then restores them on return. The relevance of a captured background to this particular flash remains unproven.
- SystemUI `ActionUpOrCancelHandler.handleMotionEvent` can call the plugin's `onClockClicked(x,y)` and then `onEmptyAreaClicked()` for the same tap. It also starts the native 1.1 scale tap-affordance springs with restoration posted after 150 ms. This is a real difference from an injected Back collapse and explains an additional scale bounce, but does not itself identify the duplicate.
- AOD `AbsClockView.setClockTextBlur` calls `cq0.b`, but this is not the only route: `AbsClockView.x0` also calls `cq0.b` directly, blur enablement in `t` changes delegated manager state, and failed path generation schedules `cq0.c` retries after 8 ms. Through 0.13.8 the module deferred only the public `setClockTextBlur` route; 0.13.9 moves the guard to `cq0.c`.
- `cq0.c` calls `tx0.b`, which collects glyph paths, combines weather, then submits a SemBlurInfo to the sibling host. The existing low-level depth hook scopes this submission to its owning clock, but does not synchronize every submission with glyph redraw.
- Native NonePaddingTextView's S flag is set true when first collected. Earlier AOD xrefs showed no media-return reset to false. Therefore 0.13.8's S-false readiness guard is not supported as an explanation of this return failure; it was not logged during the failing return.
- AOD R-field xrefs show its readiness reset on text changes, not on font/size updates. A real Samsung-class instrumentation experiment confirms that size 80 → 160 returns exactly the old compact cached path with R true until drawing. Four native cache/guard assertions pass; this establishes the cache defect, not exclusive ownership of the visual artifact.
- `aq0`'s enabled delegate does route first enablement through setClockTextBlur, so that path alone was already covered. `yp0.run` calls manager c directly. Method xrefs list cq0.b and yp0.run as c's direct callers.
- AOD ClockBlurController uses separate deferred timers: MediaExpand 500 ms, StretchAnimation 50 ms. The ordinary height spring and host-scale animation have distinct completion signals. These timings explain why a test must exercise a fully settled expanded player.

## Investigation questions and resolution

The shared-background question was resolved by the physical 0.13.10 trace in `work/media01310-native-return.txt`. It captured one visible native clock owner, the restored compact BackgroundBlurDrawable and the subsequent full-size outline at scale 1.0. The resulting drawable-preserving correction is user-confirmed on 0.13.11. Broader stock-player/device coverage remains unverified. The original investigation checklist follows:

1. Follow the actual physical-tap callbacks through AOD and its delayed blur coordinator, including direct manager calls and retry runnables.
2. Determine whether the compact output is a new native outline submission, a retained host/captured-background layer, or another visible clock owner. The earlier bounded trace of a valid large outline late in return does not distinguish these cases.
3. Correlate the exact 64 ms flash with all relevant low-level submissions/owner identities and host changes if static source cannot resolve it. Avoid another high-level timing-only change.
4. Preserve Samsung's height spring, the confirmed contrast fade/depth restoration, and gesture behavior. Do not hide the whole clock or disable blur to conceal the symptom.

Targeted SystemUI sources read through JADX MCP are saved under `work/clock-audit/SystemUI.*.java`. No APK-wide method/code search is needed. The native AOD manager/strategy sources previously read through MCP remain in the same directory.
