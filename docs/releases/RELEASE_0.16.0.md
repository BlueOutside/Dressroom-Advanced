# 0.16.0-alpha: initial One UI 8.5 widget controls

Adds the editor bottom-bar Advanced entry and a Widgets panel on the inspected 8.5 Dressroom builds. The first port includes physical widget scale, clock-edge attachment and free-position sliders. Samsung retains widget selection, capacity and native orientation control.

Existing widget IDs are preserved. Scale updates the native grid, editor measurements and attached providers’ size options. Widget settings join the native Apply snapshot without requiring the clock Advanced tab.

Custom capacity, larger picker variants, group dragging, widget depth/blur and Extras are not enabled in this port. Clock Style is validated separately from AOD. The signed build, 117 JVM checks and P613 read-only binding probe pass; on-device visual and save checks are pending.
