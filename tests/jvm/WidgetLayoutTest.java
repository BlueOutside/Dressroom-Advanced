package dev.denver.dressroomadvanced;

public final class WidgetLayoutTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[][] items = {{1, 1}, {1, 1}, {2, 2}};
        check(WidgetLayout.fits(4, 2, items), "A supported 2-row widget should fit");
        check(!WidgetLayout.fits(4, 1, items), "Shrinking must reject a 2-row widget");
        check(!WidgetLayout.fits(1, 4, items), "Vertical grid cannot hold a 2-column widget");
        check(
                WidgetLayout.fits(1, 4, new int[][] {{1, 1}, {1, 1}, {1, 2}}),
                "Vertical packing failed");
        check(WidgetLayout.fits(4, 1, new int[0][2]), "Empty grid failed");
        check(!WidgetLayout.fits(8, 8, new int[0][2]), "Oversized grid accepted");
        check(!WidgetLayout.fits(4, 2, new int[][] {{0, 1}}), "Malformed provider span accepted");
        check(items[0][0] == 1 && items[2][0] == 2, "Fit check changed provider ordering");
        check(
                WidgetLayout.positionsValid(
                        4, 2, new int[][] {{0, 0, 2, 1}, {2, 0, 2, 2}, {0, 1, 2, 1}}),
                "Valid mixed-size grid was marked for repair");
        check(
                !WidgetLayout.positionsValid(4, 2, new int[][] {{0, 0, 2, 1}, {0, 0, 2, 2}}),
                "Overlapping widgets were accepted");
        check(
                !WidgetLayout.positionsValid(2, 4, new int[][] {{0, 0, 2, 2}, {1, 2, 2, 1}}),
                "A horizontal coordinate survived outside the vertical grid");
        check(
                WidgetLayout.positionsValid(
                        2, 4, new int[][] {{0, 0, 2, 2}, {0, 2, 2, 1}, {0, 3, 2, 1}}),
                "Valid vertical secondary positions were rejected");
        check(
                !WidgetLayout.positionsValid(4, 2, new int[][] {{-1, 0, 1, 1}}),
                "An unplaced widget was accepted");
        check(
                !WidgetLayout.positionsValid(4, 2, new int[][] {{Integer.MAX_VALUE, 0, 2, 1}}),
                "Overflowed coordinates bypassed grid bounds");
        int[] projected = WidgetLayout.dimensions(4, 2, false, true);
        check(
                projected[0] == 2 && projected[1] == 4,
                "Native vertical layout did not transpose capacity");
        int[] unchanged = WidgetLayout.dimensions(2, 4, true, true);
        check(unchanged[0] == 2 && unchanged[1] == 4, "Native vertical capacity transposed twice");
        check(
                !WidgetLayout.matchesOrientation(4, 2, true),
                "Vertical control disagrees with Samsung position type");
        check(
                WidgetLayout.matchesOrientation(2, 4, true),
                "Tall grid should use secondary native positions");
        check(
                WidgetLayout.matchesOrientation(2, 2, false)
                        && !WidgetLayout.matchesOrientation(2, 2, true),
                "Square grids must use native Default positions");
        float[] below =
                WidgetLayout.place(1, 200, 300, 600, 500, 200, 100, 1000, 2000, 10, .5f, .5f);
        check(below[0] == 300 && below[1] == 510, "Below docking failed");
        float[] above =
                WidgetLayout.place(2, 200, 300, 600, 500, 200, 100, 1000, 2000, 10, .5f, .5f);
        check(above[0] == 300 && above[1] == 190, "Above docking failed");
        float[] left =
                WidgetLayout.place(3, 200, 300, 600, 500, 100, 100, 1000, 2000, 10, .5f, .5f);
        check(left[0] == 90 && left[1] == 350, "Left docking failed");
        float[] right =
                WidgetLayout.place(4, 200, 300, 600, 500, 100, 100, 1000, 2000, 10, .5f, .5f);
        check(right[0] == 610 && right[1] == 350, "Right docking failed");
        float[] slidRight =
                WidgetLayout.place(4, 200, 300, 600, 500, 100, 100, 1000, 2000, 10, .5f, .5f, 70);
        check(
                slidRight[0] == 610 && slidRight[1] == 420,
                "A snapped right grid must slide vertically without detaching");
        float[] slidAbove =
                WidgetLayout.place(2, 200, 300, 600, 500, 100, 100, 1000, 2000, 10, .5f, .5f, -80);
        check(
                slidAbove[0] == 270 && slidAbove[1] == 190,
                "An above-clock grid must slide horizontally without changing its gap");
        float[] extreme =
                WidgetLayout.place(3, 200, 300, 600, 500, 100, 100, 1000, 2000, 10, .5f, .5f, 5000);
        check(
                extreme[0] == 90 && extreme[1] == 1900,
                "Sliding a snapped grid escaped screen bounds");
        float[] zero = WidgetLayout.place(5, 200, 300, 600, 500, 200, 100, 1000, 2000, 10, 0, 0);
        check(zero[0] == 0 && zero[1] == 0, "Free origin was treated as a sentinel");
        float[] edge = WidgetLayout.place(5, 200, 300, 600, 500, 200, 100, 1000, 2000, 10, 1, 1);
        check(edge[0] == 800 && edge[1] == 1900, "Free edge escapes screen");
        float[] oversized = WidgetLayout.place(5, 0, 0, 0, 0, 2000, 3000, 1000, 2000, 0, 1, 1);
        check(oversized[0] == 0 && oversized[1] == 0, "Oversized grid produced negative position");
        check(WidgetLayout.finite(Float.NaN, 1, .5f, 2) == 1, "NaN reached native metrics");
        check(WidgetLayout.effectiveAnchor(0, true, true, true) == 4, "Fold8 default vertical must dock right");
        check(WidgetLayout.effectiveAnchor(0, true, false, true) == 0, "Horizontal native placement changed");
        check(WidgetLayout.effectiveAnchor(5, true, true, true) == 5, "Free placement changed");
        check(WidgetLayout.effectiveAnchor(0, true, true, false) == 0, "Existing providers changed");
        check(WidgetLayout.effectiveAnchor(0, false, true, true) == 0, "Stock non-custom layout changed");
        verifyMod5WidgetContracts();
        verifyWidgetWiper();
        System.out.println("Widget layout contracts: " + checks + " checks passed");
    }

    private enum AlignStub {
        LEFT, RIGHT
    }

    @SuppressWarnings("unused")
    private static final class PanelUiStub {
        static void f(
                Object a,
                Object b,
                Object c,
                Object d,
                Object e,
                int i,
                AlignStub g,
                android.graphics.Rect r,
                Object fn,
                Object j) {}

        void k(
                Object a,
                Object b,
                Object c,
                Object d,
                Object e,
                int i,
                AlignStub g,
                android.graphics.Rect r,
                boolean show,
                Object j) {}
    }

    @SuppressWarnings("unused")
    private static final class RefreshPosStub {
        boolean ran;

        RefreshPosStub(
                Object a1,
                Object a2,
                Object a3,
                Object a4,
                Object a5,
                Object a6,
                Object a7,
                Object a8,
                Object a9,
                Object a10,
                Object a11,
                Object a12,
                Object a13,
                Object a14) {}

        void x() {
            ran = true;
        }
    }

    @SuppressWarnings("unused")
    private static final class AlignUpdaterStub {
        AlignStub applied;

        boolean q(AlignStub align) {
            applied = align;
            return true;
        }
    }

    @SuppressWarnings("unused")
    private static final class PreviewStub {
        final PanelUiStub obfuscatedUi = new PanelUiStub();
        final RefreshPosStub obfuscatedRefresh =
                new RefreshPosStub(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null);
        final AlignUpdaterStub obfuscatedAlign = new AlignUpdaterStub();
    }

    private static void verifyMod5WidgetContracts() {
        try {
            Class<?> syncMapClass =
                    java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>())
                            .getClass();
            Class<?> syncSetClass =
                    java.util.Collections.synchronizedSet(
                                    java.util.Collections.newSetFromMap(
                                            new java.util.WeakHashMap<>()))
                            .getClass();
            for (String mapField :
                    new String[] {
                        "roots", "geometry", "originals", "windowOwners", "blurDisabled"
                    }) {
                java.lang.reflect.Field f = WidgetDepthBlur.class.getDeclaredField(mapField);
                f.setAccessible(true);
                check(
                        f.get(null).getClass() == syncMapClass,
                        "WidgetDepthBlur." + mapField + " must stay synchronized");
            }
            for (String setField :
                    new String[] {"verified", "hidden", "previewLayout", "deoptimized"}) {
                java.lang.reflect.Field f = WidgetDepthBlur.class.getDeclaredField(setField);
                f.setAccessible(true);
                check(
                        f.get(null).getClass() == syncSetClass,
                        "WidgetDepthBlur." + setField + " must stay synchronized");
            }

            PreviewStub preview = new PreviewStub();
            check(
                    WidgetEditorBinding.findUiController(preview, "missing")
                            == preview.obfuscatedUi,
                    "Structural UI controller lookup must ignore renamed field name");

            java.lang.reflect.Method findShow =
                    WidgetPanel.class.getDeclaredMethod("findShowMethod", Class.class);
            findShow.setAccessible(true);
            java.lang.reflect.Method show =
                    (java.lang.reflect.Method) findShow.invoke(null, PanelUiStub.class);
            check(
                    "k".equals(show.getName())
                            && !java.lang.reflect.Modifier.isStatic(show.getModifiers()),
                    "WidgetPanel.findShowMethod must skip static 10-arg methods");

            OneUi9WidgetEditor.recalculate(preview);
            check(
                    preview.obfuscatedRefresh.ran,
                    "OneUi9WidgetEditor.recalculate must resolve structural refresh use case");
            check(
                    OneUi9WidgetEditor.applyAlign(preview, AlignStub.RIGHT)
                            && preview.obfuscatedAlign.applied == AlignStub.RIGHT,
                    "OneUi9WidgetEditor.applyAlign must resolve structural align updater");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Mod #5 contract failure", error);
        }
    }

    @SuppressWarnings("unused")
    private static final class WidgetGridDataStub {
        int col = 6;
        int row = 3;
        boolean gridType = true;

        void setCol(int c) { col = c; }
        void setRow(int r) { row = r; }
        void setGridType(boolean t) { gridType = t; }
    }

    private enum AlignModeStub {
        Default, Vertical
    }

    @SuppressWarnings("unused")
    private static final class WidgetDataStub {
        boolean enable = true;
        String widgetList = "12;com.example-34;";
        boolean editableAlign = true;
        boolean widgetAlignChanging = true;
        AlignModeStub align = AlignModeStub.Vertical;
        int x = 50, y = 80, width = 400, height = 200;
        final WidgetGridDataStub grid = new WidgetGridDataStub();

        void setEnable(boolean v) { enable = v; }
        void setWidgetList(String s) { widgetList = s; }
        void setEditableAlign(boolean v) { editableAlign = v; }
        void setWidgetAlignChanging(boolean v) { widgetAlignChanging = v; }
        AlignModeStub getWidgetAlignType() { return align; }
        void setWidgetAlignType(AlignModeStub v) { align = v; }
        WidgetGridDataStub getWidgetGridData() { return grid; }
        void setX(int v) { x = v; }
        void setY(int v) { y = v; }
        void setWidth(int v) { width = v; }
        void setHeight(int v) { height = v; }
    }

    @SuppressWarnings("unused")
    private static final class ProfileStub {}

    @SuppressWarnings("unused")
    private static final class Store9Stub {
        private void hidden(WidgetDataStub d, ProfileStub p) {}
        public static void s(WidgetDataStub d, ProfileStub p) {}
        public int other(WidgetDataStub d, ProfileStub p) { return 0; }
        public void a(WidgetDataStub d, ProfileStub p) {}
    }

    @SuppressWarnings("unused")
    private static final class Store85Stub {
        public void b(WidgetDataStub d, ProfileStub p, android.os.Bundle extras) {}
        public void c() {}
        public boolean helper() { return false; }
    }

    @SuppressWarnings("unused")
    private static final class StoreAmbiguousStub {
        public void a(WidgetDataStub d, ProfileStub p) {}
        public void b(WidgetDataStub d, ProfileStub p, android.os.Bundle extras) {}
    }

    private static void verifyWidgetWiper() {
        check(WidgetWiper.extractWidgetIds(null).isEmpty(), "null widget list must yield empty ids");
        check(WidgetWiper.extractWidgetIds("").isEmpty(), "empty widget list must yield empty ids");
        check(WidgetWiper.extractWidgetIds("-5, 0, abc").isEmpty(), "non-positive tokens must be ignored");
        check(
                java.util.List.of(12, 34).equals(WidgetWiper.extractWidgetIds("12-34")),
                "Dash-delimited widget IDs must extract cleanly");
        check(
                java.util.List.of(7, 19).equals(WidgetWiper.extractWidgetIds("[7, -5, 19]")),
                "Mixed negative and positive bracket tokens must ignore negative IDs");
        check(
                java.util.List.of(12, 34)
                        .equals(
                                WidgetWiper.extractWidgetIds(
                                        "12;com.sec.android.daemonapp.widget2x1-34;com.example.v2.ClockWidget4;")),
                "Samsung widget list must extract only prefix widget IDs");
        check(
                java.util.List.of(7, 19, 42)
                        .equals(WidgetWiper.extractWidgetIds("[7, 19, 7, 42]")),
                "CSV enabled widget list must preserve unique ID order");
        try {
            WidgetDataStub model = new WidgetDataStub();
            WidgetWiper.clearWidgetModel(model, WidgetLayoutTest.class.getClassLoader(), null);
            check(
                    !model.enable
                            && "".equals(model.widgetList)
                            && !model.editableAlign
                            && !model.widgetAlignChanging
                            && model.align == AlignModeStub.Default
                            && model.grid.col == 4
                            && model.grid.row == 1
                            && !model.grid.gridType
                            && model.x == 0
                            && model.y == 0
                            && model.width == 0
                            && model.height == 0,
                    "clearWidgetModel must reset widget model and 4x1 grid defaults");

            check(
                    "a".equals(
                            WidgetWiper.resolveStoreSaveMethod(
                                            Store9Stub.class, WidgetDataStub.class, ProfileStub.class)
                                    .getName()),
                    "One UI 9 two-arg save method must resolve uniquely");
            check(
                    "b".equals(
                            WidgetWiper.resolveStoreSaveMethod(
                                            Store85Stub.class, WidgetDataStub.class, ProfileStub.class)
                                    .getName()),
                    "One UI 8.5 three-arg Bundle save method must resolve uniquely");
            check(
                    "c".equals(
                            WidgetWiper.resolveStoreNotifyOnPauseMethod(Store85Stub.class)
                                    .getName()),
                    "notifyOnPause single-zero-void fallback must resolve");
            check(
                    WidgetWiper.resolveFacadeGetter(Store9Stub.class) == null,
                    "resolveFacadeGetter must return null when absent");

            boolean threwAmbiguous = false;
            try {
                WidgetWiper.resolveStoreSaveMethod(
                        StoreAmbiguousStub.class, WidgetDataStub.class, ProfileStub.class);
            } catch (NoSuchMethodException expected) {
                threwAmbiguous = true;
            }
            check(threwAmbiguous, "Ambiguous store save methods must fail closed");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("WidgetWiper contract failure", error);
        }
    }
}
