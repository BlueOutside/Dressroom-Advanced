# Native media motion - 0.19.0 alpha

Version code 129. Keeps the confirmed 0.18.8 clock source/measurement fix and 0.18.11 fullscreen close behavior.

## Framed player widget return

The reference provider restores widget contents and blur backgrounds as soon as its media-expanded observer receives false. The artwork spring is still running at that point. The new coordinator retains those native opacity requests, follows the existing artwork return progress, and clips widgets behind the moving native frame. It fades contents and the separate compositor backgrounds together. Completion, interruption and widget-root release restore the latest native state.

Samsung passes 255f into its float View alpha setter. During the fade that input is treated as visible opacity 1; multiplying 255 by the fade would otherwise keep widgets opaque until almost the last frame. Native values outside the fade are preserved.

## Fullscreen song changes

The fullscreen renderer previously drew the newest bitmap directly while hiding Samsung's masking and lighting views. It now follows the installed masking shader, native random seed/progress, image zoom and lighting. The controls blur records the same animated composition. The shader asset is loaded from the installed provider; no native shader source or APK resource is bundled in this repository.

The worker's new artwork bitmap must not replace the displayed image before Samsung posts its ImageView bind. The renderer keeps the displayed drawable identity and previous bitmap through that handoff. RenderEffect snapshots shader uniforms, so the effect is refreshed when progress, seed or output dimensions change. GPU pixel checks cover the real installed asset at zero, partial and completed progress, plus the delayed bind and timed software fallback. Native-owned bitmaps are never recycled by the module.

Only the reference 9.0 provider gets these optional bindings. An unavailable binding leaves the corresponding native path in place. The 8.5 media port is not expanded by this release.

## Validation

Signed build, 11 JVM suites (152 checks), 18 Android suites (463 checks), and read-only hook resolution against the phone's installed reference editor/provider pass. Counts overlap between suites. Repository checks and visual integration status are recorded in [validation](../VALIDATION.md). The user confirms both the framed-player closing widget fade and fullscreen song-change animation with matching controls blur on the reference phone.

## Deferred visual polish

At the user's request, leave these for a later change:

- Widgets disappear immediately when opening the player. The closing fade is confirmed; opening needs its own reveal policy.
- Samsung's song-change shimmer/reveal looks better in the framed player than fullscreen. Tune the fullscreen presentation without assuming a performance bottleneck has been measured.
