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

## Rejected 0.21.2 workaround

The user reports that overlap remains and the football card flashes when it moves
behind the front card. Removed the cancellation-listener override and the posted
Z-derived position reconciliation in 0.21.3. They did not address the cause.

## Slot assignment and refresh: 0.21.3

Checked the reference 9.0 `NowBarCardStackView.q` against its smali. The creation
loop advances once per slot. JADX shows two increments in its Java output; that
is a decompiler artifact, not a skipped second card.

The incoming item list assigns desired slots by `yf4.r(item)` identity. Existing
cards infer their current slot through `e(view, includeHiding)`, using translation
Z and child count. Slot zero is the front; `g(1)` and `g(2)` supply the second and
third offsets, with `f(slot)` supplying native scales. Swipe reordering changes Z
before spring animations return the cards. Do not infer settled identities from
those transient Z values in a separate reconciliation callback.

Two narrower lifecycle hazards are visible in the source:

- The same-depth refresh writes scale/Y directly without cancelling an earlier
  MOVE_OTHER animator. That animator can subsequently overwrite the refreshed
  pose. Before a non-busy collapsed refresh, cancel tracked MOVE_OTHER animations
  only on retained items. Preserve their current pose for Samsung's next movement.
- `hy6.c` cancels the preceding appearance animation before MOVE_OTHER, but its
  movement implementation `hy6.d` animates only scale/Y. Complete the interrupted
  SHOW opacity on the card and native blur drawable after native movement setup.
  Native depth/content visibility still determines which contents are visible.

No listener suppression, forced offsets, continuous redraws, or manual slot order
are used. Busy gates are `h0` (drag), `k0` (reorder), `n0` (dismiss), `U` (expansion
animation), plus native expanded state. `isExpandVIStarted` is JADX's display name
for runtime field `U`.

These changes address the observed source hazards; whether they resolve the
intermittent stack overlap/missing-card report needs on-device confirmation.
No test suites were run at the user's request.

## Card-dependent overlap: 0.21.4

User feedback on 0.21.3: flashing is gone, but overlap remains. A third card can
occupy the correct pose during the swipe and disappear when it settles. The
result depends on card types and order.

Read-only WindowManager view dumps expose a geometry mismatch without adding
module probes. In the captured collapsed stack:

- Music: wrapper 848 x 224, pivot Y 112, slot 0 / Y 0 / scale 1.
- Brief: wrapper 848 x 224, pivot Y 112, slot 1 / Y 30 / scale .92.
- External: wrapper 1280 x 570, pivot Y 285, slot 2 / Y 64 / scale .8.
  Its actual background and normal content are 848 x 224 at local Y 346.

The external card's pivot belongs at Y 458 (346 + 112), not 285. With the old
pivot, the visible bottom settles at Y 577; scaling around its pill center puts
it at Y 611.6. The front ends at 570 and the second ends around 591, so the
incorrectly scaled third card is covered despite having the correct slot/Y/Z.
This explains why reconciling slot offsets alone did not resolve this capture.

0.21.4 updates collapsed card pivots from the directly contained normal-content
bounds after the stack lays out or refreshes. This uses measured geometry, not a
card-type list or hardcoded offsets. Music's content is nested and its wrapper
already matches its pill, so it retains the native pivot. Expanded/expanding
stacks are excluded. No per-frame correction or forced visibility is added.

The user-reported front/second/third order was Player, DIY, DND; the read-only
dump also contains a Brief renderer. This capture establishes the wrapper/pivot
problem, but should not be treated as proof of every reported card identity or
of a complete fix before checking the new build.

After installation, the native view dump confirms the external-card pivot is
Y 458 with slot Y 64, Z 0 and scale .8 unchanged. SystemUI logs successful hook
installation. Rear-card visibility during AOD is intentionally native-controlled;
the final awake stack still needs user confirmation after cycling cards.

## Confirmed result and visible-card limit

The user confirms 0.21.4 fixes the reported overlap/disappearing-card behavior.

A request to increase the visible-card count needs more than a maximum override:

- `getStackMaxSize()` returns 1 in native restricted mode, otherwise 3.
- Refresh `q` independently removes existing cards at index >= 3.
- `e(view, ...)` decodes Z/child counts only for the native three visible slots
  and a transient fourth child.
- `h(slot)` assigns normal Z only for slots 0..2; later slots get -1.
- `g(slot)` provides offsets for slots 1 and 2; slot 3 repeats slot 2.
- The scale table contains four entries, with the last two identical.
- Swipe promotion explicitly maps old depths 0, 1 and 2. Later depths fall into
  the fallback instead of receiving a proper cyclic rank.
- Drag displacement explicitly handles three depths too.

Changing the getter alone would add cards that the native refresh removes or
places at the same depth, reintroducing overlap. Increasing the visible limit
requires coordinated refresh, rank/Z, scale/offset and swipe/drag changes. Native
restricted single-card mode must remain intact. No cap override was installed.

## Opt-in rear layers: 0.22.0

Rather than replacing the three-slot swipe engine, keep its three interactive
renderers and materialize later native cards in a passive sibling behind the
stack. Only native scale/offset getters for ranks 0..2 are resampled to fit the
selected total count in the original footprint. Native Z, rank decoding, refresh
removal rules and swipe promotion are untouched.

