# Closing-player reveal - 0.18.11 alpha

Version code 128. Signed build, 152 JVM checks and 447 Android checks pass. Keeps the confirmed clock source fix and 0.18.10 foreground/blur corrections.

The user confirms the cover no longer drops behind the pill, but observes the two shortcuts appearing immediately and the incoming compact title crossing the moving artwork. This follow-up delays only `left_shortcut_area`, `right_shortcut_area`, and the current media card's normal-content root. The exact shortcut containers were read from the reference phone's hierarchy; the full-screen bottom-area parent is not faded.

The hold begins before native NowBarMediaCardView.D(false), rather than waiting for g0's deferred reparent/layout callback. This closes the first-frame reveal window. Native alpha requests are retained while a separate module factor stays at zero. The factor ramps from zero to one in the last 20% of the artwork's return progress. Native alpha is multiplied, never replaced; release restores the latest native value, including a zero value supplied during screen-off. Expansion/interruption and artwork cleanup release the held content.

The existing live blur-host alpha guard now also supports this reveal factor. Its factor-zero behavior for the clock blur drawable and native thumbnail is unchanged. Android regressions retain the old blur/drawable contracts and add native alpha updates during a partial reveal, repeated factor changes, final restoration, and a native hidden state arriving before release.

The clock source, native height spring, manual stretch, saved profiles and depth are unchanged. On the reference phone, the user confirms the shortcuts now wait, the compact title no longer crosses the returning artwork, and no flash is noticeable. The clock fix remains confirmed. The original USB keep-awake setting (0) was restored and temporary phone capture files were removed after retaining local evidence. Regular non-fullscreen player behavior and other devices are not newly certified.
