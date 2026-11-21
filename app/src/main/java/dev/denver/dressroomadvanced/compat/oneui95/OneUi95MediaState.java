package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

final class OneUi95MediaState {
    static final String PAGE_LOG = "onMediaExpandStateChanged() mIsMediaExpanded: ";
    private static final String MEDIA_BLUR_LOG = "animateBlur isExpand=";

    record Observer(Method changed, Field expanded, Field status) {}

    static Observer resolve(ClassLoader loader) throws ReflectiveOperationException, IOException {
        Class<?> type = Class.forName(
                "com.samsung.android.app.facewidget.presentation.MediaExpandStateObserver",
                false, loader);
        Method changed = NativeContract.method(type, "onChanged", void.class, Object.class);
        var body = DexSignatures.body(changed, Set.of(MEDIA_BLUR_LOG));
        String owner = DexSignatures.descriptor(type);
        var expanded = body.uniqueRead(owner, "Z", MEDIA_BLUR_LOG);
        if (body.writes().stream().noneMatch(write -> write.field().equals(expanded)))
            throw new NoSuchFieldException("Media observer expansion state");
        Field status = body.uniqueRead(owner,
                "Lcom/samsung/android/app/facewidget/SecKeyguardStatusView;", null).field(loader);
        return new Observer(changed, expanded.field(loader), status);
    }

    static Field pageExpanded(DexSignatures.Body body, String page, String clock,
            ClassLoader loader) throws ReflectiveOperationException {
        int log = body.anchors().getOrDefault(PAGE_LOG, -1);
        int setter = body.calls().stream()
                .filter(call -> call.owner().equals(clock) && call.name().equals("setMediaExpanded"))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Page media setter"))
                .offset();
        var field = OneUi95Editor.unique(body.reads().stream()
                .filter(read -> read.offset() > log && read.offset() < setter
                        && read.field().owner().equals(page) && read.field().type().equals("Z"))
                .map(DexSignatures.Read::field).distinct()
                .filter(member -> body.writes().stream().anyMatch(write -> write.field().equals(member)))
                .toList(), "page media expansion state");
        return field.field(loader);
    }
}
