package dev.denver.dressroomadvanced.lyrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Apple Music style lyrics view for lockscreen expanded media player.
 * Features a top header with track artwork thumbnail, title, artist,
 * and large, bold, left-aligned auto-scrolling lyrics with defocus blur and fading edges.
 */
public final class LyricsView extends FrameLayout {
    private static final RenderEffect INACTIVE_BLUR_EFFECT;
    static {
        if (Build.VERSION.SDK_INT >= 31) {
            INACTIVE_BLUR_EFFECT = RenderEffect.createBlurEffect(13f, 13f, Shader.TileMode.CLAMP);
        } else {
            INACTIVE_BLUR_EFFECT = null;
        }
    }

    private final LinearLayout headerLayout;
    private final ImageView headerArtView;
    private final TextView headerTitleView;
    private final TextView headerArtistView;
    private final TextView headerCloseBtn;

    private final FrameLayout contentContainer;
    private final ScrollView scrollView;
    private final LinearLayout linesContainer;
    private final TextView plainLyricsView;
    private final TextView statusView;
    private final TextView attributionView;

    private List<LyricLine> lyricLines = new ArrayList<>();
    private final List<LyricLineView> lineViews = new ArrayList<>();
    private int activeLineIndex = -1;
    private boolean isSynced = false;
    private boolean autoScrollEnabled = true;
    private int textSizeSp = 32;
    private boolean userInteracting = false;
    private long lastUserScrollMs = 0L;

    public interface OnLyricSeekListener {
        void onSeek(long timestampMs);
    }

    private OnLyricSeekListener seekListener;
    private Runnable closeListener;

