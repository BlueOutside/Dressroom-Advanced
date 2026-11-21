package dev.denver.dressroomadvanced;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Completely clears live, cached, AppWidgetHost, and persisted lockscreen widget state. */
final class WidgetWiper {
    private static final String ALIGN_TYPE =
            "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType";
    private static final String UI_CONTROLLER =
            "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController";
    private static final String APP_WIDGET_FACADE =
            "com.samsung.android.clockpack.plugins.appwidget.IAppWidgetFacade";
    private static final String WIDGET_DATA_CLASS =
            "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData";
    private static final String WIDGET_DATA_DESC = "L" + WIDGET_DATA_CLASS.replace('.', '/') + ";";
    private static final String OBSERVABLE_DATA =
            "com.samsung.android.lockscreen.editor.item.domain.entity.common.ObservableData";
    private static final String MUTABLE_OBSERVABLE_DATA =
            "com.samsung.android.lockscreen.editor.item.domain.entity.common.MutableObservableData";
    private static final String ENABLED_LIST_URI =
            "content://com.samsung.android.app.clockpack.provider/complication_enabled_list";
    private static final String ALL_PROFILES_LIST_URI =
            "content://com.samsung.android.app.clockpack.provider/lock_settings/all_profiles_complication_enabled_list";
    private static final String COL_ENABLED_IDS = "complication_enabled_widget_ids";
    private static final String COL_ALL_PROFILES_IDS =
            "complication_all_profiles_enabled_widget_id_list";

    private record PreviewBindings(
            Field refreshField,
            Method refreshMethod,
            Field enableField,
            Method enableMethod,
            Field updateListStrField,
            Method updateListStrMethod,
            List<Field> lazyFields,
            Method facadeGetter,
            Method facadeDisable,
            List<Field> observableFields) {}

    private record StoreBindings(Method saveMethod, boolean takesBundle, Method notifyOnPause) {}

    private record GridBindings(
            Method removeItem,
            Method initGridLayoutSize,
            Method updateSavedModuleInfo,
            Method forEachWidgetView,
            Field repoField,
            Method getAllGridItems,
            Method repoClear,
            Method removeModule,
            List<Field> repoMapFields,
            Field cacheField,
            Field cacheMapField,
            Field cacheAddedField,
            Field cacheRemovedField,
            Field cacheDisableField) {}

    private static final ClassValue<PreviewBindings> previewBindings =
            new ClassValue<>() {
                @Override
                protected PreviewBindings computeValue(Class<?> previewClass) {
                    return resolvePreviewBindings(previewClass);
                }
            };

    private static final ClassValue<StoreBindings> storeBindings =
            new ClassValue<>() {
                @Override
                protected StoreBindings computeValue(Class<?> storeClass) {
                    try {
                        return resolveStoreBindings(storeClass);
                    } catch (NoSuchMethodException error) {
                        throw new IllegalStateException(
                                "Unsupported widget store repository: " + storeClass.getName(),
                                error);
                    }
                }
            };

    private static final ClassValue<GridBindings> gridBindings =
            new ClassValue<>() {
                @Override
                protected GridBindings computeValue(Class<?> gridClass) {
                    return resolveGridBindings(gridClass);
                }
            };

    private WidgetWiper() {}

    static void wipe(
            Context context,
            Object previewOwner,
            Object gridController,
            Object widgetData,
            Object observableRepo,
            Object storeRepo,
            Object profileData)
            throws Exception {
        // Binding discovery reads DEX bodies; share one decoded copy for the whole wipe so the
        // UI thread does not reopen and rescan the APK for every candidate method.
        DexSignatures.cached(
                () -> {
                    wipeNow(
                            context,
                            previewOwner,
                            gridController,
                            widgetData,
                            observableRepo,
                            storeRepo,
                            profileData);
                    return null;
                });
    }

