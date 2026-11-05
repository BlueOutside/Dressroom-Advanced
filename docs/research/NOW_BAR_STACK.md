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
