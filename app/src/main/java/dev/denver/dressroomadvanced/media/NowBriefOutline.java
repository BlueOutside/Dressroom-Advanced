package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** The GenUI fill is gone, so draw the pill edge above the native glass. */
final class NowBriefOutline extends Drawable implements ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener {
    private final WeakReference<View> card;
    private final Method surface, state, radius, dark, border;
    private WeakReference<View> owner = new WeakReference<>(null);
    private WeakReference<Drawable> source = new WeakReference<>(null);
    private ViewTreeObserver observer;
    private GradientDrawable stroke;
    private float corner;

    NowBriefOutline(View card, Method surface, Method state, Method radius, Method dark,
            Method border) {
        this.card = new WeakReference<>(card);
        this.surface = surface;
        this.state = state;
        this.radius = radius;
        this.dark = dark;
        this.border = border;
        card.addOnAttachStateChangeListener(this);
        if (card.isAttachedToWindow()) onViewAttachedToWindow(card);
    }

    @Override public boolean onPreDraw() {
        View view = card.get();
        try {
            View pill = view != null && (Integer) state.invoke(view) == 0
                    && (Float) dark.invoke(view) == 0f
                    ? (View) surface.invoke(view) : null;
            if (pill == null || !pill.isShown() || pill.getWidth() == 0 || pill.getHeight() == 0) {
                remove();
                return true;
            }
            View nativeBorder = (View) border.invoke(view);
            Drawable background = nativeBorder == null ? null : nativeBorder.getBackground();
            if (!(background instanceof GradientDrawable) || background.getConstantState() == null) {
                remove();
                return true;
            }
            float next = (Float) radius.invoke(view);
            // Copy the stroke Samsung just resolved for this wallpaper. Its own view
            // keeps the native layout and visibility throughout the card transitions.
            if (stroke == null || source.get() != background) {
                stroke = (GradientDrawable) background.getConstantState()
                        .newDrawable(pill.getResources()).mutate();
                stroke.setCornerRadius(next);
                stroke.setBounds(getBounds());
                source = new WeakReference<>(background);
                corner = next;
                invalidateSelf();
            } else if (corner != next) {
                corner = next;
                stroke.setCornerRadius(next);
                invalidateSelf();
            }
            if (stroke.getAlpha() != background.getAlpha()) {
                stroke.setAlpha(background.getAlpha());
                invalidateSelf();
            }
            setBounds(0, 0, pill.getWidth(), pill.getHeight());
            if (owner.get() != pill) {
                remove();
                pill.getOverlay().add(this);
                owner = new WeakReference<>(pill);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            remove();
        }
        return true;
    }

    @Override public void onViewAttachedToWindow(View view) {
        observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
        remove();
    }

    void close(View view) {
        onViewDetachedFromWindow(view);
        view.removeOnAttachStateChangeListener(this);
    }

    private void remove() {
        View view = owner.get();
        if (view != null) view.getOverlay().remove(this);
        owner.clear();
    }

    @Override public void draw(Canvas canvas) {
        if (stroke != null) stroke.draw(canvas);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        if (stroke != null) stroke.setBounds(bounds);
    }

    @Override public void setAlpha(int alpha) {
        if (stroke != null) stroke.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        if (stroke != null) stroke.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
