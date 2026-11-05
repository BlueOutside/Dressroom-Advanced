# Intermittent Now Bar stack overlap

The user reports that the second card in the visible three-card stack sometimes
nearly overlaps the front card, leaving only a thin border. Captured on the
One UI 9 reference phone on 2026-11-05 without restarting SystemUI. Screenshot
and accessibility bounds are retained locally under `.build/stack-phone.*`.
The user suspects other versions are affected; this has not been verified on 8.5.

The inspected stack uses its own scale table and dimension resources
`now_bar_cardview_2nd_card_bottom_offset` and
`now_bar_cardview_3rd_card_bottom_offset`. These should remain the source of
positions; forcing fixed pixel offsets or correcting every frame would interfere
with native drag and expansion animations.

On 9.0, `aod.hy6.d` starts the scale/Y return animator. Its `aod.zj0` end listener
always reapplies the saved scale and Y; cancellation does not mark or guard that
write. This is a possible stale-position path, not a confirmed cause of the
captured overlap. The exact triggering sequence is still unknown. No workaround
has been installed, and no build or test suite was run for this investigation.

## 0.21.2 workaround

At the user's request, add a reference 9.0 workaround and install it without
running test suites. Cancelled native return listeners no longer reapply their
old scale/Y target. After native refresh, attach or a finished gesture, a coalesced
callback waits for tracked card animations, then reconciles visible cards' Y
against the stack's own depth/offset methods. Dragging, media/settings expansion
and expansion transitions are excluded. Scale, Z and resource values are not
replaced. No continuous pre-draw correction is installed.

Compilation/signing and hook startup are checked; the intermittent visual result
still needs user confirmation. This workaround is not yet ported to 8.5.

The runtime expansion flag is `U`; `isExpandVIStarted` is JADX's display name.
The corrected phone build logs successful stack-repair installation.

The runtime expansion flag is `U`; `isExpandVIStarted` is JADX's display name.
The corrected phone build logs successful stack-repair installation.
