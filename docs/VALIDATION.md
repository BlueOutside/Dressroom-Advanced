# Device validation

| Device | Software | Coverage |
| --- | --- | --- |
| SM-S948B | One UI 9.0, Dressroom 3.0.10.38, AOD 8.9.66.1 | Clock, widget, depth and Now Bar development device |
| SM-P613 | One UI 8.5 custom ROM, Dressroom 2.9.60.50, Clock Style 8.9.35.60 | Clock Style and large-screen layout development device |
| No device yet | One UI 8.5, AOD 8.9.32.95 | APK bindings checked; runtime untested |

Font-file import, live Weight adjustment, depth without Samsung stretching, Apply/reopen and lockscreen persistence are user-confirmed on both development devices. Other axes depend on the chosen font; the parser exposes adjustable fvar axes, including hidden ones.

Portrait/landscape profiles and the widget depth orientation controls are user-confirmed on the tablet. Fullscreen artwork, retained charging cards and the Now Bar stack have also been exercised during development; this does not establish compatibility with every Samsung build.

The user confirmed the tablet popup stays above the keyboard without bouncing and returns to its original position when the keyboard is dismissed. Slider/group spacing has been revised; the final endpoint thumb padding still needs a visual check. Build and repository checks do not validate native appearance or animations.

See [font bindings](compatibility/FONT_BINDINGS.md) and [the stack investigation](research/NOW_BAR_STACK.md) for renderer details.
