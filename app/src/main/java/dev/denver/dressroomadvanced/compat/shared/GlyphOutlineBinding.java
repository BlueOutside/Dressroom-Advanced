package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolve the cached-path flags from their native readers, not their field names. */
final class GlyphOutlineBinding {
    private final Class<?> glyph;
    private final Field pathMode, drawReady;

    static GlyphOutlineBinding resolve(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        Class<?> base = Class.forName(
                "com.samsung.android.uniform.widget.NonePaddingTextView", false, loader);
        Class<?> clock = Class.forName(
                "com.samsung.android.uniform.widget.ClockNonePaddingTextView", false, loader);
        String owner = DexSignatures.descriptor(base);
        java.lang.reflect.Method color = base.getMethod("setTextColor", int.class);
        // Older AOD versions inherit TextView's setter and calculate outlines
        // live. They still have a first-draw gate, but no cached-path mode flag.
        DexSignatures.Member mode = color.getDeclaringClass() == base
                ? DexSignatures.body(color).uniqueRead(owner, "Z", null) : null;
        Set<DexSignatures.Member> ready = booleanReads(
                DexSignatures.body(clock.getDeclaredMethod("getPathForBlur")), owner);
        if (mode != null && !ready.contains(mode))
            throw new NoSuchFieldException("Blur path getter does not read the path-mode flag");
        // onDraw reads drawing modes, then writes readiness when drawing finishes.
        // Its mode reads must not be mistaken for the getter's ready gate.
        ready.removeAll(booleanReads(
                DexSignatures.body(base.getDeclaredMethod("onDraw", Canvas.class)), owner));
        ready.remove(mode);
        if (ready.size() != 1)
            throw new NoSuchFieldException("Ambiguous glyph draw-ready flag: " + ready.size());
        return new GlyphOutlineBinding(
                base, mode == null ? null : mode.field(loader),
                ready.iterator().next().field(loader));
    }

    GlyphOutlineBinding(Class<?> glyph, Field pathMode, Field drawReady) {
        if ((pathMode != null && (pathMode.getType() != boolean.class || pathMode.equals(drawReady)))
                || drawReady.getType() != boolean.class)
            throw new IllegalArgumentException("Glyph readiness needs two distinct boolean fields");
        this.glyph = glyph;
        this.pathMode = pathMode;
        this.drawReady = drawReady;
        if (pathMode != null) pathMode.setAccessible(true);
        drawReady.setAccessible(true);
    }

    boolean stale(View view) throws IllegalAccessException {
        // Even the uncached getter needs offsets from onDraw. Only its outline
        // can be calculated live; cached paths also need a draw after invalidation.
        return glyph.isInstance(view)
                && (!drawReady.getBoolean(view) || view.isLayoutRequested()
                        || (pathMode != null && pathMode.getBoolean(view) && view.isDirty()));
    }

    private static Set<DexSignatures.Member> booleanReads(DexSignatures.Body body, String owner) {
        Set<DexSignatures.Member> fields = new LinkedHashSet<>();
        for (DexSignatures.Read read : body.reads())
            if (read.field().owner().equals(owner) && read.field().type().equals("Z"))
                fields.add(read.field());
        return fields;
    }
}
