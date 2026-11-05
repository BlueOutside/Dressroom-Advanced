# One UI 8.5 media player

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
Fullscreen artwork is still not ported to 8.5.

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
integer opacity 0–255: opening is 300ms (or immediate for the native nonzero
transition condition), while closing is 200ms. This is not the clock's 300ms
return animator.

The framed artwork can disappear before that wallpaper transition ends. The
fullscreen backend therefore follows the wallpaper animator and retains the last
native texture during close. Native observer callbacks only update opacity and
invalidate the window; source selection/restoration waits until pre-draw, after
Samsung finishes replacing its views and animator. Reopening replaces the old
listener, and disabling the setting releases the retained texture immediately.
The actual wallpaper/provider settings are not changed.
