# 0.18.1-alpha: Clock Style widget depth lifecycle

Follow native widget layout completion, initialization and wake/display-state updates instead of relying only on the first container attachment. Coalesce nested callbacks on the main thread and cache the selected clock profile between dark-amount frames. Clear depth and placement from replaced or released containers. Restore the depth outline when native code replaces it without changing the widget's geometry.

The P613 passes 117 JVM checks, 13 Android suites (361 checks) and read-only resolution of the added Clock Style callbacks. Installed on the tablet with SystemUI reloaded; ADB captures verify actual landscape lockscreen widget clipping before and after screen-off/on. User confirmation and portrait/rotation coverage remain pending. The separate 8.5 AOD provider remains outside this depth port.
