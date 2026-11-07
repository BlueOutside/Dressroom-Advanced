# 0.21.0 - Fullscreen artwork on One UI 8.5

Add a separate 8.5 artwork backend and expose the existing Full-screen album
artwork switch in Extras. The native music-holder constructor is mapped for
Clock Style (`aod.TK`) and AOD (`aod.YR`). Source views are identified by their
native resource roles.

The fullscreen layer sits behind the clock/player. It samples the displayed
ImageView bitmap and inherits the native artwork/page opacity, including updates
while the framed copy is hidden. It does not invent a thumbnail-return animation,
resize the native card, move controls or add a controls-blur band. The framed
artwork, its masks and outer background are suppressed; the native title, artist,
controls and control background remain in place. Disabling the setting or losing
the native source restores the latest native opacity/background state.

No bitmap readback or per-frame bitmap allocation is used. Static artwork is
center-cropped by the GPU. Native bitmap ownership is retained. The fullscreen
layer does not yet reproduce the framed player's song-change shader/lighting.

The 8.5 media-depth suspension from 0.20.1 is retained. The 9.0 fullscreen backend
is unchanged. On-device results are tracked in [validation](../VALIDATION.md).
