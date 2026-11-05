package dev.denver.dressroomadvanced;

public final class LookupAllocationProbe {
    static volatile Object sink;

    public static final class Model {
        public String value() {
            return "value";
        }
    }

    public static void main(String[] args) throws Exception {
        Model model = new Model();
        Object[] empty = new Object[0];
        for (int i = 0; i < 10000; i++) sink = Reflect.call(model, "value", empty);
        long before = Long.parseLong(android.os.Debug.getRuntimeStat("art.gc.bytes-allocated"));
        long start = System.nanoTime();
        for (int i = 0; i < 20000; i++) sink = Reflect.call(model, "value", empty);
        long time = System.nanoTime() - start;
        long allocated =
                Long.parseLong(android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")) - before;
        System.out.println(
                "20000 warm zero-argument lookups: "
                        + time / 1e6
                        + " ms, Java allocation "
                        + allocated
                        + " bytes");
    }
}
