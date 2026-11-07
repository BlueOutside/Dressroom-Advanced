# Font file and axis bindings

The document picker, private font storage, fvar controls, axis profiles and Apply/cancel flow are shared. Native renderer operations live in `FontBinding`; each provider adapter supplies its checked method names.

| Provider | Fixed typeface | Variable typeface | Font render |
| --- | --- | --- | --- |
| One UI 9 AOD | C | B | g0 |
| One UI 9 Clock Style | A | z | d0 |
| One UI 8.5 Clock Style | x | w | X |
| One UI 8.5 AOD | A | z | b0 |

All four inspected providers use the same five-argument `ClockFontData.copy` contract. Bindings validate the return and parameter types before hooking. The concrete clock subclass supplies the actual render override.

An imported font uses a non-stretching drawing request. Its saved Samsung font identity remains unchanged. Depth stays available, but imported profiles neither restore manual heights nor acquire a manual-stretch hold from depth. Returning to the Samsung font restores its stretch capability.

Keep the live preview's imported-file ID and axis values together. Re-discovering the ID through a previous renderer callback lost it and made the Weight control do nothing. Capture native font target views directly; do not apply the font recursively to every text view in the clock.

One UI 9 file import, live weight and depth/stretch behavior are user-confirmed. On 2026-11-07, the user also confirmed the complete 0.25.0 flow on the P613 One UI 8.5 custom ROM with Clock Style: file import, live Weight, depth without stretching, Apply/reopen and lockscreen persistence. The 8.5 AOD declarations have been checked against the inspected APK, but that shim still needs a real AOD device.
