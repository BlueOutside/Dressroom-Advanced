package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class OneUi95MediaCollapse {
    private static final String PROVIDER = "Ljavax/inject/Provider;";
    private static final Set<String> OBSERVER_LOG =
            Set.of("mediaExpandObserver ", "MediaExpandStateViewModel");

    static Class<?> viewModel(ClassLoader loader) throws ReflectiveOperationException, IOException {
        Set<String> observerOwners = new LinkedHashSet<>();
        for (var body : DexSignatures.find(loader, OBSERVER_LOG)) {
            if (!body.anchors().keySet().containsAll(OBSERVER_LOG)) continue;
            for (var write : body.writes()) {
                var member = write.field();
                if (member.type().equals("Z") && !member.owner().equals(body.owner()))
                    observerOwners.add(member.owner());
            }
        }
        var constructor = OneUi95Editor.unique(
                DexSignatures.find(loader, Set.of("getNowBarMediaExpandStateUseCaseProvider"))
                        .stream().filter(body -> body.name().equals("<init>")
                                && observerOwners.contains(body.owner()))
                        .toList(), "media expand state constructor");
        return constructor.loadOwner(loader);
    }

    static Field mediaProvider(Class<?> card, Class<?> model)
            throws ReflectiveOperationException, IOException {
        String cardType = DexSignatures.descriptor(card);
        String modelType = DexSignatures.descriptor(model);
        var body = OneUi95Editor.unique(DexSignatures.find(card.getClassLoader(),
                Set.of("hideMediaOutputView: MediaOutput already hide")).stream()
                .filter(method -> method.owner().equals(cardType)
                        && method.parameters().equals(List.of("Z")) && method.result().equals("V")
                        && method.reads().stream().anyMatch(read -> read.field().owner().equals(modelType)))
                .toList(), "hide media output state");
        return body.uniqueRead(cardType, PROVIDER, null).field(card.getClassLoader());
    }
}
