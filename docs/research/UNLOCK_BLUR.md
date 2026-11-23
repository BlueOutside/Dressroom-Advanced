# Blur during swipe-to-unlock

Inspected the reference One UI 9 SystemUI and AOD APKs. `UnlockBlur` adds an
experimental, opt-in override for One UI 9. The setting is in Extras -> Blur
and saves through the normal Extras Apply transaction. Older versions keep
native behavior until their controller paths are verified.

SystemUI's `ActionMoveHandler.handleMotionEvent(MotionEvent)` writes alpha and
scale to the drag views, then calls
`KeyguardStatusViewAlphaChangeControllerWrapper.updateAlpha(float)`. The wrapper
broadcasts to plugin listeners; it does not itself change view alpha.

The AOD clock controller's alpha listener treats any value other than exactly
1 as an alpha-animation blocker. The blur state becomes hidden and its apply
method calls `AbsClockView.removeBlur()`. Returning to 1 clears that blocker.
The state machine has separate unlock-alpha, shortcut, screen-animation,
doze, bouncer, media and visibility blockers. Those must remain independent.

Now Bar's listener has different behavior: it writes the actual stack alpha
and updates each card's blur drawable alpha, including Brief's GenUI blur
style. Replacing that listener's alpha with 1 would also stop its normal fade.

The widget container updates background alpha separately. Its cover-specific
path multiplies container alpha, content alpha and blur-background alpha.
The inspected code does not establish an equivalent unconditional cutoff for
all widget types. A common system-level rendering cutoff remains unverified.

The override intercepts the clock-specific listener's intermediate drag alpha;
other listeners still receive the original value. The external clock blur
host follows the original swipe opacity. ClockBlurAlignment continues to
rebuild its moving matte after drawing. It does not retain old SemBlurInfo or
suppress native clear calls.

Widget, Now Bar and clock blur radius stays native while region opacity follows
the product of the surface's ancestor view alphas. BackgroundBlurDrawable's
compositor region uses its own alpha; changing only View alpha fades the tint
but can leave the actual blur visible. The framework's BackgroundBlurDrawable
RenderNode position listener tracks their moving geometry on the rendering
thread. Existing depth clipping still updates through WidgetDepthBlur.

ActionMoveHandler snapshots state before its first native alpha/visibility
writes. Cancel restoration remains tracked until alpha returns to one and
restores the pre-drag background/cover state. Zero alpha is only the native
fade endpoint; it can occur with the finger still down. Actual completion is
checked from the touch animator after the gesture ends. Media,
screen, shortcut and bouncer blockers remain native. Disabling the setting
restores the last native opacity and forwards the real clock alpha again.

The unlock performance patch keeps the global `View.setAlpha` and
`BackgroundBlurDrawable.setAlpha` hooks only while Keep live blur is enabled.
Settings notifications reconcile those hooks on the main thread. Disabling
first restores the current native opacity, then releases both hook handles;
enabling takes fresh opacity baselines at the next drag. Partial installation
rolls back both hooks. Eligibility checks are cached for at most 500 ms and
invalidated on a new touch or Extras notification. Glyph mapping adds the
native path through its matrix directly, avoiding a temporary matrix and path.

Frame boost caches the window root weakly and checks its window token before
reuse. Every pulse extends both view and root deadlines for the full requested
duration. The proposed early-return shortcut was removed because it could
expire before the last pulse's requested tail. Expiry/detachment releases the
boost and its root cache entry.

Validation: 29 JVM suites passed; focused phone probes passed 47 unlock-blur
checks and 9 frame-boost checks, covering opacity restoration before unhook,
repeat disable, fresh baselines, path transforms, deadline extension, root
replacement and expiry. The preview build is `2.0.6-unlock-perf-preview`
(`20006017`); it was installed and SystemUI logged successful opacity/core hook
installation. These checks do not measure animation smoothness.
The user confirmed that a cancelled swipe still moves/fades and restores blur
correctly, and completed the off/on toggle check on the installed preview.

During the drag, the clock matte is rebuilt from native local glyph/weather
paths using each view's complete global matrix relative to the blur host.
Clipped visible rectangles and the clock's standalone scale omit ancestor
swipe zoom and can produce a second, misaligned clock. Native source paths are
not mutated, and depth subtraction runs after mapping the live paths.

Framework blur availability and Reduce transparency checks still apply. GenUI
Brief cards have their own renderer and still need visual validation; the
drawable override covers their native BackgroundBlurDrawable surfaces only.

JADX aliases in SystemUI's KeyguardTouchAnimator:

| Decompiled name | Alias |
| --- | --- |
| m323xa5ae6e1 | getAnimatedView |
| m324xd394625 | hasAnimatedView |
| m327x84c4ab2c | restoreChildViewAnimations |

Clock opacity continuity: the native manager has two reveal entries reached from
its submit method. The ordinary path fades the blur for 300 ms; dashboard return
runs a separate 500 ms animation from opaque text to the glass opacity. Both are
resolved from their DEX calls and animator reads. Finish their native callbacks,
rather than only finishing the ordinary reveal. During a deferred glyph redraw,
apply the manager's collected text opacity before drawing the new glyphs; waiting
until the post-draw submission exposes one frame in the palette's opaque color.
The opacity binding is optional so an unknown provider keeps its existing retry
and readiness handling.

Animated clock blur on One UI 9 also bypasses motion-only clear requests. This
includes overlapping stretch, screen and media animation reasons. DOZE_ACTIVE
is permitted during a morph, but amount 1 clears the lockscreen blur as usual.
Dashboard activity is only permitted alongside an active media transition.
Hidden containers, bouncer, disabled blur and unknown reasons retain Samsung's
clear behavior. The live path uses each glyph's complete global matrix during
these transitions, including ancestor zoom, and is submitted after glyph drawing.
No subject detection or wallpaper capture is repeated. Motion tracking stops
when the native animation reasons clear; idle dispatchDraw has an empty-set exit.