The tail reads the native ordered item list after refresh, clears before refresh
so shared external content Views can be promoted safely, and releases on stack
detach. Rear cards use native layouts, binding and backgrounds. They have no
separate hit targets or accessibility nodes. Native single-card/AOD and media
expansion hide the added rear layers. Extras stores an integer stackCards, default
3, clamped to 3..8. The control is shown only on the 9.0 reference editor.

This is a visible-stack extension, not a change to which Now Bar activities are
eligible. There must be enough available native cards to fill the selected count.
The optional renderer has not yet been visually confirmed by the user.

## Extended swipe handoff: 0.22.1

0.22.0 is not a complete extension: native release sends the outgoing card to
slot 2, and its subsequent model refresh installs the actual next card there.
The user observes this replacement and order-dependent missing rear layers.

The native process log identifies a concrete second cause: media `y0` casts its
parent to NowBarCardStackView before checking whether the wave should run. In a
passive tail host, this throws during onAttachedToWindow, clearing the rear set.
The fix skips this wave-only method solely for rear-hosted media cards.

During a qualifying ACTION_UP, a scoped depth override maps only the outgoing
card after its Z reassignment to the actual last visible rank (or one beyond the
visible limit when more items exist). Scale/offset overrides accept that release
rank. Native gesture thresholds, Z changes, spring timing and model rotation
remain in charge. Cancel/reset gestures retain their native behavior.

While the native spring runs, a temporary pre-draw callback interpolates rear
cards into their promoted positions using that spring's actual Y progress. The
outgoing native card remains attached to its native owner, but draws in the tail
below the rear cards. A hidden look-ahead rear card fills the last visible slot
when the total item count exceeds the selected visible count. The callback is
removed at refresh, interruption, hidden state or detach. Busy refreshes leave the
in-flight tail intact. At the final refresh, appearance animations are briefly
disabled only for the already visible handoff, then the native flag is restored.

No separate timing loop, slot-order mutation or native-card reparenting is used.
The update still needs visual confirmation for cycling and rapid interruption.

The first 0.22.1 phone capture confirms rear media attachment no longer reports
the parent cast exception. It also reveals a new native rear card at 0 x 0 under
a correctly sized tail host: insertion happens after parent measurement. Position
now explicitly measures the tail with the native stack's exact size when its
layout is requested or measured size differs, before laying it out.

After the measurement correction, a targeted swipe on the phone completes with
no reported rear/handoff exception. Its native window dump shows the rear media
card visible and measured at 1280 x 570, scale .8, Y 63.75. Three active native
slots plus that rear layer are present. This verifies attachment and settled
geometry, not visual smoothness throughout the transition.

## Clock Style 8.5 backport: 0.23.0

JADX's Clock Style source and local DEX declarations agree on:

| Role | 9.0 AOD | 8.5 Clock Style |
| --- | --- | --- |
| Ordered items / restricted mode | p / P | b / I |
| View mode / screen-on flag | M / O | F / H |
| Drag / reorder / dismiss | h0 / k0 / n0 | b0 / e0 / h0 |
| Expansion-animation flag | U | N |
| Animation manager / enable flag | V / hy6.p | O / LT0.b |
| Move entry | hy6.c(View,qx4,int) | LT0.d(View,Io0) |
| State/model | ck0 / qx4 | Ak / Io0 |
| Identity helper | yf4.r | o71.B |
| Stack-size lambda | vx4 | No0 |
| Bind item / hide contents | U / s | P / o |
| Resource package | aodservice | clockpack (0x7d) |

Both providers use the same native slot remapping and spring targets on release.
The old media renderer has no wave check or parent cast equivalent to 9.0 y0.
The engine therefore takes a checked binding, with provider names kept in their
version folders. Extras shows the count for 8.5 only when its loaded clock
provider resolves as Clock Style. This does not claim 8.5 AOD support.

Native 8.5 refresh also has the same-depth path without cancelling old MOVE
animators and the movement path without restoring an interrupted SHOW opacity.
The shared repair and pill-centered scaling apply to both providers. Orientation
changes invalidate resource offset caches, before native configuration handling.

The signed 0.23.0 is installed on the P613. After SystemUI reload and wake, the
process reports the refresh/pivot repair and configurable rear layers installed.
No stack binding exception appears in the inspected startup log. Count selection,
extra-card appearance and rotation still need user verification on the tablet.

## AOD 8.5 mapping: 0.23.1

AOD 8.9.32.95 uses items b, restricted J, view mode G, screen-on I, busy flags
c0/f0/i0, expansion O and manager P (nq0, enabled b). Its model is jA0 with Yo
states, identity is eJ1.G, size callback oA0, item binding R and content hiding p.
Its manager entry c(View,jA0) is two-argument, like Clock Style's equivalent.
There is no 9.0 media wave method. Resource lookup uses aodservice.

These fields and method signatures pass direct DEX declaration checks against
the user's referenced AOD APK. Native source shows the same three-slot release
logic, same-depth refresh and interrupted appearance-fade behavior. The shared
engine is wired through OneUi85AodStack. This is an experimental binding, pending
an actual 8.5 AOD device; the connected P613 still runs Clock Style.
