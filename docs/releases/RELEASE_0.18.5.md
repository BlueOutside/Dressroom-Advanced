# 0.18.5-alpha: widget lifecycle stability

Cancel queued widget refreshes by ticket, so callbacks from a released lifecycle cannot consume work scheduled after reinitialization. Remove old root-layout listeners on replacement/release and use the same helper for the reference editor preview. Ignore the tablet editor's delayed resize callback if its owner/session changed or the view detached.

Watch Samsung's wallpaper-color handoff and check the cached wallpaper identity there. Changing wallpaper while keeping the same clock type/font can now invalidate the widget settings/profile cache without adding wallpaper-ID polling to animation frames.

The signed build passes 129 JVM checks, 16 Android suites / 407 checks and read-only tablet binding resolution. The wallpaper callback/context declarations also match the original 9.0 AOD DEX. Installed on the P613 with permission to discard the editor draft and reload scopes; startup is normal, and the user confirmed widget size, placement and depth survive repeated editor opens, rotation and lock/unlock. External wallpaper replacement remains unverified. Reference-phone execution remains pending.

See [the native lifecycle audit](../research/AUDIT_8.5_LIFECYCLE.md) for confirmed source behavior and remaining investigations.
