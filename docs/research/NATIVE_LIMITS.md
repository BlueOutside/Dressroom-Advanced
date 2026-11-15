# Samsung lockscreen limits

These findings refer to the One UI 9 reference APKs. The same limits can
sit in different callers on 8.5, even when the provider uses the same class.

| Native limit | Current status |
| --- | --- |
| The clock, widgets and Now Bar lose blur as soon as an unlock swipe starts. | Already covered by Keep blur while swiping to unlock. |
| Widget blur on wallpaper AOD lowers the curve ceiling to 70 for some presets and applies a black scrim to the white-clock/dark-wallpaper preset. | Keep widget blur colors on AOD uses the native lockscreen curve and tint. Confirmed on the reference phone in 1.1.53. The separate AOD widget profile in 1.1.55 adds radius, saturation, a linear curve, and tint including None. |
| Clock blur is restricted to types 1, 2, 7, 9, 10 and 11. | The existing Clock blur switch bypasses the whitelist for styles with a native blur renderer. Styles without native glyph outlines still need their own renderer. |
| Clock blur clears immediately for a blocker and returns after 10–500 ms, depending on the transition. | Shorter blur return delay reschedules media, stretch, alpha and swipe callbacks on the next frame. Native visibility checks and the original cancellation handle still apply. |
| Samsung compresses clock text alpha and applies hard-coded wallpaper brightness curves. | The text visibility slider now writes the native alpha target directly where its binding matches. Neutral blur colors switches to a linear curve and keeps the chosen saturation adjustment. |
| AOD clears live clock blur during doze even when a wallpaper is shown. | Keep clock blur on AOD keeps the existing native layer for a configured clock in wallpaper AOD. It stops when the wallpaper or window blur is turned off. |
| Native clock suggestions reject small height spreads or large vertical ratios. The font setter also normalizes neighboring heights. | Allow more stretch shapes accepts valid calculated heights that the native suggestion rule rejects. Manual stretch already bypasses the renderer's normalizer. |
| The phone editor restricts grid rotation and widget capacity. | Custom grid and rotation already override these where the provider supports them. |
| Expanding Now Bar media clears widget blur and hides widget content. | Fade widgets when opening the player fades the existing contents and window blur together before clearing the blur. |
| Camera 2×2 gets no widget background. Picture Frame gets a fixed dim layer. | The Widgets blur page has separate switches for the camera background and the Picture Frame scrim. |
| The Now Bar normally shows three cards and removes the charging card when its animation ends. | Visible cards and Keep charging card already cover these. |
| SystemUI limits notification glass by device capability, notification type and lockscreen mode. | Separate switches make colorized and custom lockscreen cards eligible. Media, themes, system blur, power saving and lockscreen mode still follow Samsung's policy. |

The notification switches apply to notification cards. Now Bar has its own
Glass backgrounds for colored pills setting on One UI 9. It uses Samsung
window blur for the native pill background and replaces the gradients on
the compact media pill and its long-press menu. Now Brief uses the same
setting to clear the tint in its native GenUI BlurStyle.

Wallpaperless AOD, disabled system blur and Reduce Transparency have their
own rendering paths. Preserve those when changing a wallpaper-AOD preset.

The new 1.1.54 overrides target the One UI 9 reference. A matched renderer
and a visual check are still needed for each path and for 8.5 backports.

Source notes are in [the clock deep dive](ONE_UI_CLOCK_DEEP_DIVE.md),
[the widget deep dive](ONE_UI_WIDGET_DEEP_DIVE.md),
[the SystemUI deep dive](ONE_UI_SYSTEMUI_DEEP_DIVE.md) and
[the Dressroom deep dive](ONE_UI_DRESSROOM_DEEP_DIVE.md).
