# One UI 8.5 media player

## Lyrics page integration (2026-11-23)

JADX MCP inspection of AOD 8.9.32.95 confirms a scaled music holder containing
a ViewPager2 with several native media holders. `getNowBarMusicHeight` and
`getNowBarMusicWidth` use different device/orientation ratios; the page also
animates its holder scale. The media adapter constructs holders ahead of display
and separately binds their visibility, artwork, title and artist. Consequently,
the latest constructed holder is not necessarily the player the user is viewing.

The previous global lyrics controller was retargeted by every holder constructor.
Lyrics could inherit a hidden/offscreen page's controls, title or artwork. Its
fallback container could also be inside the scaled/clipped native pager. These
are concrete black/empty-page failure paths; the individual user reports still
need a visual retest to establish which path affected each device.

The lyrics binding now keeps one controller per controls host, with weak registry
entries and a single active page. Closed/preloaded pages do not register media
listeners or request lyrics. View moves remain deferred until attachment ends.
The active overlay belongs to its actual window; leaving/recycling the player
closes and removes that overlay, including native alpha-only closes.

Bounds map the measured controls through their full ancestor transform into the
overlay window. The safe region excludes system bars/cutouts. Portrait normally
uses the area above the controls; side regions or available lower space
cover landscape/compact layouts. Unmeasured/offscreen/full-window controls cannot
open a zero-sized lyrics page. No guessed 1750px position is used. Layout changes
do not reorder window children; while open, geometry follows native motion.

The 8.x binding uses its own blurred artwork and restores original native title,
artist and artwork alpha/translation on close. It does not depend on the transient
native full-cover layer being visible. Hiding lockscreen clocks/indications now
protects ancestors of the lyrics and player controls. Native control/output touch
listeners are preserved. The lyrics toggle positions beside the measured legacy
output button instead of assuming the One UI 9 icon layout.

Resource checks covered both `now_bar_music_page_holder` and the dashboard's
`facewidget_music_page_holder` in these local samples:

| Provider | Controls resource |
| --- | --- |
| 8.0 AOD 8.9.15.50, Clock Style 8.9.15.39 | `facewidget_music_container_small_expand` |
| 8.5 AOD 8.9.32.95, both 8.9.36.23 samples, Clock Style 8.9.35.60 | `facewidget_music_container_expand_Player` |

All sampled layouts expose separate title, artist and media-output views. The
existing constructor/body signatures are unchanged; no obfuscated runtime lookup
was introduced.

JADX aliases applied to the reference 8.5 AOD project:

| Original | Alias |
| --- | --- |
| `aod.YR` | `LegacyMusicPageViewHolder` |
| `aod.NR` | `LegacyMusicPageAdapter` |
| holder `M(boolean)` | `animateMusicPageVisibility` |
| holder `N()` | `applyMusicPageVisibility` |
| holder `F()` | `isLandscape` |
| holder `g0` | `expandedControls` |
| holder `U` | `albumArtContainer` |
| holder `T` | `albumArtAndTitles` |
| holder `P` | `artworkTransitionContainer` |

Validation: signed preview `2.0.6-legacy-lyrics-preview` (`20006019`) built;
all 29 JVM suites passed, including 645 lyrics checks. Repository/layout/diff
checks passed. The lyrics suite includes portrait, landscape, cutout-safe,
clipped/offscreen and insufficient-space geometry cases. The preview was installed
on the connected One UI 9 phone and SystemUI was reloaded for a shared-code smoke
test. Device validation must cover framed and
fullscreen players, switching pager cards, opening/closing lyrics and rotation
on 8.0 and 8.5. APK inspection does not establish their visual behavior.

Inspected Clock Style 8.9.35.60 through JADX MCP. AOD 8.9.32.95 is compared
against the earlier source inspection and read-only APK binding checks.

## Native layout

`NowBarMediaCardView` owns `album_art_container` and `album_art`, with separate
portrait `media_card_title_actions_container` and landscape
`media_card_title_actions_container_land` layouts. Its `c0(boolean)` configures
the native card and applies tablet/fold/phone dimensions. The player controls
remain in the expanded pill/card layout. This is not 9.0's artwork overlay
reparent/return contract, so its thumbnail motion and controls blur band should
not be transplanted as a shared implementation.

The tablet user confirms that its compact thumbnail does not fly into the
expanded artwork. Keep that native behavior while fixing the clock lifecycle.
Fullscreen artwork uses the dedicated 8.5 fade backend described below.