    private static void wipeNow(
            Context context,
            Object previewOwner,
            Object gridController,
            Object widgetData,
            Object observableRepo,
            Object storeRepo,
            Object profileData)
            throws Exception {
        ClassLoader loader = previewOwner.getClass().getClassLoader();
        Set<Integer> widgetIds = new LinkedHashSet<>();
        Set<String> gridIds = new LinkedHashSet<>();

        collectFromWidgetData(widgetData, widgetIds);
        collectFromProfileAndProvider(context, profileData, widgetIds);

        GridBindings gridMeta =
                gridController == null ? null : gridBindings.get(gridController.getClass());
        if (gridController != null && gridMeta != null) {
            collectFromGrid(gridController, gridMeta, gridIds, widgetIds);
            wipeGridController(gridController, gridMeta, gridIds, widgetIds);
        }

        PreviewBindings previewMeta = previewBindings.get(previewOwner.getClass());
        invokeUseCase(previewOwner, previewMeta.refreshField, previewMeta.refreshMethod);
        invokeUseCase(
                previewOwner, previewMeta.updateListStrField, previewMeta.updateListStrMethod);

        Rect emptyRect = new Rect();
        clearWidgetModel(widgetData, loader, emptyRect);
        pushWidgetDataObservables(previewOwner, previewMeta, observableRepo, widgetData, loader, emptyRect);

        invokeUseCase(previewOwner, previewMeta.enableField, previewMeta.enableMethod, false);
        clearWidgetModel(widgetData, loader, emptyRect);
        pushWidgetDataObservables(previewOwner, previewMeta, observableRepo, widgetData, loader, emptyRect);

        if (gridController != null && gridMeta != null) {
            invokeOptional(gridMeta.initGridLayoutSize, gridController, 4, 1);
            invokeOptional(gridMeta.updateSavedModuleInfo, gridController, new ArrayList<>());
        }

        clearProfileString(profileData);
        persistEmptyStore(storeRepo, widgetData, profileData);
        deleteOrphanedWidgetIds(context, previewOwner, previewMeta, gridController, gridMeta, widgetIds);
    }

    /** Resets persisted Portrait/Landscape/unoriented widget preferences to stock defaults. */
    static Bundle wipeSavedPreferences(Context context, Bundle currentDraft) {
        Bundle clean = WidgetPreferences.clean(WidgetPreferences.defaults());
        if (currentDraft != null) {
            Bundle merged = new Bundle(currentDraft);
            merged.putAll(clean);
            merged.remove("depthMask");
            merged.remove("depthMaskWidth");
            merged.remove("depthMaskHeight");
            merged.remove("depthMaskClockHash");
            merged.remove("depthMaskHash");
            merged.putBoolean("vertical", false);
            clean = merged;
        }
        if (context != null) {
            commitAcrossOrientations(context, clean);
            int mainWallpaper = clean.getInt("wallpaperId", -1);
            int legacyWallpaper = clean.getInt("legacyWallpaperId", -1);
            if (legacyWallpaper >= 0 && legacyWallpaper != mainWallpaper) {
                Bundle legacyCopy = new Bundle(clean);
                legacyCopy.putInt("wallpaperId", legacyWallpaper);
                commitAcrossOrientations(context, legacyCopy);
            }
        }
        return clean;
    }

    private static void commitAcrossOrientations(Context context, Bundle base) {
        for (int orientation : new int[] {1, 2, 0}) {
            Bundle oriented = new Bundle(base);
            oriented.putInt(LayoutOrientation.FIELD, orientation);
            WidgetPreferences.commit(context, oriented);
        }
    }

    /** Extracts positive AppWidget IDs from Samsung widget-list or CSV/bracket ID strings. */
    static List<Integer> extractWidgetIds(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        Set<Integer> ids = new LinkedHashSet<>();
        int start = -1;
        for (int i = 0; i <= raw.length(); i++) {
            char ch = i == raw.length() ? ',' : raw.charAt(i);
            if ((ch == '-' && start >= 0)
                    || ch == ','
                    || ch == '['
                    || ch == ']'
                    || Character.isWhitespace(ch)) {
                if (start >= 0 && i > start) addParsedId(raw.substring(start, i), ids);
                start = -1;
            } else if (start < 0) {
                start = i;
            }
        }
        return new ArrayList<>(ids);
    }

    /** Resets a native {@code WidgetData} (and its nested grid) back to stock empty 4x1 state. */
    static void clearWidgetModel(Object widgetData, ClassLoader loader, Object emptyPositionRect)
            throws ReflectiveOperationException {
        if (widgetData == null) return;
        Reflect.call(widgetData, "setEnable", false);
        Reflect.call(widgetData, "setWidgetList", "");
        Reflect.call(widgetData, "setEditableAlign", false);
        Reflect.call(widgetData, "setWidgetAlignChanging", false);
        resetDefaultAlign(widgetData, loader);
        Object gridData = Reflect.call(widgetData, "getWidgetGridData");
        if (gridData != null) {
            Reflect.call(gridData, "setCol", 4);
            Reflect.call(gridData, "setRow", 1);
            callIfDeclared(gridData, "setGridType", boolean.class, false);
        }
        callIfDeclared(widgetData, "setX", int.class, 0);
        callIfDeclared(widgetData, "setY", int.class, 0);
        callIfDeclared(widgetData, "setWidth", int.class, 0);
        callIfDeclared(widgetData, "setHeight", int.class, 0);
        if (emptyPositionRect != null) {
            callIfDeclared(widgetData, "setWidgetPositionRect", emptyPositionRect.getClass(), emptyPositionRect);
        }
    }

