package dev.denver.dressroomadvanced;

import android.view.View;
import java.util.List;

/** Samsung resets R on text changes, but font/size changes can leave a stale U marked ready. */
final class GlyphBlurReadiness {
    static boolean stale(View view,Class<?> glyphType) throws ReflectiveOperationException {
        return glyphType.isInstance(view) && Boolean.TRUE.equals(Reflect.field(view,"S"))
            && (view.isLayoutRequested() || view.isDirty() || !Boolean.TRUE.equals(Reflect.field(view,"R")));
    }
    static boolean needsDraw(List<?> views,Class<?> glyphType) throws ReflectiveOperationException {
        for(Object item:views)if(item instanceof View view && view.isShown() && stale(view,glyphType))return true;
        return false;
    }
}