## Clock depth bug

The clock page's media observer calls `AbsClockView.setMediaExpanded(boolean)`.
Both inspected 8.5 renderers store that flag at `W0`. The module previously
suspended depth through the 9.0 artwork host only, so the 8.5 player could keep
clipping its clock against the wallpaper subject. The user observes this in
portrait; the exact reason it is not visible in their landscape profile is not
yet established. The fix follows media state in both orientations rather than
changing saved portrait/landscape masks.

Clock Style uses observer `aod.WJ`, page state `J0`, return animator `K0` and
view-state enum `aod.cL`. AOD uses observer `aod.ZQ`, page state `K0`, return
animator `L0` and enum `aod.iS`. Each clock page starts a native 300ms animator
after updating the clock flag. These are different from 9.0's spring state.

## Fix

The 8.5 adapter suppresses wallpaper depth when native media expansion is set.
On close it checks the page's actual return animator on the next animation
frame, then waits for completion and a final layout frame before resuming depth.
It does not set clock heights, cancel Samsung's animator or introduce a timer.
Reopening invalidates the old return ticket and removes its listener. Missing
or failed animator lookup releases the suspension instead of leaving depth off.

Saved depth settings and orientation policies are unchanged. 9.0's artwork,
clock return, controls blur and thumbnail layers are unchanged.

## Wallpaper transition

`MediaExpandStateObserver.onChanged` owns a separate wallpaper blur/dim animator.
Clock Style stores it in `C`, with expanded state `E` and status-view host `u`.
AOD stores the equivalent in `D`, with state `F` and host `v`. Both sources use
integer opacity 0-255: opening is 300ms (or immediate for the native nonzero
transition condition), while closing is 200ms. This is not the clock's 300ms
return animator.

The framed artwork can disappear before that wallpaper transition ends. The
fullscreen backend therefore follows the wallpaper animator and retains the last
native texture during close. Native observer callbacks only update opacity and
invalidate the window; source selection/restoration waits until pre-draw, after
Samsung finishes replacing its views and animator. Reopening replaces the old
listener, and disabling the setting releases the retained texture immediately.
The actual wallpaper/provider settings are not changed.

AOD can retire the native artwork view after opening. Once a cover has been
shown, keep that texture while Samsung reports the player expanded, and pick
up a new bitmap if the native artwork becomes available again. Samsung can
also publish the expanded state without starting a wallpaper animator. Use
the settled opacity for that state.

The expanded controls have a tinted image and a separate SemBlurInfo surface
that Samsung uses during the transition. Fullscreen artwork uses an untinted
window blur behind the controls for the whole expanded session. The framed
player uses the same banner when Now Bar glass is enabled. Leave the native
banner visible if the system cannot supply a window blur drawable.

The on-device comparison showed the cover drawn at full opacity in the
settled player. With Clock blur enabled, Samsung then submitted a clock blur
region with full-screen bounds and animated its opacity to 1. With Clock blur
off, the cover stayed visible. Hold the lockscreen clock blur submission while
the fullscreen cover is visible. Replay the latest request once the cover
has faded out, and keep the controls banner on its separate blur surface.

## Wallpaper blur over fullscreen artwork

In AOD 8.9.32.95, MediaExpandStateObserver applies the wallpaper blur to the
SecKeyguardStatusView host. The native opening callback also resubmits it during
the fade. Because the fullscreen artwork is behind that host, window blur can
blur the replacement artwork as well as the wallpaper. The controls use their
own container and are not this surface.

The artwork binding worker uses the media drawable, cropping/scaling it as
needed, without applying a blur there. This distinguishes the inspected path
from an already blurred source bitmap; it does not establish the affected
user's exact provider build or backend.

MediaWallpaperBlur records native SemBlurInfo requests only for the legacy
status-host class. Once the fullscreen layer actually presents artwork, it
suppresses that host's blur. Closing, disabling or detaching releases the hold
using the latest native request, including a native clear. A restored blur
drawable receives the current native fade alpha. Clock, widget and controls
surfaces are not matched by this override.

This integration runs only from the 8.5 artwork backend and uses stable framework
and status-view contracts. The captured-blur callback through SystemUI is a
separate backend; this patch does not intercept that callback. The affected
user's settled fullscreen result still needs confirmation. State checks cover
native updates while held, release, native clears and independent hosts; the
existing media-return/fade probe checks the 8.5 layer lifecycle.
