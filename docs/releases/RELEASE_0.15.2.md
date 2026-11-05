# 0.15.2 alpha: native weather eligibility

After the native row correction, the P613 rendered controls but refresh stopped at `ClockTypeData.isSupportWeatherInfo`, leaving queued-edit controls inactive. The 8.5 model has no such property or renamed replacement. Its editor queries the native clock manager for weather support instead.

EditorBinding now resolves that capability operation explicitly: the reference uses the model property; 8.5 obtains its ClockManager from the native wrapper and queries `isSupportWeatherInfo(Category, int)`, respecting ClockData's enable state. The named ClockManager API was inspected through JADX against the tablet's Clock Style APK. A failure during the weather-capability query now reports that weather controls are unavailable and allows the remaining panel setup to finish. Refresh also drops the detached status view before rebuilding.

The local APK comparison checked getters/setters on ClockData, ClockTypeData, ClockFontData, ClockStretchData and ClockColorData. The additional removed stretch accessors are not used by the panel. Constructors/copy layouts differ and are not translated by positional assumptions; native deepCopy remains responsible for the model copy.

Validation: signed APK builds and verifies; nine JVM suites pass (117 checks). The updated Android binding checker passes against the tablet's installed Dressroom .50 and Clock Style APKs, including resolution of the new native weather operation. Installed successfully on the P613. On 2026-10-30 the user confirmed the Advanced controls work after restarting Dressroom, saving applies the tested settings to the lockscreen, and reopening Dressroom restores them.

This is not an AOD-phone validation. Clock Style 8.9.35.60 and AOD 8.9.32.95 retain separate render/shadow/normalizer mappings; .15.2 changes the Dressroom editor contract, not a shared claim that both providers have been tested. Depth/blur/widget/media ports remain unfinished.
