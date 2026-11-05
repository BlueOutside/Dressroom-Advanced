# 0.21.1 — Follow the native wallpaper fade

The user confirms 0.21.0 fullscreen artwork works on the tablet, but closing
removes it immediately with the native framed view. Follow the separate native
wallpaper blur/dim animator instead: preserve the last artwork texture until its
200ms return reaches zero, and use its native opening opacity too.

Clock Style and AOD have different observer fields; both are mapped from their
inspected source. Listeners are weak, replaced on reopening and removed on root
detach. Missing native animation falls back to the original source visibility.
The clock, controls and saved wallpaper are unchanged.

See [validation](../VALIDATION.md) for checks and device results.

The tablet user confirms the closing fade and reopening during the fade work correctly.
