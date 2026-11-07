# Selected World Clock identity and composite clipping - 0.12.9 alpha

VersionCode 91. The user confirms wallpaper-style detection now seemingly works across their styles for the current subject. They confirm World Clock still fails on 0.12.7; the assistant also reproduced failure in the 0.12.8 container experiment.

## Confirmed native contract

Dressroom `vo.x` checks model type 50002, obtains `bp.q`, and binds `bp.q.s` through `oc.a.e`. `bp.q` constructs that `s` field from the local **CityClockExtension** view. The active renderer can therefore be CityClockView (provider type 2097872928), while the selected model remains World Clock (type 50002). The native UI hierarchy puts local/remote city rows in Dressroom's world_clock_view list under clock_preview. AOD WorldClockView itself uses a different nested child layout.

Earlier changes excluded unselected children but still read the selected renderer's provider type. Detection therefore published the subject into the wrong profile, and the class-name condition for the composite clip did not match.

## Change

- Both selected **type and font** come from the native editor model whenever its active renderer is the same instance. The provider's own identity remains a fallback when no selected editor model applies.
- The World Clock container clip is chosen from Dressroom's visible world_clock_view hierarchy, rather than assuming the selected renderer has the WorldClockView class name.
- The container covers city rows regardless of which provider view owns them, with native outline restoration on style switch, disable, and editor reset. No new runtime/AOD render-node clipping is enabled.
- Prior filter/source/weather changes are retained without another segmentation rewrite.

## Validation

Final build/v3 signature and 40 Android checks pass: 12 clock/editor/composite clip checks, eight weather/source ownership checks, and 20 existing depth contracts. Subsequent testing verified World Clock idle clipping using placement controls (`work/world129-placement-on.png`), but exposed manual stretch being restored on a non-stretchable city renderer, which interfered with placement. The final native drag/release fix is in RELEASE_0.12.10.md. Draft discard/restart was explicitly authorized; final wallpaper Apply/Done has not been used in the tests.

Calendar/Analog idle depth is visually verified from earlier builds. Wallpaper styles are user-confirmed for the current subject. Snow preview and existing static depth were exercised together. Arbitrary frame cutouts, fresh weather-wallpaper detection and other-style saved lockscreen/AOD behavior remain unverified.
