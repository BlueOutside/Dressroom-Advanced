package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;

import java.lang.reflect.Method;

public final class RenderHotPathProbe {
    static int checks;
    static volatile Object sink;

    static class Parent {
        private int value = 4;

        public String inherited() {
            return "parent";
        }
    }

    static final class Model extends Parent {
        public String choose(String s) {
            return "string";
        }

        public String choose(Integer i) {
            return "integer";
        }

        public String nullable(String s) {
            return s == null ? "null" : s;
        }

        public int scalar(int value) {
            return value + 1;
        }
    }

    static void check(boolean value) {
        if (!value) throw new AssertionError("Check " + checks);
        checks++;
    }

    static Path rectangle(float left, float top, float right, float bottom) {
        Path p = new Path();
        p.addRect(left, top, right, bottom, Path.Direction.CW);
        return p;
    }

    static Region raster(Path path) {
        Region r = new Region();
        r.setPath(path, new Region(-100, -100, 400, 400));
        return r;
    }

    public static void main(String[] args) throws Exception {
        Model first = new Model(), second = new Model();
        for (int i = 0; i < 2; i++) {
            check("string".equals(Reflect.call(first, "choose", "x")));
            check("integer".equals(Reflect.call(first, "choose", 1)));
            check("null".equals(Reflect.call(first, "nullable", (Object) null)));
            check((Integer) Reflect.call(first, "scalar", 4) == 5);
            check("parent".equals(Reflect.call(first, "inherited")));
            try {
                Reflect.call(first, "choose", (Object) null);
                throw new AssertionError("ambiguous null cached");
            } catch (NoSuchMethodException expected) {
                checks++;
            }
            try {
                Reflect.call(first, "scalar", (Object) null);
                throw new AssertionError("primitive null accepted");
            } catch (NoSuchMethodException expected) {
                checks++;
            }
        }
        Reflect.set(first, "value", 9);
        check(
                (Integer) Reflect.field(first, "value") == 9
                        && (Integer) Reflect.field(second, "value") == 4);
        Path round = new Path();
        round.addRoundRect(new RectF(0, 0, 100, 100), 17, 17, Path.Direction.CW);
        Path outside = rectangle(150, 0, 200, 100);
        Path visible = DepthGeometry.subtract(round, outside);
        check(java.util.Arrays.equals(round.approximate(.01f), visible.approximate(.01f)));
        visible.reset();
        check(!DepthGeometry.subtract(round, outside).isEmpty());
        check(raster(DepthGeometry.subtract(round, new Path())).equals(raster(round)));
        check(DepthGeometry.subtract(new Path(), round).isEmpty());
        Path inverse = rectangle(150, 0, 200, 100);
        inverse.toggleInverseFillType();
        check(raster(DepthGeometry.subtract(round, inverse)).isEmpty());
        Path stem = rectangle(49, 0, 51, 100);
        Region cut = raster(DepthGeometry.subtract(round, stem));
        check(!cut.contains(50, 50) && cut.contains(40, 50) && cut.contains(60, 50));
        Path holes = rectangle(20, 20, 80, 80);
        holes.addRect(40, 40, 60, 60, Path.Direction.CCW);
        cut = raster(DepthGeometry.subtract(round, holes));
        check(cut.contains(50, 50) && !cut.contains(30, 30));
        for (int i = 0; i < 1000; i++) {
            sink = Reflect.call(first, "inherited");
            sink = legacy(first, "inherited");
        }
        long start = System.nanoTime();
        for (int i = 0; i < 5000; i++) sink = legacy(first, "inherited");
        long oldTime = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 5000; i++) sink = Reflect.call(first, "inherited");
        long newTime = System.nanoTime() - start;
        System.out.println(
                "Inherited lookup 5000 calls: uncached="
                        + oldTime / 1e6
                        + "ms cached="
                        + newTime / 1e6
                        + "ms");
        System.out.println("Render hot path: " + checks + " checks passed");
    }

    static Object legacy(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == 0 && !m.isBridge()) {
                    m.setAccessible(true);
                    return m.invoke(target);
                }
        throw new NoSuchMethodException(name);
    }
}