    public LyricsView(Context context) {
        super(context);

        // Apple Music style: transparent container, floating directly above fullscreen blurred cover
        setBackgroundColor(Color.TRANSPARENT);

        // Main vertical structure: Header bar on top, lyrics ScrollView below
        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        addView(rootLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Apple Music Top Header Bar
        headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dpToPx(24);
        int padTop = dpToPx(38);
        int padBottom = dpToPx(12);
        headerLayout.setPadding(padH, padTop, padH, padBottom);

        // Header Thumbnail Art (rounded 10dp)
        headerArtView = new ImageView(context);
        int artSize = dpToPx(48);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        headerArtView.setLayoutParams(artLp);
        headerArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholderBg = new GradientDrawable();
        placeholderBg.setColor(Color.argb(70, 255, 255, 255));
        placeholderBg.setCornerRadius(dpToPx(10));
        headerArtView.setBackground(placeholderBg);
        headerArtView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(10));
            }
        });
        headerArtView.setClipToOutline(true);
        headerLayout.addView(headerArtView);

        // Header Title & Artist Text Column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        colLp.setMargins(dpToPx(14), 0, dpToPx(10), 0);
        textCol.setLayoutParams(colLp);

        headerTitleView = new TextView(context);
        headerTitleView.setTextColor(Color.WHITE);
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        headerTitleView.setTypeface(getLyricsTypeface(context));
        headerTitleView.setLetterSpacing(-0.01f);
        headerTitleView.setSingleLine(true);
        headerTitleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(headerTitleView);

        headerArtistView = new TextView(context);
        headerArtistView.setTextColor(Color.argb(190, 255, 255, 255));
        headerArtistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        headerArtistView.setSingleLine(true);
        headerArtistView.setEllipsize(TextUtils.TruncateAt.END);
        headerArtistView.setPadding(0, dpToPx(2), 0, 0);
        textCol.addView(headerArtistView);

        headerLayout.addView(textCol);

        // Header Close Button (frosted glass circular pill button)
        headerCloseBtn = new TextView(context);
        headerCloseBtn.setText("✕");
        headerCloseBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        headerCloseBtn.setTypeface(Typeface.DEFAULT_BOLD);
        headerCloseBtn.setTextColor(Color.WHITE);
        headerCloseBtn.setGravity(Gravity.CENTER);
        int closeSize = dpToPx(32);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
        headerCloseBtn.setLayoutParams(closeLp);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.argb(55, 255, 255, 255));
        headerCloseBtn.setBackground(closeBg);
        headerCloseBtn.setClickable(true);
        headerCloseBtn.setFocusable(true);
        headerCloseBtn.setOnTouchListener((v, event) -> {
            ViewParent p = v.getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        headerCloseBtn.setOnClickListener(v -> {
            if (closeListener != null) closeListener.run();
        });
        headerLayout.addView(headerCloseBtn);

        rootLayout.addView(headerLayout);

        // 2. Lyrics Content Area (occupying remainder of height)
        contentContainer = new FrameLayout(context);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1.0f);
        rootLayout.addView(contentContainer, contentLp);

        // Status view (Loading / No lyrics)
        statusView = new TextView(context);
        statusView.setTextColor(Color.argb(180, 255, 255, 255));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setText("Loading lyrics...");
        contentContainer.addView(statusView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Scroll view with Apple Music style fading edges
        scrollView = new ScrollView(context) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (userInteracting) {
                    lastUserScrollMs = System.currentTimeMillis();
                }
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.setFadingEdgeLength(dpToPx(45));
        scrollView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                case android.view.MotionEvent.ACTION_MOVE:
                    ViewParent p = v.getParent();
                    if (p != null) p.requestDisallowInterceptTouchEvent(true);
                    userInteracting = true;
                    lastUserScrollMs = System.currentTimeMillis();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    userInteracting = false;
                    lastUserScrollMs = System.currentTimeMillis();
                    break;
            }
            return false;
        });

        linesContainer = new LinearLayout(context);
        linesContainer.setOrientation(LinearLayout.VERTICAL);
        linesContainer.setGravity(Gravity.START);
        linesContainer.setPadding(dpToPx(28), dpToPx(20), dpToPx(28), dpToPx(280));

        plainLyricsView = new TextView(context);
        plainLyricsView.setTextColor(Color.WHITE);
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        plainLyricsView.setTypeface(getLyricsTypeface(context));
        plainLyricsView.setLetterSpacing(-0.02f);
        plainLyricsView.setLineSpacing(dpToPx(4), 1.12f);
        plainLyricsView.setGravity(Gravity.START);
        plainLyricsView.setPadding(0, dpToPx(8), 0, dpToPx(8));
        plainLyricsView.setVisibility(GONE);

        linesContainer.addView(plainLyricsView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.addView(linesContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.setVisibility(GONE);
        contentContainer.addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Source attribution label (scrolled footer at bottom of lyrics container)
        attributionView = new TextView(context);
        attributionView.setTextColor(Color.argb(120, 255, 255, 255));
        attributionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        attributionView.setTypeface(getLyricsTypeface(context));
        attributionView.setGravity(Gravity.START);
        attributionView.setPadding(dpToPx(4), dpToPx(28), dpToPx(4), dpToPx(16));
    }

    public void setOnLyricSeekListener(OnLyricSeekListener listener) {
        this.seekListener = listener;
    }

    public void setOnCloseListener(Runnable listener) {
        this.closeListener = listener;
    }

    public void prepareForOpen() {
        animate().cancel();
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        if (headerLayout != null) {
            headerLayout.animate().cancel();
            headerLayout.setAlpha(0f);
            headerLayout.setTranslationY(-dpToPx(14));
        }
        if (contentContainer != null) {
            contentContainer.animate().cancel();
            contentContainer.setAlpha(0f);
            contentContainer.setTranslationY(dpToPx(36));
        }
    }

    public void animateOpen() {
        prepareForOpen();

        // 1. Root container fade in
        animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                .setListener(null)
                .start();

        // 2. Header slides down slightly and fades in
        if (headerLayout != null) {
            headerLayout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }

        // 3. Lyrics scroll content slides up from below and fades in
        if (contentContainer != null) {
            contentContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new PathInterpolator(0.18f, 0f, 0.08f, 1f))
                    .start();
        }
    }

    public void animateClose(Runnable onEnd) {
        animate().cancel();
        animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setVisibility(View.GONE);
                        setAlpha(1f);
                        if (headerLayout != null) {
                            headerLayout.setTranslationY(0f);
                            headerLayout.setAlpha(1f);
                        }
                        if (contentContainer != null) {
                            contentContainer.setTranslationY(0f);
                            contentContainer.setAlpha(1f);
                        }
                        if (onEnd != null) onEnd.run();
                    }
                })
                .start();

        if (headerLayout != null) {
            headerLayout.animate().cancel();
            headerLayout.animate()
                    .alpha(0f)
                    .translationY(-dpToPx(10))
                    .setDuration(180)
                    .start();
        }

        if (contentContainer != null) {
            contentContainer.animate().cancel();
            contentContainer.animate()
                    .alpha(0f)
                    .translationY(dpToPx(24))
                    .setDuration(190)
                    .start();
        }
    }

    public void setTrackInfo(String title, String artist, Bitmap cover) {
        post(() -> {
            headerTitleView.setText(title != null ? title : "");
            headerArtistView.setText(artist != null ? artist : "");
            if (cover != null && !cover.isRecycled()) {
                headerArtView.setImageBitmap(cover);
            } else {
                headerArtView.setImageDrawable(null);
            }
        });
    }

    public void setAutoScrollEnabled(boolean enabled) {
        this.autoScrollEnabled = enabled;
        if (!enabled) {
            for (TextView tv : lineViews) {
                tv.setAlpha(0.9f);
                if (Build.VERSION.SDK_INT >= 31) {
                    tv.setRenderEffect(null);
                }
            }
        }
    }

    public void setTextSizeSp(int sizeSp) {
        this.textSizeSp = Math.max(16, Math.min(38, sizeSp));
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        for (TextView tv : lineViews) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        }
    }

    public void setLyrics(LyricsEntry entry) {
        lyricLines = new ArrayList<>();
        lineViews.clear();
        linesContainer.removeAllViews();
        linesContainer.addView(plainLyricsView);
        activeLineIndex = -1;

        if (entry == null || !entry.hasLyrics()) {
            statusView.setVisibility(VISIBLE);
            statusView.setText(entry != null && entry.notFound() ? "No lyrics found" : "Loading lyrics...");
            scrollView.setVisibility(GONE);
            attributionView.setVisibility(GONE);
            return;
        }

        statusView.setVisibility(GONE);
        scrollView.setVisibility(VISIBLE);
        attributionView.setVisibility(VISIBLE);

        if (entry.hasSyncedLyrics()) {
            isSynced = true;
            lyricLines = LrcParser.parse(entry.syncedLyrics());
            plainLyricsView.setVisibility(GONE);

            String src = "lrclib".equalsIgnoreCase(entry.source()) ? "LRCLIB • Synced" : entry.source() + " • Synced";
            attributionView.setText(src);

            Context ctx = getContext();
            int linePadding = dpToPx(9);
            for (int i = 0; i < lyricLines.size(); i++) {
                LyricLine line = lyricLines.get(i);
                LyricLineView lineTv = new LyricLineView(ctx);
                lineTv.setText(line.text());
                lineTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                lineTv.setTypeface(getLyricsTypeface(ctx));
                lineTv.setTextColor(Color.WHITE);
                lineTv.setLetterSpacing(-0.025f);
                lineTv.setAlpha(0.38f);
                if (INACTIVE_BLUR_EFFECT != null) {
                    lineTv.setRenderEffect(INACTIVE_BLUR_EFFECT);
                }
                lineTv.setLineSpacing(dpToPx(3), 1.14f);
                lineTv.setGravity(Gravity.START);
                lineTv.setPadding(dpToPx(4), linePadding, dpToPx(4), linePadding);

                final int lineIndex = i;
                lineTv.setOnClickListener(v -> {
                    if (seekListener != null && lineIndex < lyricLines.size()) {
                        seekListener.onSeek(lyricLines.get(lineIndex).timestampMs());
                    }
                });

                linesContainer.addView(lineTv, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                lineViews.add(lineTv);
            }
            linesContainer.addView(attributionView, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            scrollView.scrollTo(0, 0);
        } else if (entry.hasPlainLyrics()) {
            isSynced = false;
            plainLyricsView.setVisibility(VISIBLE);
            plainLyricsView.setText(entry.plainLyrics());
            String src = "lyrics.ovh".equalsIgnoreCase(entry.source()) ? "lyrics.ovh" : entry.source();
            attributionView.setText(src + " • Plain");
            linesContainer.addView(attributionView, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            scrollView.scrollTo(0, 0);
        }
    }

    /**
     * Updates active highlighted lyric line and triggers smooth scroll (Apple Music style).
     * Continually advances the gradual white fill across the active line.
     */
    public void updatePlaybackPosition(long positionMs) {
        if (!isSynced || lyricLines.isEmpty() || lineViews.isEmpty()) return;

        int newIndex = LrcParser.findActiveLineIndex(lyricLines, positionMs);

        // Handle line transition
        if (newIndex != activeLineIndex) {
            int oldIndex = activeLineIndex;
            activeLineIndex = newIndex;

            for (int i = 0; i < lineViews.size(); i++) {
                LyricLineView v = lineViews.get(i);
                if (i != newIndex) {
                    v.setActive(false);
                    v.setProgress(i < newIndex ? 1f : 0f);
                }
            }

            if (autoScrollEnabled) {
                if (oldIndex >= 0 && oldIndex < lineViews.size()) {
                    LyricLineView oldTv = lineViews.get(oldIndex);
                    oldTv.animate().alpha(0.38f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
                    if (INACTIVE_BLUR_EFFECT != null) {
                        oldTv.setRenderEffect(INACTIVE_BLUR_EFFECT);
                    }
                }

                if (newIndex >= 0 && newIndex < lineViews.size()) {
                    LyricLineView newTv = lineViews.get(newIndex);
                    newTv.setActive(true);
                    newTv.setPivotX(0f);
                    newTv.setPivotY(newTv.getHeight() / 2f);
                    newTv.animate().alpha(1.0f).scaleX(1.04f).scaleY(1.04f).setDuration(220).start();
                    if (Build.VERSION.SDK_INT >= 31) {
                        newTv.setRenderEffect(null);
                    }

                    // Auto-scroll so active line is positioned around 35% from the top (Apple Music style)
                    long now = System.currentTimeMillis();
                    if (!userInteracting && (now - lastUserScrollMs > 2500L)) {
                        post(() -> {
                            int viewTop = newTv.getTop();
                            int scrollHeight = scrollView.getHeight();
                            int targetY = viewTop - (int) (scrollHeight * 0.35f);
                            scrollView.smoothScrollTo(0, Math.max(0, targetY));
                        });
                    }
                }
            }
        }

        // Continually update gradual white fill flow on active line
        if (activeLineIndex >= 0 && activeLineIndex < lineViews.size() && activeLineIndex < lyricLines.size()) {
            LyricLine currentLine = lyricLines.get(activeLineIndex);
            long startMs = currentLine.timestampMs();
            long nextStartMs = (activeLineIndex + 1 < lyricLines.size())
                    ? lyricLines.get(activeLineIndex + 1).timestampMs()
                    : startMs + 5000L;

            long lineGap = nextStartMs - startMs;
            long singingDuration;
            if (lineGap > 5000L) {
                int charCount = currentLine.text().length();
                singingDuration = Math.min(lineGap - 500L, Math.max(2000L, charCount * 80L));
            } else {
                singingDuration = Math.max(800L, lineGap - 250L);
            }

            long elapsed = positionMs - startMs;
            float progress = (float) elapsed / (float) Math.max(1L, singingDuration);
            lineViews.get(activeLineIndex).setProgress(progress);
        }
    }

    private static Typeface cachedSfProHeavy;

    private static Typeface getLyricsTypeface(Context context) {
        if (cachedSfProHeavy != null) return cachedSfProHeavy;

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            cachedSfProHeavy = Typeface.create(Typeface.SANS_SERIF, 800, false);
        } else {
            cachedSfProHeavy = Typeface.create("sans-serif", Typeface.BOLD);
        }
        return cachedSfProHeavy;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return true;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * Custom TextView rendering an Apple Music style gradual glowing white fill
     * across words as each lyric line progresses.
     */
    public static class LyricLineView extends TextView {
        private static final int COLOR_ACTIVE = 0xFFFFFFFF;
        private static final int COLOR_INACTIVE = 0x55FFFFFF;

        private float progress = 0f;
        private boolean isActive = false;

        public LyricLineView(Context context) {
            super(context);
        }

        public void setActive(boolean active) {
            if (this.isActive != active) {
                this.isActive = active;
                if (!active) {
                    progress = 0f;
                }
                invalidate();
            }
        }

        public void setProgress(float progress) {
            float clamped = Math.max(0f, Math.min(1f, progress));
            if (this.progress == clamped) return;
            this.progress = clamped;
            if (isActive) {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!isActive) {
                super.onDraw(canvas);
                return;
            }

            Layout layout = getLayout();
            if (layout == null) {
                super.onDraw(canvas);
                return;
            }

            TextPaint paint = getPaint();
            int origColor = paint.getColor();
            Shader origShader = paint.getShader();

            int padLeft = getCompoundPaddingLeft();
            int padTop = getCompoundPaddingTop();

            try {
                // 1. Draw base unsung text in dim translucent white
                canvas.save();
                canvas.translate(padLeft, padTop);
                paint.setShader(null);
                paint.setColor(COLOR_INACTIVE);
                layout.draw(canvas);
                canvas.restore();

                if (progress <= 0f) {
                    return;
                }

                if (progress >= 1f) {
                    // Fully sung line: solid bright white
                    canvas.save();
                    canvas.translate(padLeft, padTop);
                    paint.setShader(null);
                    paint.setColor(COLOR_ACTIVE);
                    layout.draw(canvas);
                    canvas.restore();
                    return;
                }

                int totalChars = getText().length();
                if (totalChars == 0) return;

                int lineCount = layout.getLineCount();
                float charPos = progress * totalChars;
                int activeChar = Math.min(totalChars - 1, Math.max(0, (int) charPos));
                int activeLine = layout.getLineForOffset(activeChar);

                // 2a. Any lines before activeLine are fully sung -> draw full bright white
                if (activeLine > 0) {
                    canvas.save();
                    canvas.translate(padLeft, padTop);
                    canvas.clipRect(-dpToPx(10), layout.getLineTop(0), getWidth(), layout.getLineBottom(activeLine - 1));
                    paint.setShader(null);
                    paint.setColor(COLOR_ACTIVE);
                    layout.draw(canvas);
                    canvas.restore();
                }

                // 2b. The active line being sung -> smooth flowing white fill
                int lineStart = layout.getLineStart(activeLine);
                int lineEnd = layout.getLineEnd(activeLine);
                float lineFrac = (charPos - lineStart) / Math.max(1f, (float) (lineEnd - lineStart));
                lineFrac = Math.max(0f, Math.min(1f, lineFrac));

                float lineLeft = layout.getLineLeft(activeLine);
                float lineRight = layout.getLineRight(activeLine);
                float currX = lineLeft + (lineRight - lineLeft) * lineFrac;

                float ramp = dpToPx(18);
                float x0 = currX - ramp;
                float x1 = currX + ramp;
                if (x1 <= x0) x1 = x0 + 1f;

                LinearGradient shader = new LinearGradient(
                        x0, 0, x1, 0,
                        new int[]{COLOR_ACTIVE, 0x00FFFFFF},
                        new float[]{0f, 1f},
                        Shader.TileMode.CLAMP);

                canvas.save();
                canvas.translate(padLeft, padTop);
                canvas.clipRect(-dpToPx(10), layout.getLineTop(activeLine), x1, layout.getLineBottom(activeLine));
                paint.setColor(COLOR_ACTIVE);
                paint.setShader(shader);
                layout.draw(canvas);
                canvas.restore();

            } finally {
                paint.setColor(origColor);
                paint.setShader(origShader);
            }
        }

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }
    }
}
