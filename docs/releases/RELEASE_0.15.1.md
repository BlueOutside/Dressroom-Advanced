# 0.15.1 alpha: 8.5 native control rows

On the P613, the clock's Advanced tab and sub-tabs appeared but its controls did not. The tab ABI resolved correctly; control inflation still used the reference build's data-binding row tags. Dressroom 2.9.60.50/.51 uses `binding_8` for the label and `binding_4` for the SESL seek bar. The reference's `binding_10` does not exist in that layout, and `binding_5` is a container.

Native row roles now live in EditorBinding alongside the other editor differences. The existing panel uses those roles for tabs, labels, switches and sliders. Label creation is inside the guarded refresh path, with a visible error fallback if inflation fails.

Validation: signed build succeeds and verifies with v3 signing. The tablet APK's compiled layout (`res/ye.xml`, resolved by resource name) confirms all four 8.5 row classes/tags. Installed successfully on the P613; Package Manager reports version 0.15.1-alpha / code 108. Dressroom process reload and visual verification remain pending.

The 8.5 bottom-bar Advanced button remains unported. The user confirmed native horizontal/vertical widget rotation works on the tablet; the widget port must preserve that control and avoid adding a duplicate when native rotation is available. Foldable behavior needs its own capability/device check rather than being inferred from the P613 result.
