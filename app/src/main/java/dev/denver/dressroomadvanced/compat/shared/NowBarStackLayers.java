package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extra rear layers; the native three-card stack still owns gestures and promotion. */
final class NowBarStackLayers {
    private final DressroomModule module;
    private final NowBarStackBinding binding;
    private final Class<?> stackClass;
    private final Field items, restricted, mode, screenOn;
    private final Method expanded, content;
    private final Field dragging, reordering, dismissing, animationManager, animationsEnabled;
    private final ThreadLocal<Release> release = new ThreadLocal<>();
    private final Constructor<?> stackSize;
    private final WeakHashMap<ViewGroup, WeakReference<Tail>> tails = new WeakHashMap<>();
    private volatile int liveTails;
    private final WeakHashMap<android.content.res.Resources, float[]> offsets = new WeakHashMap<>();

    private NowBarStackLayers(DressroomModule module, NowBarStackBinding binding) {
        this.module = module;
        this.binding = binding;
        stackClass = binding.stack;
        items = binding.items;
        restricted = binding.restricted;
        mode = binding.mode;
        screenOn = binding.screenOn;
        expanded = binding.expanded;
        content = binding.content;
        stackSize = binding.stackSize;
        dragging = binding.dragging;
        reordering = binding.reordering;
        dismissing = binding.dismissing;
        animationManager = binding.animationManager;
        animationsEnabled = binding.animationsEnabled;
    }

