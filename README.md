![Dressroom Advanced](docs/assets/banner.png)

# Dressroom Advanced

An LSPosed (API 102) module that adds clock, widget and Now Bar controls to Samsung's lockscreen editor, with live previews inside Dressroom.

## Features

- Clock size, position, manual stretching, shadows and blur.
- Import TTF, OTF and TTC fonts. Adjust the axes the font provides, including weight, width, optical size, italic and custom axes.
- Hide the date while keeping weather visible.
- Wallpaper depth with Samsung Object Capture, automatic subject detection and crop/zoom tracking.
- Widget scaling, custom grids, free placement and independent depth controls.
- Separate portrait and landscape layouts on supported large screens.
- Keep the charging Now Bar card visible, add a custom text card, and show up to eight cards in the stack.
- Fullscreen album artwork in the expanded media player.

Imported fonts use their own variable axes. Samsung's dynamic per-digit stretching remains limited to its supported native fonts.

## Support

| One UI | Apps | Status |
| --- | --- | --- |
| 9.0 | AOD + Dressroom | Tested on SM-S948B |
| 8.5 | Clock Style + Dressroom | Tested on SM-P613 with a custom ROM |
| 8.5 | AOD + Dressroom | Experimental adapter; not tested on an AOD device |
| 9.0 | Clock Style + Dressroom | Unsupported |
| 8.0 | Any combination | Unsupported |

Foldable support is enabled through Samsung hardware capabilities on One UI 9, without a model whitelist. Individual features still require compatible Dressroom/provider interfaces. Flip cover UI and other firmware combinations need live validation.
Features depend on the device and app versions.

The shared One UI 9 foldable path includes mappings for Dressroom 3.0.01.20 and AOD 8.9.57.1 alongside the existing adapters. Clock saving, lockscreen depth, independent cover/inner widget selections and independent Extras have been confirmed on SM-F971B. See [One UI 9 foldable support](docs/compatibility/ONE_UI_9_FOLDABLES.md) for eligibility and interface checks, and the [device validation](docs/compatibility/FOLDABLE_PARITY.md) for the live results and limits. Existing non-fold adapters remain present.

## Setup

1. Install the module and enable it in LSPosed/Vector.
2. Select Dressroom, SystemUI, and **AOD** or **Clock Style**, depending on your device (so basically all scopes).
3. Restart all hooked processes and open the lockscreen editor.
4. Tap the clock for **Advanced**, or use the bottom-bar **Advanced** button for **Widgets** and **Extras**.
5. Tap Apply/Done to save. Closing without applying discards the editor draft.

For a font file, open **Clock → Advanced → Layout → Typography → Choose font file**. The module keeps a private copy, so the lockscreen does not need access to your Downloads folder.

## Known issues

- Some editor panels still need spacing and layout fixes.
- Media transitions and the fullscreen song-change effect still need visual polish.
- Subject detection can miss fine details (blame the libraries Samsung uses for object detection). Animated, video and gyro wallpapers are not supported for depth.
- Device-wide blur forcing is not included.
- Other devices and Samsung app versions may behave differently from the tested setups.
- The preview in the menu you can access from settings is incorrect. (The actual editor is fine)
- Some details aren't polished (But is conform with One UI's attention to details and quality <3)

## Building and contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions and [docs/](docs/README.md) for the source layout and compatibility notes.

## License

You can use, study, modify and share the project for free. Keep credit to **BlueOutside / Dressroom Advanced**, share the source of distributed modifications, and do not sell or paywall the code, module or derivatives. Do not pass someone else's work off as your own.

See [LICENSE](LICENSE) for the full terms.
