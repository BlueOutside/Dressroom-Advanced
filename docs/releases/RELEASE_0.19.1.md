# 0.19.1 — Clock Style stability backport

The 8.5 Clock Style runtime now supplies saved manual heights to Samsung's font
source and measurement paths, instead of restoring only the visible clock.
Depth-owned manual stretch uses the same source. The native height animation
still owns timing; the 9.0 animation and artwork code are unchanged.

Version-specific measurement callers live in `compat/oneui85` and
`compat/oneui9`. Existing shared binding classes live in `compat/shared`.
These are source-folder moves, with no Java package or hook-name changes.
The full source contract is resolved before optional hooks are installed.
8.5 AOD and 9.0 Clock Style remain unsupported.

Widget fixes on 8.5:

- Clear old root effects when the controller switches to a non-main display.
- Mark a scale refresh complete only after native refresh succeeds.
- Retry a missing clock profile on subsequent native callbacks.
- Remove the previous clock's depth mask while the provider has no clock item.

The signed build, 152 JVM checks and 17 Android suites (447 checks) pass on the
P613. The reference AOD shader suite was excluded because the tablet has no AOD
package. The binding probe resolves the tablet's actual editor/provider APKs,
including all six source/measurement dependencies. JADX confirms that Clock
Style measures stretched height in `FaceWidgetClockPage.w()`, unlike 9.0's `y()`.
Runtime startup and the actual portrait/landscape behavior are tracked in
[validation](../VALIDATION.md).

The user confirms saved clock stretch and widget depth survive portrait/landscape
rotation and lock/unlock on the P613 without opening Advanced or reapplying.
