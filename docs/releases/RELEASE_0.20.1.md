# 0.20.1 - Native 8.5 media depth lifecycle

Fix the missing connection between 8.5's native media-expanded flag and the
module's wallpaper-depth suspension. The clock should not retain subject cuts
while it is displayed over media artwork. Resume depth after the native clock
return completes, including a final layout frame. Reopen/interruption and a
missing animator have explicit cleanup paths.

Clock Style and AOD have separate observer/animator mappings. Existing 9.0
media hooks remain unchanged. No thumbnail motion, fullscreen player or controls
blur layer is added to 8.5. See [the research notes](../research/MEDIA_8.5.md).

Device verification is tracked in [validation](../VALIDATION.md).

The user confirms the fix on the P613 in portrait and landscape: the player clock
has no wallpaper-subject cuts, and depth returns correctly after closing.
