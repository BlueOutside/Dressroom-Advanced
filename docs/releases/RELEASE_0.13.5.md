# Media-return glyph/blur synchronization — 0.13.5 alpha

VersionCode 98. **The user reports this candidate did not fix the long-open player case.** See RELEASE_0.13.7.md for the subsequent native cache-clear change. The user reported a small compact clock appearing over the restored stretched clock near the end of full-player collapse. A baseline 0.13.3 recording reproduced the exact artifact (`work/media0133-correct-frames/frame-46.png`, approximately 2.3 seconds into the recording).

## Native cache boundary

An initial 0.13.4 candidate delayed outstanding-layout blur requests until pre-draw. The user reported that the bug remained. That candidate is not considered a fix.

Targeted JADX reads of `ClockNonePaddingTextView` and `NonePaddingTextView` identify the missing boundary: `getPathForBlur()` can copy cached path U plus offsets O/P, while the parent text view updates U, O/P and its readiness flag R inside `onDraw`. Completing layout does not mean that this cached glyph shape has been refreshed. The native refresh itself remains necessary; the compact duplicate is an ordering problem, not a desired animation.

## Final implementation

- During the module-managed media return, defer native `setClockTextBlur` submissions until after the clock group's children have drawn. Invalidating the group ensures it re-records when necessary.
- Coalesce repeated requests per clock. A queued request continues to coalesce even after the manual-font restore finishes, so the final font's blur is rebuilt using fresh glyph paths.
- A scoped guard allows the final native rebuild through without recursively queueing itself. Completion removes the request before running its callback, and restores the scope even on failure. A new expansion cancels pending return work.
- Work is held in a weak-keyed map; the callback receives its clock argument rather than capturing it. No permanent pre-draw listener or timer remains. The draw hook performs an empty-map check when idle.
- Native font spring, saved manual-height fallback, palette return, depth, blur strength and animation duration are retained. No settings schema changes.

## Validation

Build/v3 signing and 200 Android checks pass across twelve suites. `ClockBlurSyncProbe` adds 20 checks using actual Android ViewGroup/Canvas child drawing with an onDraw-owned surrogate path cache. It distinguishes layout completion from draw completion, verifies the new cached shape reaches blur, coalesces requests, and covers cancellation, detachment, unrelated groups, reentrancy and callback failure. The other 180 optimization, geometry, depth, animation, touch and saved-state checks pass. The surrogate avoids headless system-font initialization; it does not claim to run Samsung's glyph renderer in the isolated probe process.

Installed 0.13.5, reloaded SystemUI, and confirmed build 0.13.5 runtime hooks under PID 329. Logs confirm the new `Media clock blur aligned after glyph draw` path ran, followed by manual-height restoration. The new transition recording is `/sdcard/media0135-close-test.mp4`; fine frame inspection is in progress. The user's visual confirmation and stock/non-fullscreen-player coverage remain pending.

No wallpaper settings were applied or edited during the test. Original USB keep-awake value 0 has been restored. 0.13.3 remains the prior optimization fallback; 0.13.4 is retained only as the failed pre-draw experiment.