    static Method resolveStoreSaveMethod(
            Class<?> storeClass, Class<?> widgetDataType, Class<?> profileDataType)
            throws NoSuchMethodException {
        Method found = null;
        for (Method m : storeClass.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())
                    || Modifier.isStatic(m.getModifiers())
                    || m.isBridge()
                    || m.isSynthetic()
                    || m.getReturnType() != void.class) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            boolean matchTwo =
                    params.length == 2
                            && params[0].isAssignableFrom(widgetDataType)
                            && params[1].isAssignableFrom(profileDataType);
            boolean matchThree =
                    params.length == 3
                            && params[0].isAssignableFrom(widgetDataType)
                            && params[1].isAssignableFrom(profileDataType)
                            && params[2] == Bundle.class;
            if (!matchTwo && !matchThree) continue;
            if (found != null) {
                throw new NoSuchMethodException(
                        "Ambiguous WidgetStoreRepository.save methods on " + storeClass.getName());
            }
            found = m;
        }
        if (found == null) {
            throw new NoSuchMethodException(
                    "Missing WidgetStoreRepository.save method on " + storeClass.getName());
        }
        found.setAccessible(true);
        return found;
    }

    static Method resolveStoreNotifyOnPauseMethod(Class<?> storeClass) throws NoSuchMethodException {
        Method anchored = null;
        Method singleZeroVoid = null;
        int zeroVoidCount = 0;
        for (Method m : storeClass.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())
                    || Modifier.isStatic(m.getModifiers())
                    || m.isBridge()
                    || m.isSynthetic()
                    || m.getReturnType() != void.class
                    || m.getParameterCount() != 0) {
                continue;
            }
            singleZeroVoid = m;
            zeroVoidCount++;
            try {
                if (DexSignatures.body(m, Set.of("lock_edit_action_on_pause"))
                        .anchors()
                        .containsKey("lock_edit_action_on_pause")) {
                    anchored = m;
                    break;
                }
            } catch (ReflectiveOperationException | IOException | LinkageError ignored) {
            }
        }
        Method target = anchored != null ? anchored : zeroVoidCount == 1 ? singleZeroVoid : null;
        if (target == null) {
            throw new NoSuchMethodException(
                    "Missing notifyOnPause method on " + storeClass.getName());
        }
        target.setAccessible(true);
        return target;
    }

    static Method resolveFacadeGetter(Class<?> wrapperClass) {
        Method found = null;
        for (Method m : wrapperClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    || m.isBridge()
                    || m.isSynthetic()
                    || m.getParameterCount() != 0
                    || !APP_WIDGET_FACADE.equals(m.getReturnType().getName())) {
                continue;
            }
            if (found != null) return null;
            found = m;
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static void addParsedId(String token, Set<Integer> out) {
        int semi = token.indexOf(';');
        String candidate = semi >= 0 ? token.substring(0, semi) : token;
        if (candidate.isEmpty()) return;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c < '0' || c > '9') return;
        }
        try {
            int id = Integer.parseInt(candidate);
            if (id > 0) out.add(id);
        } catch (NumberFormatException ignored) {
        }
    }

    private static void collectFromWidgetData(Object widgetData, Set<Integer> widgetIds) {
        if (widgetData == null) return;
        try {
            Object rawList = Reflect.call(widgetData, "getWidgetList");
            if (rawList instanceof String listStr) {
                widgetIds.addAll(extractWidgetIds(listStr));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void collectFromProfileAndProvider(
            Context context, Object profileData, Set<Integer> widgetIds) {
        if (profileData != null) {
            try {
                if (hasDeclaredOrPublicZeroArg(profileData.getClass(), "getEnabledWidgetsIdList")) {
                    Object list = Reflect.call(profileData, "getEnabledWidgetsIdList");
                    if (list instanceof String s) widgetIds.addAll(extractWidgetIds(s));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (context == null || isKnoxProfile(profileData)) return;
        List<Integer> persisted = queryWidgetIds(context, ENABLED_LIST_URI, COL_ENABLED_IDS);
        if (persisted != null) widgetIds.addAll(persisted);
    }

    private static boolean isKnoxProfile(Object profileData) {
        if (profileData == null) return false;
        try {
            if (hasDeclaredOrPublicZeroArg(profileData.getClass(), "getFromKnoxProfile")) {
                return Boolean.TRUE.equals(Reflect.call(profileData, "getFromKnoxProfile"));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private static void collectFromGrid(
            Object grid, GridBindings meta, Set<String> gridIds, Set<Integer> widgetIds) {
        try {
            Object repo = meta.repoField == null ? null : meta.repoField.get(grid);
            if (repo != null) {
                if (meta.getAllGridItems != null) {
                    Object items = meta.getAllGridItems.invoke(repo);
                    if (items instanceof Collection<?> list) {
                        for (Object item : list) {
                            if (item == null) continue;
                            try {
                                Object id = Reflect.call(item, "getGridId");
                                if (id instanceof String s && !s.isEmpty()) gridIds.add(s);
                            } catch (ReflectiveOperationException ignored) {
                            }
                        }
                    }
                }
                for (Field mapField : meta.repoMapFields) {
                    collectStringKeys(mapField.get(repo), gridIds);
                }
            }
            Object cache = meta.cacheField == null ? null : meta.cacheField.get(grid);
            if (cache != null) {
                if (meta.cacheMapField != null) {
                    collectStringKeys(meta.cacheMapField.get(cache), gridIds);
                }
                collectIntList(
                        meta.cacheAddedField == null ? null : meta.cacheAddedField.get(cache),
                        widgetIds);
                collectIntList(
                        meta.cacheRemovedField == null ? null : meta.cacheRemovedField.get(cache),
                        widgetIds);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        for (String gridId : gridIds) widgetIds.addAll(extractWidgetIds(gridId));
    }

    private static void collectStringKeys(Object mapObj, Set<String> out) {
        if (!(mapObj instanceof Map<?, ?> map)) return;
        for (Object key : map.keySet()) {
            if (key instanceof String s && !s.isEmpty()) out.add(s);
        }
    }

    private static void collectIntList(Object listObj, Set<Integer> out) {
        if (!(listObj instanceof Collection<?> items)) return;
        for (Object item : items) {
            if (item instanceof Number n && n.intValue() > 0) out.add(n.intValue());
        }
    }

    private static void wipeGridController(
            Object grid, GridBindings meta, Set<String> gridIds, Set<Integer> widgetIds)
            throws ReflectiveOperationException {
        boolean neededFallback = false;
        for (String gridId : gridIds) {
            boolean removedNatively = false;
            if (meta.removeItem != null) {
                try {
                    meta.removeItem.invoke(grid, gridId);
                    removedNatively = true;
                } catch (Throwable ignored) {
                }
            }
            if (!removedNatively) {
                neededFallback = true;
                fallbackRemoveItem(grid, meta, gridId, widgetIds);
            }
        }
        Object repo = meta.repoField == null ? null : meta.repoField.get(grid);
        if (repo != null) {
            if (meta.repoClear != null) {
                try {
                    meta.repoClear.invoke(repo);
                } catch (Throwable ignored) {
                }
            }
            for (Field mapField : meta.repoMapFields) {
                if (mapField.get(repo) instanceof Map<?, ?> m) m.clear();
            }
        }
        Object cache = meta.cacheField == null ? null : meta.cacheField.get(grid);
        if (cache != null) {
            if (meta.cacheMapField != null && meta.cacheMapField.get(cache) instanceof Map<?, ?> m) {
                m.clear();
            }
            if (meta.cacheAddedField != null
                    && meta.cacheAddedField.get(cache) instanceof Collection<?> added) {
                collectIntList(added, widgetIds);
                added.clear();
            }
            if (meta.cacheRemovedField != null
                    && meta.cacheRemovedField.get(cache) instanceof List<?> rawRemoved) {
                @SuppressWarnings("unchecked")
                List<Integer> removed = (List<Integer>) rawRemoved;
                for (Integer id : widgetIds) {
                    if (!removed.contains(id)) removed.add(id);
                }
            }
        }
        if (neededFallback && meta.forEachWidgetView != null) {
            detachRemainingWidgetViews(grid, meta.forEachWidgetView);
        }
    }

    private static void fallbackRemoveItem(
            Object grid, GridBindings meta, String gridId, Set<Integer> widgetIds) {
        try {
            Object repo = meta.repoField == null ? null : meta.repoField.get(grid);
            if (repo != null) {
                if (meta.removeModule != null) {
                    try {
                        meta.removeModule.invoke(repo, gridId);
                    } catch (Throwable ignored) {
                    }
                }
                for (Field mapField : meta.repoMapFields) {
                    if (mapField.get(repo) instanceof Map<?, ?> m) m.remove(gridId);
                }
            }
            Object cache = meta.cacheField == null ? null : meta.cacheField.get(grid);
            if (cache != null && meta.cacheMapField != null
                    && meta.cacheMapField.get(cache) instanceof Map<?, ?> m) {
                m.remove(gridId);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        widgetIds.addAll(extractWidgetIds(gridId));
    }

    private static void detachRemainingWidgetViews(Object grid, Method forEachWidgetView) {
        try {
            List<View> views = new ArrayList<>();
            Consumer<View> collector = views::add;
            forEachWidgetView.invoke(grid, collector);
            for (View view : views) {
                if (view != null && view.getParent() instanceof ViewGroup parent) {
                    parent.removeView(view);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resetDefaultAlign(Object widgetData, ClassLoader loader)
            throws ReflectiveOperationException {
        Object current = Reflect.call(widgetData, "getWidgetAlignType");
        Class<?> alignType =
                current != null
                        ? current.getClass()
                        : Class.forName(ALIGN_TYPE, false, loader);
        if (!alignType.isEnum()) return;
        Reflect.call(widgetData, "setWidgetAlignType", WidgetEngine.enumValue(alignType, "Default"));
    }

    private static void pushWidgetDataObservables(
            Object previewOwner,
            PreviewBindings meta,
            Object explicitObs,
            Object widgetData,
            ClassLoader loader,
            Rect emptyRect) {
        Set<Object> observables = new LinkedHashSet<>();
        if (explicitObs != null) observables.add(explicitObs);
        for (Field f : meta.observableFields) {
            try {
                Object val = f.get(previewOwner);
                Object obs = extractWidgetObservable(val);
                if (obs != null) observables.add(obs);
            } catch (Throwable ignored) {
            }
        }
        for (Object obs : observables) {
            try {
                Object inner = Reflect.call(obs, "getData");
                if (inner != null && inner != widgetData) {
                    clearWidgetModel(inner, loader, emptyRect);
                }
                pushObservable(obs, widgetData != null ? widgetData : inner);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void pushObservable(Object obs, Object data) {
        if (obs == null || data == null) return;
        try {
            Method setDataTwo = findSetDataMethod(obs.getClass());
            if (setDataTwo != null) {
                setDataTwo.invoke(obs, data, false);
                return;
            }
            Reflect.call(obs, "setData", data);
        } catch (Throwable ignored) {
        }
    }

    private static Method findSetDataMethod(Class<?> type) {
        for (Class<?> cur = type; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())
                        && m.getName().equals("setData")
                        && m.getReturnType() == void.class
                        && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == boolean.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static void clearProfileString(Object profileData) {
        if (profileData == null) return;
        callIfDeclared(profileData, "setEnabledWidgetsIdList", String.class, "");
    }

    private static void persistEmptyStore(Object storeRepo, Object widgetData, Object profileData)
            throws Exception {
        if (storeRepo == null || widgetData == null || profileData == null) return;
        StoreBindings meta = storeBindings.get(storeRepo.getClass());
        if (meta.takesBundle) {
            meta.saveMethod.invoke(storeRepo, widgetData, profileData, null);
        } else {
            meta.saveMethod.invoke(storeRepo, widgetData, profileData);
        }
        meta.notifyOnPause.invoke(storeRepo);
    }

    private static void deleteOrphanedWidgetIds(
            Context context,
            Object previewOwner,
            PreviewBindings previewMeta,
            Object gridController,
            GridBindings gridMeta,
            Set<Integer> widgetIds) {
        if (context == null || widgetIds.isEmpty()) return;
        List<Integer> activeElsewhere =
                queryWidgetIds(context, ALL_PROFILES_LIST_URI, COL_ALL_PROFILES_IDS);
        if (activeElsewhere == null) return;
        Set<Integer> keep = new LinkedHashSet<>(activeElsewhere);

        Object facade = resolveFacadeInstance(previewOwner, previewMeta);
        Method disableMethod = facade == null ? null : previewMeta.facadeDisable;
        Object fallbackDisable = resolveFallbackDisable(gridController, gridMeta);

        List<?> rawRemoved = null;
        if (gridController != null
                && gridMeta != null
                && gridMeta.cacheField != null
                && gridMeta.cacheRemovedField != null) {
            try {
                Object cache = gridMeta.cacheField.get(gridController);
                if (cache != null && gridMeta.cacheRemovedField.get(cache) instanceof List<?> l) {
                    rawRemoved = l;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        for (Integer id : widgetIds) {
            if (keep.contains(id)) continue;
            boolean deleted = false;
            if (facade != null && disableMethod != null) {
                try {
                    disableMethod.invoke(facade, id);
                    deleted = true;
                } catch (Throwable ignored) {
                }
            }
            if (!deleted && fallbackDisable != null) {
                try {
                    Reflect.call(fallbackDisable, "invoke", id);
                    deleted = true;
                } catch (Throwable ignored) {
                }
            }
            if (deleted && rawRemoved != null) {
                rawRemoved.remove(id);
            }
        }
    }

    private static Object resolveFacadeInstance(Object previewOwner, PreviewBindings meta) {
        for (Field lazyField : meta.lazyFields) {
            try {
                Object lazy = lazyField.get(previewOwner);
                if (lazy == null) continue;
                Object wrapper = Reflect.call(lazy, "get");
                if (wrapper == null) continue;
                Method getter =
                        meta.facadeGetter != null
                                        && meta.facadeGetter
                                                .getDeclaringClass()
                                                .isInstance(wrapper)
                                ? meta.facadeGetter
                                : resolveFacadeGetter(wrapper.getClass());
                if (getter != null) return getter.invoke(wrapper);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object resolveFallbackDisable(Object gridController, GridBindings meta) {
        if (gridController == null || meta == null
                || meta.cacheField == null || meta.cacheDisableField == null) {
            return null;
        }
        try {
            Object cache = meta.cacheField.get(gridController);
            return cache == null ? null : meta.cacheDisableField.get(cache);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static List<Integer> queryWidgetIds(Context context, String uri, String column) {
        try {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor cursor = resolver.query(Uri.parse(uri), null, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) return List.of();
                int idx = cursor.getColumnIndex(column);
                if (idx < 0 && cursor.getColumnCount() == 1) idx = 0;
                if (idx < 0) return List.of();
                return extractWidgetIds(cursor.getString(idx));
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static StoreBindings resolveStoreBindings(Class<?> storeClass)
            throws NoSuchMethodException {
        ClassLoader loader = storeClass.getClassLoader();
        Class<?> widgetDataType;
        Class<?> profileDataType;
        try {
            widgetDataType = Class.forName(WIDGET_DATA_CLASS, false, loader);
            profileDataType =
                    Class.forName(
                            "com.samsung.android.lockscreen.editor.item.domain.entity.profile.ProfileData",
                            false,
                            loader);
        } catch (ClassNotFoundException error) {
            NoSuchMethodException wrapped = new NoSuchMethodException("Missing store entity types");
            wrapped.initCause(error);
            throw wrapped;
        }
        Method save = resolveStoreSaveMethod(storeClass, widgetDataType, profileDataType);
        Method notifyOnPause = resolveStoreNotifyOnPauseMethod(storeClass);
        return new StoreBindings(save, save.getParameterCount() == 3, notifyOnPause);
    }

    private static PreviewBindings resolvePreviewBindings(Class<?> previewClass) {
        Field refreshField = null;
        Method refreshMethod = null;
        Field enableField = null;
        Method enableMethod = null;
        Field updateListStrField = null;
        Method updateListStrMethod = null;
        List<Field> lazyFields = new ArrayList<>();
        Method facadeGetter = null;
        Method facadeDisable = null;
        List<Field> observableFields = new ArrayList<>();

        for (Field f : previewClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Class<?> t = f.getType();

            if (isLazyField(t)) {
                if ("a".equals(f.getName())) lazyFields.add(0, f);
                else lazyFields.add(f);
            }

            if (t == Object.class || t.getName().equals(WIDGET_DATA_CLASS)) continue;

            if (mightBeWidgetObservableHolder(t)) {
                observableFields.add(f);
            }

            UseCaseMatch match = classifyUseCaseField(t);
            if (match == UseCaseMatch.REFRESH && refreshField == null) {
                refreshField = f;
                refreshMethod = findZeroArgVoidMethod(t);
            } else if (match == UseCaseMatch.ENABLE && enableField == null) {
                enableField = f;
                enableMethod = findBoolVoidMethod(t);
            } else if (match == UseCaseMatch.UPDATE_LIST_STRING && updateListStrField == null) {
                updateListStrField = f;
                updateListStrMethod = findZeroArgVoidMethod(t);
            }
        }

        ClassLoader loader = previewClass.getClassLoader();
        Class<?> wrapperClass = findLockItemWrapperClass(previewClass);
        try {
            if (wrapperClass != null) {
                facadeGetter = resolveFacadeGetter(wrapperClass);
            }
            Class<?> facadeClass = Class.forName(APP_WIDGET_FACADE, false, loader);
            facadeDisable = NativeContract.method(facadeClass, "disable", void.class, int.class);
        } catch (Throwable ignored) {
        }

        return new PreviewBindings(
                refreshField,
                refreshMethod,
                enableField,
                enableMethod,
                updateListStrField,
                updateListStrMethod,
                List.copyOf(lazyFields),
                facadeGetter,
                facadeDisable,
                List.copyOf(observableFields));
    }

    private enum UseCaseMatch {
        NONE,
        REFRESH,
        ENABLE,
        UPDATE_LIST_STRING
    }

    private static UseCaseMatch classifyUseCaseField(Class<?> type) {
        if (type.isInterface() || type.isArray() || frameworkType(type)) {
            return UseCaseMatch.NONE;
        }
        boolean hasZeroVoid = findZeroArgVoidMethod(type) != null;
        boolean hasBoolVoid = findBoolVoidMethod(type) != null;
        if (!hasZeroVoid && !hasBoolVoid) return UseCaseMatch.NONE;

        boolean callsSetWidgetList = false;
        boolean callsSetModuleInfoList = false;
        boolean callsEnable = false;
        boolean callsNotiAssist = false;
        for (Method m : type.getDeclaredMethods()) {
            try {
                for (DexSignatures.Call c : DexSignatures.body(m).calls()) {
                    if (c.owner().equals(WIDGET_DATA_DESC) && c.name().equals("setWidgetList")) {
                        callsSetWidgetList = true;
                    }
                    if (c.owner().endsWith("/ModuleAssistData;")
                            && c.name().equals("setModuleInfoList")) {
                        callsSetModuleInfoList = true;
                    }
                    if (c.owner().equals(WIDGET_DATA_DESC)
                            && (c.name().equals("setEnable") || c.name().equals("getEnable"))) {
                        callsEnable = true;
                    }
                    for (String pt : c.parameters()) {
                        if (pt.endsWith("/NotificationData;")) {
                            callsNotiAssist = true;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (hasZeroVoid && callsSetWidgetList && callsSetModuleInfoList) {
            return UseCaseMatch.REFRESH;
        }
        if (hasBoolVoid && callsEnable && callsNotiAssist) {
            return UseCaseMatch.ENABLE;
        }
        if (hasZeroVoid && callsSetWidgetList && !callsSetModuleInfoList) {
            return UseCaseMatch.UPDATE_LIST_STRING;
        }
        return UseCaseMatch.NONE;
    }

    private static Class<?> findLockItemWrapperClass(Class<?> previewClass) {
        ClassLoader loader = previewClass.getClassLoader();
        for (Field f : previewClass.getDeclaredFields()) {
            Class<?> t = f.getType();
            if (t.isPrimitive() || t.isInterface() || t.isArray() || frameworkType(t)) continue;
            for (Method m : t.getDeclaredMethods()) {
                Class<?> found = scanMethodForWrapper(m, loader);
                if (found != null) return found;
            }
        }
        for (Method m : previewClass.getDeclaredMethods()) {
            Class<?> found = scanMethodForWrapper(m, loader);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean frameworkType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("android.")
                || name.startsWith("androidx.")
                || name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("kotlin.")
                || name.startsWith("kotlinx.")
                || name.startsWith("dagger.")
                || name.startsWith("dalvik.")
                || name.startsWith("com.google.");
    }

    private static Class<?> scanMethodForWrapper(Method method, ClassLoader loader) {
        String uiDesc = "L" + UI_CONTROLLER.replace('.', '/') + ";";
        try {
            for (DexSignatures.Call c : DexSignatures.body(method).calls()) {
                if (!c.result().equals(uiDesc) || !c.parameters().isEmpty()) continue;
                Class<?> candidate =
                        Class.forName(
                                c.owner().substring(1, c.owner().length() - 1).replace('/', '.'),
                                false,
                                loader);
                if (resolveFacadeGetter(candidate) != null) return candidate;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean mightBeWidgetObservableHolder(Class<?> t) {
        String name = t.getName();
        if (name.startsWith("android.") || name.startsWith("androidx.") || name.startsWith("java.")) {
            return false;
        }
        if (OBSERVABLE_DATA.equals(name) || MUTABLE_OBSERVABLE_DATA.equals(name)) return true;
        for (Class<?> iface : t.getInterfaces()) {
            if (OBSERVABLE_DATA.equals(iface.getName())) return true;
        }
        for (Method m : t.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0
                    && OBSERVABLE_DATA.equals(m.getReturnType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Object extractWidgetObservable(Object val) throws ReflectiveOperationException {
        if (val == null) return null;
        if (isObservableData(val.getClass())) {
            Object inner = Reflect.call(val, "getData");
            return isWidgetData(inner) ? val : null;
        }
        for (Method m : val.getClass().getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getParameterCount() == 0
                    && OBSERVABLE_DATA.equals(m.getReturnType().getName())) {
                m.setAccessible(true);
                Object obs = m.invoke(val);
                if (obs != null && isWidgetData(Reflect.call(obs, "getData"))) return obs;
            }
        }
        return null;
    }

    private static boolean isObservableData(Class<?> type) {
        for (Class<?> cur = type; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            if (OBSERVABLE_DATA.equals(cur.getName()) || MUTABLE_OBSERVABLE_DATA.equals(cur.getName())) {
                return true;
            }
            for (Class<?> iface : cur.getInterfaces()) {
                if (OBSERVABLE_DATA.equals(iface.getName())) return true;
            }
        }
        return false;
    }

    private static boolean isWidgetData(Object value) {
        return value != null && WIDGET_DATA_CLASS.equals(value.getClass().getName());
    }

    private static boolean isLazyField(Class<?> type) {
        return "dagger.Lazy".equals(type.getName()) || WidgetEditorBinding.isZeroArgSupplier(type);
    }

    private static Method findZeroArgVoidMethod(Class<?> type) {
        Method found = null;
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())
                    && !Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && !m.isSynthetic()
                    && m.getParameterCount() == 0
                    && m.getReturnType() == void.class) {
                if (found != null) return null;
                found = m;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static Method findBoolVoidMethod(Class<?> type) {
        Method found = null;
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())
                    && !Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && !m.isSynthetic()
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == boolean.class
                    && m.getReturnType() == void.class) {
                if (found != null) return null;
                found = m;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static GridBindings resolveGridBindings(Class<?> gridClass) {
        Method removeItem = findMethodOpt(gridClass, "removeItem", String.class);
        Method initGridLayoutSize =
                findMethodOpt(gridClass, "initGridLayoutSize", int.class, int.class);
        Method updateSavedModuleInfo =
                findMethodOpt(gridClass, "updateSavedModuleInfo", ArrayList.class);
        Method forEachWidgetView =
                findMethodOpt(gridClass, "forEachWidgetView", Consumer.class);

        Field repoField = null;
        Field cacheField = null;
        for (Field f : gridClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Class<?> ft = f.getType();
            if (repoField == null
                    && findMethodOpt(ft, "getAllGridItems") != null
                    && findMethodOpt(ft, "clear") != null) {
                repoField = f;
            }
            if (cacheField == null
                    && findFieldOpt(ft, "temporaryAddedTileList", ArrayList.class) != null
                    && findFieldOpt(ft, "temporaryRemovedTileList", ArrayList.class) != null
                    && findFieldOpt(ft, "moduleInfoMap", Map.class) != null) {
                cacheField = f;
            }
        }

        Method getAllGridItems = null;
        Method repoClear = null;
        Method removeModule = null;
        List<Field> repoMapFields = new ArrayList<>();
        if (repoField != null) {
            Class<?> rt = repoField.getType();
            getAllGridItems = findMethodOpt(rt, "getAllGridItems");
            repoClear = findMethodOpt(rt, "clear");
            removeModule = findMethodOpt(rt, "removeModule", String.class);
            for (Field rf : rt.getDeclaredFields()) {
                if (!Modifier.isStatic(rf.getModifiers())
                        && Map.class.isAssignableFrom(rf.getType())) {
                    rf.setAccessible(true);
                    repoMapFields.add(rf);
                }
            }
        }

        Field cacheMapField = null;
        Field cacheAddedField = null;
        Field cacheRemovedField = null;
        Field cacheDisableField = null;
        if (cacheField != null) {
            Class<?> ct = cacheField.getType();
            cacheMapField = findFieldOpt(ct, "moduleInfoMap", Map.class);
            cacheAddedField = findFieldOpt(ct, "temporaryAddedTileList", ArrayList.class);
            cacheRemovedField = findFieldOpt(ct, "temporaryRemovedTileList", ArrayList.class);
            for (Field cf : ct.getDeclaredFields()) {
                if (!Modifier.isStatic(cf.getModifiers())
                        && "kotlin.jvm.functions.Function1".equals(cf.getType().getName())) {
                    cf.setAccessible(true);
                    cacheDisableField = cf;
                    break;
                }
            }
        }

        return new GridBindings(
                removeItem,
                initGridLayoutSize,
                updateSavedModuleInfo,
                forEachWidgetView,
                repoField,
                getAllGridItems,
                repoClear,
                removeModule,
                List.copyOf(repoMapFields),
                cacheField,
                cacheMapField,
                cacheAddedField,
                cacheRemovedField,
                cacheDisableField);
    }

    private static void invokeUseCase(
            Object owner, Field field, Method method, Object... args) {
        if (field == null || method == null) return;
        try {
            Object useCase = field.get(owner);
            if (useCase != null) method.invoke(useCase, args);
        } catch (Throwable ignored) {
        }
    }

    private static void invokeOptional(Method method, Object target, Object... args) {
        if (method == null || target == null) return;
        try {
            method.invoke(target, args);
        } catch (Throwable ignored) {
        }
    }

    private static void callIfDeclared(
            Object target, String name, Class<?> paramType, Object arg) {
        if (target == null) return;
        Method m = findMethodOpt(target.getClass(), name, paramType);
        if (m == null) return;
        try {
            m.invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasDeclaredOrPublicZeroArg(Class<?> type, String name) {
        return findMethodOpt(type, name) != null;
    }

    private static Method findMethodOpt(Class<?> type, String name, Class<?>... params) {
        for (Class<?> cur = type; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            try {
                Method m = cur.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field findFieldOpt(Class<?> type, String name, Class<?> expectedSuper) {
        try {
            Field f = type.getDeclaredField(name);
            if (!Modifier.isStatic(f.getModifiers()) && expectedSuper.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }
}
