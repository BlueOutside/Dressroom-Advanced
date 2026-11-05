package dev.denver.dressroomadvanced;

public final class ReflectContractTest {
    static class Parent {
        private final String marker = "inherited";
        public String inherited() { return marker; }
    }
    static final class Model extends Parent {
        boolean adaptive;
        int color;
        float scale;
        public void setAdaptiveColor(boolean value) { adaptive = value; }
        public void setAdaptiveColor(int value) { color = value; }
        public void setScale(float value) { scale = value; }
        public String ambiguous(String value) { return value; }
        public String ambiguous(Integer value) { return String.valueOf(value); }
        public void fail() { throw new IllegalStateException("native failure"); }
    }
    public static void main(String[] args) throws Exception {
        Model model = new Model();
        Reflect.call(model, "setAdaptiveColor", true);
        Reflect.call(model, "setAdaptiveColor", 0xff123456);
        Reflect.call(model, "setScale", 1.25f);
        require(model.adaptive && model.color == 0xff123456 && model.scale == 1.25f, "primitive overload selection");
        require("inherited".equals(Reflect.field(model, "marker")), "inherited private field");
        require("inherited".equals(Reflect.call(model, "inherited")), "inherited method");
        try { Reflect.call(model, "ambiguous", (Object) null); throw new AssertionError("ambiguity accepted"); }
        catch (NoSuchMethodException expected) { }
        try { Reflect.call(model, "fail"); throw new AssertionError("target failure swallowed"); }
        catch (ReflectiveOperationException expected) {
            require(expected.getCause() instanceof IllegalStateException, "target cause preserved");
        }
        try { Reflect.call(model, "setScale", 1); throw new AssertionError("wrong scalar type accepted"); }
        catch (NoSuchMethodException expected) { }
        System.out.println("Reflection contract: 6 checks passed");
    }
    static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
