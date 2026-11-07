# Current validation

## Reference devices

| Device | Software | Status |
| --- | --- | --- |
| SM-S948B | One UI 9.0, Dressroom 3.0.10.38, AOD 8.9.66.1 | Main development device |
| SM-P613 | One UI 8.5 custom ROM, Dressroom 2.9.60.50, Clock Style 8.9.35.60 | Tablet development device |
| No device yet | One UI 8.5, AOD 8.9.32.95 | APK bindings checked; runtime untested |

## Recent changes

- **0.21.4:** the user confirmed the original Now Bar overlap and disappearing-card bugs were fixed on the phone.
- **0.22.1:** a targeted phone swipe completed with four measured cards present and no rear-media parent exception. Extended-stack animation smoothness still needs user confirmation.
- **0.23.0:** the Clock Style stack backport is installed on the tablet. Runtime hooks loaded; visible counts, swipes and rotation still need user confirmation.
- **0.23.1:** 8.5 AOD stack bindings pass declaration checks against the supplied APK. Text encoding and panel copy were cleaned up, and the build is installed on the tablet.

## Publication checks

The signed 0.23.1 build and 152 JVM checks pass locally. Repository contents, local documentation links and the generated reflection inventory are checked before publishing. GitHub Actions builds an unsigned APK and runs the JVM checks; it does not test Samsung rendering or reload any device.

See [the stack investigation](research/NOW_BAR_STACK.md) for binding details and [the previous validation record](history/VALIDATION-through-0.23.1.md) for older results.
