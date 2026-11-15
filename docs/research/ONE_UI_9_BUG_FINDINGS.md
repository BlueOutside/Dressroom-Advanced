# One UI 9 bug findings

Reference AOD 8.9.66.1, Dressroom 3.0.10.38 and their reference SystemUI/framework.
The names below are JADX aliases. APK comparisons should use the raw DEX roles
and confirm their callers. The 1.1.42 implementations are listed below. The
reproduction and visual checks are still pending.

| Finding | 1.1.42 implementation |
| --- | --- |
| NOWBAR-A | Mirror native stack opacity to the rear sibling. Forward blur alpha through the native helper when its DEX body matches. |
| NOWBAR-B | Refresh existing rear cards' native mode and doze inputs on stack state changes. |
| NOWBAR-C | Restore rear scale, Y and alpha when a handoff stops. Discard posted work if the tail has changed. |
| NOWBAR-D | Keep one pending count refresh through a busy animation. Read the live item list at completion and cancel the request on detach. |
| BLUR-A | On settled wake, recover a missing drawable or stale zero opacity when a live window-blur request and the current widget or front-card owner agree. An explicit native clear remains authoritative. |
| DEPTH-A | Scan the original bitmap for layered previews when the wallpaper layer fills the composite bounds and the source aspect matches. Keep native preview input when alignment cannot be verified. |

## NOWBAR-A · Rear cards miss native opacity changes

**Confirmed path mismatch.** The native `NowBarController` alpha listener sets
the stack alpha and calls `NowBarCardStackView.updateCardBlurAlpha` (raw `y`)
on each direct child. That helper handles ordinary background drawables, the
media settings background and Brief's GenUI blur separately. Our extra cards
are in a sibling `Tail`. Its alpha is copied only from the module's stack
state hooks. Those hooks are not called by this alpha listener.

Expected result: with four or more cards, the rear layers can keep their
previous opacity during a keyguard fade even while the front stack fades.
Forward the native update to the active tail and its cards, with an empty-tail
exit. Compare collapse, cancelled unlock swipe and shade transitions at three
and five cards. For backports, locate the actual alpha fan-out and per-card
blur helper on each provider.

Module locations: `NowBarStackLayers.install`, `NowBarStackLayers.sync`.

## NOWBAR-B · Rear cards can retain the AOD view mode on wake

**Confirmed path mismatch.** Native `setNowBarViewMode` updates only direct
children, stores the mode, and calls `updateCardVisibilityByExpandState`
(raw `z`). `rebuild` can create tail cards while the root is in AOD mode and
then hide them through `sync`. On return to the lockscreen, `sync` can show
the same nonempty tail without updating its cards' mode or rebuilding it.
Subsequent native refresh may mask the problem.

Propagate native mode changes to the tail or rebuild it at the mode boundary.
Check a tail built in AOD and a tail retained across two sleep/wake cycles.
For backports, compare mode enum values, the mode setter and its visibility
callback. Confirm whether the provider has a separate dozing setter.

Module locations: `NowBarStackLayers.rebuild`, `NowBarStackLayers.sync`.

## NOWBAR-C · Interrupted promotion can leave intermediate rear poses

**Confirmed cleanup gap.** `Tail.advanceFrame` writes scale, Y translation
and alpha while following the outgoing card. The module calls `Tail.stop`
before forwarding the next native touch-down. `stop` removes the pre-draw
listener and clears the outgoing card without restoring those properties.
It also bypasses the refresh normally queued when `advanceFrame` detects a
finished reorder. Native `resetCardPositions` (raw `s`) walks only direct
stack children. A tail layout restores scale and Y. Rebuilding the tail also
restores alpha.

Give interrupted handoffs a way to settle the tail without restarting the
native spring. Recheck touching during a promotion, releasing without a swipe,
and immediately starting a second swipe. For backports, verify which callback
owns reorder completion and how native reset affects only its own children.

Module locations: `NowBarStackLayers.install` touch hook, `Tail.advanceFrame`,
`Tail.stop`, `Tail.onLayout`.

## NOWBAR-D · Stack count updates can be discarded during an animation

**Confirmed dropped request.** The Extras observer calls native
`refreshAllCards` (raw `q`) immediately. Our refresh hook passes through
while busy. The reference native method returns before consuming its argument
when dragging, reordering or dismissing. It sets a deferred refresh flag
only for dragging. An Extras-only update during reorder/dismiss can therefore
change the configured count while leaving the rear layers built for the old
count until another native refresh.

Keep one pending count change per stack and reconcile it when the native
operation settles. Use the live item list at that point. Check changing the
count or switching an Extras profile during each busy state, including a
reduction to three. For backports, compare the early return flags and the
native settled callbacks.

Module location: `NowBarStackLayers.install` refresh hook and Extras observer.

## BLUR-A · Wake recovery only reaches existing, nonzero drawables

**Confirmed coverage gap, original incident unresolved.** The doze recovery
refreshes compositor registration and can rebuild a visible drawable with
an empty native position on wake. It skips zero-alpha or absent backgrounds.
It also cannot find a surface after a native null submission removes it
from the module cache. Samsung's native widget and Now Bar code owns drawable
alpha separately from content visibility. An empty region, alpha zero, and
a native clear can look similar while requiring different fixes.

Trace each native owner's first eligible update after a minute tick and after
wake. If the missing case has no drawable or zero alpha, recover from the
current owner's request rather than replaying a pre-doze snapshot. Include a
case without live swipe blur to distinguish this from gesture state.

Module locations: `DozeBlurRegions.schedule`, `BlurAppearance.submit`.

## DEPTH-A · Detection pixels and source identity can come from different layers

**Confirmed input mismatch on layered previews.** The reference
`CropImageView.getDrawableInner` returns the live ImageView drawable.
`setLayerBitmap` can wrap it in a `LayerDrawable`, and
`updateObjectLayerVisibility` changes the extra layer's alpha.
`LiveSubject.capture` rasterizes that composite. Its fingerprint prefers
`getOriginalDrawable`, so the same original wallpaper may retain its identity
while a new scan sees different pixels. A detection miss or a different mask
from this path is plausible and still needs a matching wallpaper case.

Compare the original and rendered scan input for the same image with its extra
layer hidden and shown. Before selecting a different detector input, check
the original-to-preview transform and preserve crop/zoom and saved alignment.
For backports, check both drawable getters and which layer owns the crop bounds.

Module locations: `LiveSubject.capture`, `LiveSubject.fingerprint`.
