package dev.denver.dressroomadvanced;

import android.view.View;

import java.util.List;

final class GlyphBlurReadiness {
    static boolean stale(View view, GlyphOutlineBinding glyph) throws IllegalAccessException {
        return glyph.stale(view);
    }

    static boolean needsDraw(List<?> views, GlyphOutlineBinding glyph)
            throws IllegalAccessException {
        for (Object item : views)
            if (item instanceof View view && view.isShown() && stale(view, glyph)) return true;
        return false;
    }
}