    static void install(DressroomModule module, NowBarStackBinding binding)
            throws ReflectiveOperationException {
        NowBarStackLayers layers = new NowBarStackLayers(module, binding);
        Class<?> stack = binding.stack;
        Method refresh = binding.refresh;
        Method layout =
                FrameLayout.class.getDeclaredMethod(
                        "onLayout", boolean.class, int.class, int.class, int.class, int.class);
        try (HookTransaction hooks = new HookTransaction()) {
            for (String name : new String[] {"f", "g"}) {
                Method method = stack.getDeclaredMethod(name, int.class);
                hooks.add(
                        module.hook(method)
                                        .setId("dressroom-advanced.stack-density-" + name)
                                        .intercept(
                                                chain -> {
                                                    ViewGroup root =
                                                            (ViewGroup) chain.getThisObject();
                                                    int rank =
                                                            ((Number) chain.getArg(0)).intValue();
                                                    int count = layers.count(root);
                                                    Release moving = layers.release.get();
                                                    if (count <= 3
                                                            || rank < 0
                                                            || (rank > 2
                                                                    && (moving == null
                                                                            || moving.root != root
                                                                            || moving.target
                                                                                    != rank)))
                                                        return chain.proceed();
                                                    try {
                                                        return name.equals("f")
                                                                ? layers.scale(rank, count)
                                                                : layers.offset(root, rank, count);
                                                    } catch (RuntimeException error) {
                                                        module.report(
                                                                "Now Bar stack density unavailable",
                                                                error);
                                                        return chain.proceed();
                                                    }
                                                })
                                ::unhook);
                module.deoptimize(method);
            }
            hooks.add(
                    module.hook(refresh)
                                    .setId("dressroom-advanced.stack-rear-refresh")
                                    .intercept(
                                            chain -> {
                                                ViewGroup root = (ViewGroup) chain.getThisObject();
                                                if (layers.busy(root)) return chain.proceed();
                                                Tail tail = layers.tail(root);
                                                boolean completing =
                                                        tail != null && tail.outgoing != null;
                                                Object manager = layers.animationManager.get(root);
                                                boolean enabled =
                                                        layers.animationsEnabled.getBoolean(
                                                                manager);
                                                // Release shared external Views before native
                                                // promotion. During the handoff,
                                                // the incoming third card has already appeared and
                                                // must not fade in again.
                                                layers.clear(root);
                                                try {
                                                    if (completing)
                                                        layers.animationsEnabled.setBoolean(
                                                                manager, false);
                                                    Object result = chain.proceed();
                                                    layers.rebuild(root);
                                                    return result;
                                                } finally {
                                                    if (completing)
                                                        layers.animationsEnabled.setBoolean(
                                                                manager, enabled);
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(layout)
                                    .setId("dressroom-advanced.stack-rear-layout")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                if (stack.isInstance(chain.getThisObject())) {
                                                    ViewGroup root =
                                                            (ViewGroup) chain.getThisObject();
                                                    if (!layers.tails.containsKey(root)) {
                                                        layers.tails.put(
                                                                root, new WeakReference<>(null));
                                                        root.post(() -> layers.rebuild(root));
                                                    }
                                                    layers.position(root);
                                                }
                                                return result;
                                            })
                            ::unhook);
            for (String name : new String[] {"z", "setExpandedCardState", "setExpandVIStarted"}) {
                Method method =
                        name.equals("z")
                                ? stack.getDeclaredMethod(name)
                                : stack.getDeclaredMethod(name, boolean.class);
                hooks.add(
                        module.hook(method)
                                        .setId("dressroom-advanced.stack-rear-state-" + name)
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    layers.sync((ViewGroup) chain.getThisObject());
                                                    return result;
                                                })
                                ::unhook);
                module.deoptimize(method);
            }
            Method wave = binding.wave;
            if (wave != null) {
                hooks.add(
                        module.hook(wave)
                                        .setId("dressroom-advanced.stack-rear-wave")
                                        .intercept(
                                                chain -> {
                                                    // Rear cards never display the playing-wave
                                                    // effect. Samsung casts their
                                                    // parent to NowBarCardStackView before checking
                                                    // whether they are in front.
                                                    if (((View) chain.getThisObject()).getParent()
                                                            instanceof Tail) return null;
                                                    return chain.proceed();
                                                })
                                ::unhook);
                module.deoptimize(wave);
            }
            Method depth = stack.getDeclaredMethod("e", View.class, boolean.class);
            hooks.add(
                    module.hook(depth)
                                    .setId("dressroom-advanced.stack-release-depth")
                                    .intercept(
                                            chain -> {
                                                Release moving = layers.release.get();
                                                View card = (View) chain.getArg(0);
                                                if (moving != null
                                                        && moving.root == chain.getThisObject()
                                                        && moving.front == card
                                                        && card.getTranslationZ() == 0f)
                                                    return moving.target;
                                                return chain.proceed();
                                            })
                            ::unhook);
            Method touch = stack.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            hooks.add(
                    module.hook(touch)
                                    .setId("dressroom-advanced.stack-release")
                                    .intercept(
                                            chain -> {
                                                ViewGroup root = (ViewGroup) chain.getThisObject();
                                                MotionEvent event = (MotionEvent) chain.getArg(0);
                                                if (event == null
                                                        || layers.count(root) <= 3
                                                        || Boolean.TRUE.equals(
                                                                layers.expanded.invoke(root)))
                                                    return chain.proceed();
                                                Tail tail = layers.tail(root);
                                                if (event.getActionMasked()
                                                        == MotionEvent.ACTION_DOWN) {
                                                    if (tail != null) tail.stop();
                                                }
                                                if (event.getActionMasked()
                                                        != MotionEvent.ACTION_UP)
                                                    return chain.proceed();
                                                View front =
                                                        (View) Reflect.call(root, "getTopCard");
                                                if (front == null
                                                        || tail == null
                                                        || tail.getChildCount() == 0)
                                                    return chain.proceed();
                                                int count = layers.count(root);
                                                int total =
                                                        ((List<?>) layers.items.get(root)).size();
                                                Release moving =
                                                        new Release(
                                                                root,
                                                                front,
                                                                count,
                                                                Math.min(count, total - 1),
                                                                front.getTranslationY());
                                                layers.release.set(moving);
                                                Object result;
                                                try {
                                                    result = chain.proceed();
                                                } finally {
                                                    layers.release.remove();
                                                }
                                                if (layers.reordering.getBoolean(root))
                                                    tail.start(moving);
                                                return result;
                                            })
                            ::unhook);
            Method drawChild =
                    ViewGroup.class.getDeclaredMethod(
                            "drawChild", Canvas.class, View.class, long.class);
            hooks.add(
                    module.hook(drawChild)
                                    .setId("dressroom-advanced.stack-release-drawing")
                                    .intercept(
                                            chain -> {
                                                if (layers.liveTails == 0) return chain.proceed();
                                                if (stack.isInstance(chain.getThisObject())) {
                                                    Tail tail =
                                                            layers.tail(
                                                                    (ViewGroup)
                                                                            chain.getThisObject());
                                                    if (tail != null
                                                            && tail.outgoing != null
                                                            && tail.outgoing.front
                                                                    == chain.getArg(1)) return true;
                                                }
                                                return chain.proceed();
                                            })
                            ::unhook);
            for (Method method : new Method[] {depth, touch, drawChild}) module.deoptimize(method);
            Method configuration =
                    stack.getDeclaredMethod(
                            "onConfigurationChanged", android.content.res.Configuration.class);
            hooks.add(
                    module.hook(configuration)
                                    .setId("dressroom-advanced.stack-rear-configuration")
                                    .intercept(
                                            chain -> {
                                                ViewGroup root = (ViewGroup) chain.getThisObject();
                                                layers.offsets.remove(root.getResources());
                                                Object result = chain.proceed();
                                                layers.position(root);
                                                return result;
                                            })
                            ::unhook);
            module.deoptimize(configuration);
            Method detach = stack.getDeclaredMethod("onDetachedFromWindow");
            hooks.add(
                    module.hook(detach)
                                    .setId("dressroom-advanced.stack-rear-release")
                                    .intercept(
                                            chain -> {
                                                layers.remove((ViewGroup) chain.getThisObject());
                                                return chain.proceed();
                                            })
                            ::unhook);
            module.deoptimize(refresh);
            module.deoptimize(layout);
            module.deoptimize(detach);
            hooks.commit();
        }
        RuntimeExtras.listen(
                () -> {
                    for (ViewGroup root : new ArrayList<>(layers.tails.keySet())) {
                        if (!root.isAttachedToWindow()) continue;
                        try {
                            refresh.invoke(
                                    root,
                                    new CopyOnWriteArrayList<>((List<?>) layers.items.get(root)));
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            module.report("Now Bar stack count refresh unavailable", error);
                        }
                    }
                });
        android.util.Log.i(DressroomModule.TAG, "Configurable Now Bar rear layers installed");
    }

    private int count(ViewGroup root) {
        try {
            if (restricted.getBoolean(root)) return 3;
            return Math.min(
                    RuntimeExtras.get(root.getContext()).getInt("stackCards", 3),
                    ((List<?>) items.get(root)).size());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 3;
        }
    }

    private float scale(int rank, int count) {
        float position = 2f * rank / (count - 1);
        return position <= 1 ? 1f - .08f * position : .92f - .12f * (position - 1);
    }

    private float offset(ViewGroup root, int rank, int count) {
        float position = 2f * rank / (count - 1);
        android.content.res.Resources resources = root.getResources();
        float[] dimensions = offsets.get(resources);
        if (dimensions == null) {
            int second =
                    resources.getIdentifier(
                            "now_bar_cardview_2nd_card_bottom_offset", "dimen", binding.resources);
            int third =
                    resources.getIdentifier(
                            "now_bar_cardview_3rd_card_bottom_offset", "dimen", binding.resources);
            dimensions =
                    new float[] {resources.getDimension(second), resources.getDimension(third)};
            offsets.put(resources, dimensions);
        }
        float middle = dimensions[0], back = dimensions[1];
        return position <= 1 ? middle * position : middle + (back - middle) * (position - 1);
    }

    private Tail tail(ViewGroup root) {
        WeakReference<Tail> reference = tails.get(root);
        return reference == null ? null : reference.get();
    }

    private void clear(ViewGroup root) {
        Tail tail = tail(root);
        if (tail != null) {
            tail.stop();
            tail.removeAllViews();
            tail.setVisibility(View.INVISIBLE);
        }
    }

    private void remove(ViewGroup root) {
        Tail tail = tail(root);
        tails.remove(root);
        if (tail != null) {
            liveTails--;
            tail.stop();
            if (tail.getParent() instanceof ViewGroup parent) parent.removeView(tail);
        }
    }

    private void rebuild(ViewGroup root) {
        try {
            // Track native stacks even at the default value, so Apply can enable layers
            // immediately through the existing Extras observer.
            if (!tails.containsKey(root)) tails.put(root, new WeakReference<>(null));
            int count = count(root);
            if (count <= 3
                    || !root.isAttachedToWindow()
                    || Boolean.TRUE.equals(expanded.invoke(root))) return;
            clear(root);
            if (!(root.getParent() instanceof FrameLayout parent)) return;
            Tail tail = tail(root);
            if (tail == null || tail.getParent() != parent) {
                remove(root);
                tail = new Tail(root);
                tails.put(root, new WeakReference<>(tail));
                liveTails++;
                parent.addView(
                        tail, 0, new FrameLayout.LayoutParams(root.getWidth(), root.getHeight()));
            }
            List<?> values = (List<?>) items.get(root);
            for (int rank = Math.min(count, values.size() - 1); rank >= 3; rank--) {
                Object value = values.get(rank);
                View card = inflate(root, value);
                if (card == null) continue;
                Reflect.call(card, "setGetStackSize", stackSize.newInstance(root, 0));
                Reflect.call(card, "setViewMode", mode.get(root));
                Reflect.call(card, "setInitialDozing", !screenOn.getBoolean(root));
                binding.bindItem.invoke(card, value, Integer.valueOf(2));
                binding.hideContents.invoke(card, 2, false);
                card.setClickable(false);
                card.setFocusable(false);
                tail.ranks.put(card, new Rank(rank, count));
                card.setAlpha(rank < count ? 1f : 0f);
                tail.addView(card, new FrameLayout.LayoutParams(-1, -1));
                binding.hideContents.invoke(card, 2, false);
            }
            position(root);
            tail.setVisibility(View.VISIBLE);
            sync(root);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            clear(root);
            module.report("Extra Now Bar rear layers unavailable", error);
        }
    }

    private View inflate(ViewGroup root, Object value) throws ReflectiveOperationException {
        int style = ((Number) Reflect.call(value, "getNowBarViewStyle")).intValue();
        String layout =
                switch (style) {
                    case 1 -> "nowbar_media_layout";
                    case 2 -> "nowbar_mode_layout";
                    case 4 -> "nowbar_battery_layout";
                    case 5 -> "nowbar_contact_layout";
                    case 6, 7 -> "nowbar_external_view_layout";
                    case 8 -> "nowbar_brief_view_layout";
                    default -> "nowbar_ongoing_layout";
                };
        Bundle extras = (Bundle) Reflect.call(value, "getExtraData");
        if (extras != null && extras.getBoolean("nowbar_key_app_search_data_card"))
            layout = "nowbar_app_search_card_layout";
        else if (Boolean.TRUE.equals(Reflect.call(value, "isCall"))) layout = "nowbar_call_layout";
        int id = root.getResources().getIdentifier(layout, "layout", binding.resources);
        return id == 0 ? null : LayoutInflater.from(root.getContext()).inflate(id, root, false);
    }

    private void position(ViewGroup root) {
        Tail tail = tail(root);
        if (tail == null) return;
        ViewGroup.LayoutParams params = tail.getLayoutParams();
        if (params.width != root.getWidth() || params.height != root.getHeight()) {
            params.width = root.getWidth();
            params.height = root.getHeight();
            tail.setLayoutParams(params);
        }
        // This sibling is inserted after the parent's measurement pass. A layout alone
        // leaves new native rear cards at 0 x 0 until another unrelated measure occurs.
        if (tail.isLayoutRequested()
                || tail.getMeasuredWidth() != root.getWidth()
                || tail.getMeasuredHeight() != root.getHeight())
            tail.measure(
                    View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        tail.layout(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
        tail.setTranslationX(root.getTranslationX());
        tail.setTranslationY(root.getTranslationY());
        tail.setTranslationZ(root.getTranslationZ() - 1);
    }

    private void sync(ViewGroup root) {
        Tail tail = tail(root);
        if (tail == null) return;
        try {
            boolean visible =
                    count(root) > 3
                            && root.getVisibility() == View.VISIBLE
                            && !restricted.getBoolean(root)
                            && !Boolean.TRUE.equals(expanded.invoke(root))
                            && !((Enum<?>) mode.get(root)).name().equals("AOD");
            if (visible
                    && tail.getChildCount() == 0
                    && !tail.rebuilding
                    && tail.getVisibility() != View.VISIBLE) {
                tail.rebuilding = true;
                Tail pending = tail;
                root.post(
                        () -> {
                            pending.rebuilding = false;
                            if (root.isAttachedToWindow() && count(root) > 3) rebuild(root);
                        });
            }
            if (!visible) tail.stop();
            tail.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            tail.setAlpha(root.getAlpha());
        } catch (ReflectiveOperationException | RuntimeException error) {
            tail.setVisibility(View.INVISIBLE);
        }
    }

    private boolean busy(ViewGroup root) throws IllegalAccessException {
        return dragging.getBoolean(root)
                || reordering.getBoolean(root)
                || dismissing.getBoolean(root);
    }

    private record Rank(int rank, int count) {}

    private record Release(ViewGroup root, View front, int count, int target, float startY) {}

    private final class Tail extends FrameLayout {
        private final WeakReference<ViewGroup> root;
        final IdentityHashMap<View, Rank> ranks = new IdentityHashMap<>();
        boolean rebuilding;
        Release outgoing;
        ViewTreeObserver observer;
        float progress;
        final ViewTreeObserver.OnPreDrawListener advance = this::advanceFrame;

        private boolean advanceFrame() {
            if (outgoing == null) return true;
            try {
                if (!reordering.getBoolean(outgoing.root) || !outgoing.root.isAttachedToWindow()) {
                    ViewGroup owner = outgoing.root;
                    stop();
                    if (owner.isAttachedToWindow())
                        owner.post(
                                () -> {
                                    try {
                                        Reflect.call(
                                                owner,
                                                "q",
                                                new CopyOnWriteArrayList<>(
                                                        (List<?>) items.get(owner)));
                                    } catch (ReflectiveOperationException
                                            | RuntimeException error) {
                                        module.report(
                                                "Now Bar interrupted handoff refresh unavailable",
                                                error);
                                    }
                                });
                    return true;
                }
                float destination = offset(outgoing.root, outgoing.target, outgoing.count);
                float distance = destination - outgoing.startY;
                progress =
                        Math.abs(distance) < .01f
                                ? 1f
                                : Math.max(
                                        0,
                                        Math.min(
                                                1,
                                                (outgoing.front.getTranslationY() - outgoing.startY)
                                                        / distance));
                for (int i = 0; i < getChildCount(); i++) {
                    View card = getChildAt(i);
                    Rank rank = ranks.get(card);
                    if (rank == null) continue;
                    float startScale = scale(rank.rank, rank.count),
                            endScale = scale(rank.rank - 1, rank.count);
                    float scale = startScale + (endScale - startScale) * progress;
                    card.setScaleX(scale);
                    card.setScaleY(scale);
                    float startY = offset(outgoing.root, rank.rank, rank.count),
                            endY = offset(outgoing.root, rank.rank - 1, rank.count);
                    card.setTranslationY(startY + (endY - startY) * progress);
                    card.setAlpha(rank.rank >= rank.count ? progress : 1f);
                }
                invalidate();
            } catch (ReflectiveOperationException | RuntimeException error) {
                stop();
                module.report("Now Bar rear transition unavailable", error);
            }
            return true;
        }

        void start(Release moving) {
            stop();
            outgoing = moving;
            progress = 0;
            observer = getViewTreeObserver();
            observer.addOnPreDrawListener(advance);
            invalidate();
            moving.root.invalidate();
        }

        void stop() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(advance);
            observer = null;
            outgoing = null;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            Release moving = outgoing;
            if (moving != null && moving.front.getParent() == moving.root) {
                // Draw the outgoing native card below every rear layer while its own spring
                // keeps updating it. Reparenting it would break Samsung's child-count logic.
                View card = moving.front;
                int saved = canvas.save();
                canvas.translate(card.getLeft(), card.getTop());
                canvas.concat(card.getMatrix());
                if (moving.target >= moving.count)
                    canvas.saveLayerAlpha(
                            0,
                            0,
                            card.getWidth(),
                            card.getHeight(),
                            Math.round((1 - progress) * 255));
                card.draw(canvas);
                canvas.restoreToCount(saved);
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }

        Tail(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            setClipChildren(false);
            setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        @Override
        public void removeAllViews() {
            super.removeAllViews();
            ranks.clear();
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            return false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            ViewGroup owner = root.get();
            if (owner == null) return;
            for (int i = 0; i < getChildCount(); i++) {
                View card = getChildAt(i);
                Rank rank = ranks.get(card);
                if (rank == null) continue;
                try {
                    View pill = (View) content.invoke(card);
                    // Media nests the normal container inside its background; find the
                    // direct ancestor before deriving its center in card coordinates.
                    while (pill != null
                            && pill.getParent() instanceof View parent
                            && parent != card) pill = parent;
                    if (pill == null || pill.getHeight() <= 0) continue;
                    card.setPivotX(pill.getLeft() + pill.getWidth() * .5f);
                    card.setPivotY(pill.getTop() + pill.getHeight() * .5f);
                    if (outgoing != null) continue;
                    float scale = scale(rank.rank, rank.count);
                    card.setScaleX(scale);
                    card.setScaleY(scale);
                    card.setTranslationY(offset(owner, rank.rank, rank.count));
                } catch (ReflectiveOperationException | RuntimeException error) {
                    module.report("Now Bar rear geometry unavailable", error);
                }
            }
        }
    }
}
