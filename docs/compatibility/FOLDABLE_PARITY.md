# Foldable follow-up: 1.0.10-fold8-test

This update addresses the remaining source-audit findings on the supported
Samsung editor/provider adapters. Existing adapters and global legacy records
are retained.

## Changes

- Runtime widget state includes screen identity as well as orientation. A fold
  event forces the existing controllers to reload geometry, depth and blur,
  including when both screens have the same orientation and clock font.
- Wallpaper identity explicitly selects main or sub lock wallpaper (Samsung
  which values 6/18), with the corresponding system wallpaper fallback (5/17).
  A detached on-device probe resolved main ID 62 and sub ID 56. No wallpaper
  image was exported or changed.
- Existing generic-wallpaper preferences are imported into explicit screen
  identities once per legacy layout key. Old records remain intact. Changing
  wallpaper later cannot repeatedly import the old baked depth mask.
- Extras settings, including charging-card retention, fullscreen artwork,
  stretch retention, custom text and stack count, have separate main/sub keys.
  Both initially inherit the existing shared options. Editor snapshots retain
  each screen's identity and acknowledge only matching committed drafts.
- Extras runtime caches and recovery values are separated by screen. Fold
  events refresh registered charging, Now Bar, stack and artwork listeners.
  Failure to read one screen's settings does not reuse the other's options.
- Clock/media contrast accepts a Fold display instead of requiring display 0,
  while still requiring the clock and artwork to share display and window.
- Folding cancels pending module depth-return callbacks, clears artwork return
  geometry and contrast state, and reapplies the active screen's effects.
  Samsung's own animations are not indiscriminately cancelled.
- Clock panel identity includes screen. Stale widget/Extras controls and color
  dialogs cannot apply changes or restore an old snapshot onto another screen.
  Fold events advance the editor generation to reject stale asynchronous work.
- Hardware detection uses Samsung's LOCKSCREEN subdisplay capability on the
  module's supported Android versions rather than only one model string.
  The inspected Fold8 native profile migration/selection adapter remains
  restricted to its known model and AOD revision; uninspected vendor bindings
  are not automatically treated as compatible.

## Validation and limits

Passed: 252 JVM checks, 206 targeted Android checks, 25 settings/recovery
checks, 24 FoldParityProbe checks, and 34 synthetic Samsung profile checks.
MediaReturnProbe includes folding during a pending native return, listener
cleanup and an old completion racing a newly expanded player. Extras and
wallpaper migration tests use detached in-memory preferences; wallpaper ID
lookup is read-only against the real framework.

This does not certify every live fold timing, every wallpaper crop, or every
Now Bar/music-provider animation on every foldable. Physical validation is on
SM-F971B, with the installed inspected Samsung APK revisions. Other hardware
and firmware require their own binding and live validation.

## Installed verification

Installed 1.0.10 (versionCode 1010) in place and reloaded Dressroom, AOD/provider
and SystemUI with root killall. Fresh SystemUI logs report the clock, widgets,
native screen selection, depth, charging, artwork and Now Bar hooks installed.
The active DualDisplaySub:Portrait item retained widget IDs 636/637/638.
The module preferences contain distinct options_fold_main/options_fold_sub
records, initially matching the retained legacy options. No custom text content
was included in diagnostic output.

The user opened Extras on both screens and confirmed that each screen label is
correct and an Extras change on the cover leaves the inner setting unchanged.
A current cover-editor screenshot also showed the separate cover wallpaper,
clock and three-widget selection. No wallpaper was changed by the test probe.
