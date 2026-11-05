# 0.18.4-alpha: reachable widget effects and clock-panel scrolling

Expose the Effects sub-tab on the 8.5 widget panel. Its appearance controls were assigned page 2 while the tablet still created only two tabs, leaving the existing depth controls unreachable. Reject page groups with no tab so this mismatch cannot fail silently again.

The native clock sheet can clip the bottom of the Advanced scroll viewport without changing its measured height. Account for that clipped area in ScrollView's bottom inset, including the current scroll offset. Keep the existing content spacing, clamp the scroll position when the inset shrinks and remove the viewport listener on detach. This lets the final row reach the visible bottom when the sheet changes size.

The signed build and 117 JVM checks pass. The P613 passes 15 Android suites (402 checks), including eight real-view checks for a clipped parent, a fully visible final row at maximum scroll and repeated resizing without accumulating insets. Installed on the tablet; Dressroom restarted with permission to discard its test draft. SystemUI does not need a reload for this editor-only update. On 2026-11-04, the user confirmed the widget depth controls are visible and work, and the longer clock tabs show their complete last row when scrolled to the bottom on the P613.

The user confirmed portrait-first Apply/reopen works in 0.18.3. The reported portrait/landscape color difference uses Samsung's automatic color mode; no forced clock-color change is included.
