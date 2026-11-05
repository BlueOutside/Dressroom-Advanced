# 0.18.2-alpha: portrait and landscape profiles

Keep Advanced clock and widget settings separate for portrait and landscape. Clock/font selection remains part of the clock profile key. Samsung still owns native positions, provider IDs, widget ordering and its two coordinate sets; Now Bar extras remain shared.

The editor uses its native ScreenSize dimensions to identify the preview layout, with configuration orientation as a fallback for an unmeasured or square preview. Freeze outgoing depth geometry before native rotation changes the views. Keep drafts per layout during the editor session and freeze both into the normal Apply transaction. The orientation travels with each snapshot even if Apply completes after rotation. Runtime clock and widget owners reload the matching profile when their layout changes; widget sizing also invalidates its orientation-specific cache.

Old unsuffixed records remain available for rollback. The first known orientation to read a record claims a migrated copy; the other orientation starts with native/default Advanced settings. It must not inherit a screen-space mask baked for a different crop. Configure depth in each orientation before expecting it in both.

Validation: signed build, 117 JVM checks, 14 Android suites (380 checks), including 19 detached orientation/migration checks, plus read-only binding resolution against the tablet APKs. Installed on the P613. Runtime logs show saved landscape depth enabled with a mask, a separate empty portrait profile, and restoration of landscape depth after rotation. Temporary rotation settings were restored. Full editor Apply/reopen validation with different settings in both layouts remains pending. Foldable, 8.5 AOD and 9.0 runtime validation remain separate tasks.
