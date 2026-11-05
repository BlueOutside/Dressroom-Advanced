# 0.20.0 — Initial One UI 8.5 AOD shim

Targets the inspected AOD 8.9.32.95 with the existing 8.5 Dressroom bindings.
Dressroom version selection remains separate from the runtime clock provider.

- Add AOD-specific font-source and native measurement callers.
- Resolve the native blur owner, host origin and clipping helper before installing depth hooks.
- Enable clock depth only after those hooks install successfully.
- Reuse the 8.5 widget lifecycle, sizing and depth policy with AOD's provider.
- Adapt final widget blur submissions without forcing blur onto unsupported devices.
- Retain the established 9.0 mapping and Clock Style path.

Native clip paths are preserved on optional clipping errors. Invalid host coordinates
skip the override. Failed widget helper resolution can be retried rather than being
marked complete. No full-screen media, glyph-cache optimization or custom clock-blur
control port is included.

The AOD and matching Dressroom APKs were declaration-checked on the tablet without
installation. This does not validate AOD rendering, save/reopen, media transitions,
cover displays or SystemUI plugin compatibility on a real 8.5 AOD phone. The tablet's
SystemUI provides a framework test host, not a substitute for that device test.
See [validation](../VALIDATION.md) for check results.
