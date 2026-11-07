# Shared One UI 9 foldable support

Starting with 1.0.11-oneui9-fold-test, eligibility is based on One UI 9 and
Samsung's LOCKSCREEN/COVER subdisplay capabilities. No model name or Fold8
exception is used. The module's existing API 36/37 support boundary remains.
Book-style and clamshell capabilities are both recognized. One UI 8.5, other
One UI major versions, unknown capabilities and non-fold devices do not enter
this foldable path.

Screen profiles, wallpaper identity, Extras, screen-change refresh, vertical
default docking and separate widget-depth surfaces use this shared eligibility.
The native profile adapter checks its storage and SDK method/field contracts
before installing hooks, rather than requiring one model or numeric AOD
version. Migration initializes every native ProfileScreen enum value, covering
native main-screen variants such as Fold and Tablet. Missing contracts disable
that capability with a diagnostic; unrelated features retain their adapters.

Obfuscated mappings are still specific to app interfaces. They are named
OneUi9Aod8957Runtime/Blur/ClockSource/Stack and OneUi9Editor3001Widgets instead
of Fold8. Editor variants use structural checks for compatible One UI 9 fold
builds outside the already mapped version numbers. Removing a hardware gate
does not make unrelated Samsung method names interchangeable.

The original Fold8 compatibility and screen-profile notes retain the historical
device investigation. Their old model/version restrictions describe earlier
test builds; this document describes current eligibility. Physical validation
remains on SM-F971B with Dressroom 3.0.01.20 and AOD 8.9.57.1. Other models,
including Flip cover UI, still need live validation and compatible app bindings.

Validation: 264 JVM checks (including 12 model-independent capability cases),
206 targeted Android checks, 25 migration/settings checks, 24 Extras/wallpaper
checks and 34 synthetic native-profile checks pass. The installed-APK binding
probe also validates the full native storage contract before hook installation.
