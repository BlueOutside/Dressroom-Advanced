package dev.denver.dressroomadvanced;

final class OneUi9Editor {
    static final String SAVE_CONTROLLER = "dc.p";
    static final String BLUR_HELPER = "ah.g";

    private OneUi9Editor() {}

    static EditorBinding.Names names() {
        return new EditorBinding.Names(
                false,
                "vo.x",
                "mf.o0",
                "oc.a",
                "Y",
                "b",
                "w",
                "a",
                "d",
                "e",
                "e",
                "mf.i0",
                null,
                null,
                null);
    }

    static Class<?> saveController(ClassLoader loader) throws ClassNotFoundException {
        Class<?> baseActivity =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        Class<?> clockManager =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockManager", false, loader);
        return NativeContract.findClass(
                loader,
                cls -> NativeContract.hasConstructor(cls, null, baseActivity, clockManager),
                SAVE_CONTROLLER);
    }

    static Class<?> blurHelper(ClassLoader loader, Class<?> clockView)
            throws ClassNotFoundException {
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasMethod(cls, void.class, clockView, boolean.class)
                                && NativeContract.hasMethod(cls, void.class, clockView),
                BLUR_HELPER);
    }
}
