package dev.denver.dressroomadvanced.lyrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
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
 * Timestamp-driven lyric rows with line highlighting and enhanced-LRC word sweeps.
 * Scroll anticipation stays separate from vocal timing; row translations compensate
 * for scroll and interlude layout changes before the next draw.
 */
public final class LyricsView extends FrameLayout {

    // Apple Music exact easing curves
    private static final PathInterpolator CASCADE_INTERPOLATOR =
            new PathInterpolator(0.4f, 0.1f, 0.0f, 1.0f);
    private static final PathInterpolator ALPHA_INTERPOLATOR =
            new PathInterpolator(0.39f, 0.575f, 0.565f, 1.0f);
    private static final PathInterpolator EASE_INTERPOLATOR =
            new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f);
    private static final PathInterpolator DOT_FILL_INTERPOLATOR =
            new PathInterpolator(0.0f, 0.25f, 1.0f, 0.58f);
    private static final PathInterpolator DOT_EXIT_SHRINK_INTERPOLATOR =
            new PathInterpolator(0.25f, 0.0f, 1.0f, 0.2f);
    private static final AccelerateInterpolator DOT_ENTRY_INTERPOLATOR =
            new AccelerateInterpolator();

    // Scale constants: active line is 1.00f (never overflows horizontal bounds!)
    public static final float LINE_SCALE_NORMAL = 0.98f;
    public static final float LINE_SCALE_HIGHLIGHT = 1.00f;

    // Exact Apple Music timing parameters (ms)
    private static final float CASCADE_DURATION_MS = 750f;
    private static final float SEEK_SETTLE_DURATION_MS = 380f;
    private static final float SCALE_IN_DELAY_MS = 150f;
    private static final float SCALE_IN_DURATION_MS = 500f;
    private static final float SCALE_OUT_DELAY_MS = 50f;
    private static final float SCALE_OUT_DURATION_MS = 350f;
    private static final float ALPHA_IN_DELAY_MS = 0f;
    private static final float ALPHA_IN_DURATION_MS = 180f;
    private static final float ALPHA_OUT_DELAY_MS = 200f;
    private static final float ALPHA_OUT_DURATION_MS = 350f;

    // Exact Apple Music syllable lift spring (dampingRatio = 0.93f, stiffness = 25.0f)
    private static final float LIFT_SPRING_STIFFNESS = 25.0f;
    private static final float LIFT_SPRING_DAMPING = 0.93f;
    private static final float LIFT_SPRING_OMEGA = 5.0f; // sqrt(25.0f)

    // Manual browsing hold timeout before auto-scroll gently resumes on lockscreen
    private static final long MANUAL_SCROLL_HOLD_MS = 4500L;

    // Calibrated vibrant white alphas (crisp across dark, bright, and live-cover surfaces)
    private static final float ALPHA_ACTIVE_LEAD = 0.98f;
    private static final float ALPHA_ACTIVE_BACKING = 0.84f;
    private static final float ALPHA_UPCOMING_LEAD = 0.38f;
    private static final float ALPHA_PAST_LEAD = 0.26f;
    private static final float ALPHA_UPCOMING_BACKING = 0.28f;
    private static final float ALPHA_PAST_BACKING = 0.20f;
    private static final float ALPHA_MANUAL_BROWSE = 0.50f;
    private static final float BLUR_STEP_DP = 0.25f;
    private static final int MAX_BLUR_BUCKET = 16;

    private final RenderEffect backdropBlur;
    private final RenderEffect[] lineBlurEffects = new RenderEffect[MAX_BLUR_BUCKET + 1];

    private final ImageView localBackdropImage;
    private final View localBackdropScrim;
    private final LinearLayout headerLayout;
    private final ImageView headerArtView;
    private final TextView headerTitleView;
    private final TextView headerArtistView;
    private final HeaderActionButton headerCloseBtn;
    private final HeaderActionButton headerPinBtn;
    private boolean screenPinned;
    private boolean pageOpen;

    private final FrameLayout contentContainer;
    private final AlphaFadeFrameLayout fadeContainer;
    private final ScrollView scrollView;
    private final LinearLayout linesContainer;
    private final TextView plainLyricsView;
    private final TextView statusView;
    private final TextView attributionView;

    private List<LyricLine> lyricLines = new ArrayList<>();
    private LyricsEntry displayedEntry;
    private boolean lyricsAssigned;
    private final List<RowHolder> rowHolders = new ArrayList<>();
    private final LyricScrollMotion scrollMotion = new LyricScrollMotion(
            value -> CASCADE_INTERPOLATOR.getInterpolation((float) value));
    private boolean[] activeScratch = new boolean[0];
    private int primaryAnchorIndex = -1;
    private boolean isSynced = false;
    private boolean autoScrollEnabled = true;
    private int textSizeSp = 28;
    private Typeface lyricTypeface = Typeface.DEFAULT_BOLD;
    private Typeface artistTypeface = Typeface.DEFAULT;

    private boolean userInteracting = false;
    private boolean manualBrowseLocked = false;
    private boolean wasUserBrowsing = false;
    private long lastUserScrollUptimeMs = 0L;
    private float manualBrowseMix = 0f;
    private boolean scrollInitialized = false;
    private int lastScrolledAnchorIndex = -1;
    private boolean layoutCompensationArmed = false;
    private boolean pendingPostLayoutScroll = false;
    private int lastViewportHeight = 0;

    private boolean useLocalBackdrop = false;
    private int statusBarInsetPx = 0;
    private Bitmap currentCoverBitmap;

    public interface OnLyricSeekListener {
        void onSeek(long timestampMs);
    }

    private OnLyricSeekListener seekListener;
    private Runnable closeListener;

    private static final class RowHolder {
        final View view;
        final LyricLine line;
        final LyricLineView textView;
        final InterludeDotsView interludeView;
        final long nextVocalMs;

        boolean wasActive;
        boolean stateInitialized;

        // Deterministic Apple Music scale transition (0.98f <-> 1.00f)
        float scaleFrom = LINE_SCALE_NORMAL;
        float scaleTo = LINE_SCALE_NORMAL;
        float currentScale = LINE_SCALE_NORMAL;
        float scaleElapsedMs = 0f;
        float scaleDelayMs = 0f;
        float scaleDurationMs = 1f;

        // Deterministic Apple Music focus/alpha transition (0f <-> 1f)
        float focusFrom = 0f;
        float focusTo = 0f;
        float currentFocus = 0f;
        float focusElapsedMs = 0f;
        float focusDelayMs = 0f;
        float focusDurationMs = 1f;

        // Smoothed final view alpha
        float currentAlpha;
        float blurDp;
        int blurBucket;

        // Apple Music triangular cascade wave (750ms)
        float cascadeFromY = 0f;
        float currentCascadeY = 0f;
        float scrollLagY, scrollLagStartY;
        long scrollDelayMs;
        float cascadeElapsedMs = 0f;
        float cascadeDelayMs = 0f;
        float cascadeDurationMs = CASCADE_DURATION_MS;
        boolean cascadeActive = false;

        // Apple Music spring float lift (-2dp, stiffness=25, damping=0.93)
        float liftY = 0f;
        float liftVelY = 0f;
        float lastSungProgress;

        // Layout-shift tracking for glitch-free interlude expand/collapse
        int lastLaidOutTop = Integer.MIN_VALUE;

        RowHolder(View view, LyricLine line, LyricLineView textView, InterludeDotsView interludeView, long nextVocalMs) {
            this.view = view;
            this.line = line;
            this.textView = textView;
            this.interludeView = interludeView;
            this.nextVocalMs = nextVocalMs;
            this.currentAlpha = line.isInterlude()
                    ? 0f
                    : (line.isBackgroundVocal() ? ALPHA_UPCOMING_BACKING : ALPHA_UPCOMING_LEAD);
        }

        void startCascade(float deltaY, long delayMs, float durationMs) {
            float nextFrom = currentCascadeY + deltaY;
            if (Math.abs(nextFrom) < 0.5f) {
                currentCascadeY = 0f;
                cascadeFromY = 0f;
                cascadeActive = false;
                view.setTranslationY(scrollLagY + liftY);
                return;
            }
            cascadeFromY = nextFrom;
            currentCascadeY = nextFrom;
            cascadeElapsedMs = 0f;
            cascadeDelayMs = Math.max(0f, delayMs);
            cascadeDurationMs = Math.max(1f, durationMs);
            cascadeActive = true;
            // Layout callbacks can run after this frame's animation tick. Compensate
            // before drawing, rather than exposing the new scroll position for one frame.
            view.setTranslationY(currentCascadeY + scrollLagY + liftY);
        }
    }

    public LyricsView(Context context) {
        super(context);

        if (Build.VERSION.SDK_INT >= 31) {
            float d = context.getResources().getDisplayMetrics().density;
            backdropBlur = RenderEffect.createBlurEffect(34f * d, 34f * d, Shader.TileMode.CLAMP);
        } else {
            backdropBlur = null;
        }

        setBackgroundColor(Color.TRANSPARENT);

        // Optional local blurred cover backdrop used when Fullscreen Album Cover is disabled
        int cardMargin = dpToPx(12);
        int cardRadius = dpToPx(26);
        ViewOutlineProvider cardOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadius);
            }
        };

        localBackdropImage = new ImageView(context);
        localBackdropImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        localBackdropImage.setVisibility(GONE);
        localBackdropImage.setOutlineProvider(cardOutline);
        localBackdropImage.setClipToOutline(true);
        if (backdropBlur != null) {
            localBackdropImage.setRenderEffect(backdropBlur);
        }
        LayoutParams bgLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        bgLp.setMargins(cardMargin, dpToPx(4), cardMargin, dpToPx(8));
        addView(localBackdropImage, bgLp);

        localBackdropScrim = new View(context);
        GradientDrawable scrimBg = new GradientDrawable();
        scrimBg.setColor(Color.argb(138, 12, 12, 16));
        scrimBg.setCornerRadius(cardRadius);
        localBackdropScrim.setBackground(scrimBg);
        localBackdropScrim.setVisibility(GONE);
        LayoutParams scrimLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        scrimLp.setMargins(cardMargin, dpToPx(4), cardMargin, dpToPx(8));
        addView(localBackdropScrim, scrimLp);

        // Main vertical structure: Header bar on top, Apple Music alpha-faded lyrics below
        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        addView(rootLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Top Header Bar (topMargin on LyricsView already clears status bar)
        headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dpToPx(24);
        int padTop = dpToPx(14);
        int padBottom = dpToPx(12);
        headerLayout.setPadding(padH, padTop, padH, padBottom);

        headerArtView = new ImageView(context);
        int artSize = dpToPx(44);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        headerArtView.setLayoutParams(artLp);
        headerArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholderBg = new GradientDrawable();
        placeholderBg.setColor(Color.argb(56, 255, 255, 255));
        placeholderBg.setStroke(dpToPx(1), Color.argb(40, 255, 255, 255));
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

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        colLp.setMargins(dpToPx(12), 0, dpToPx(6), 0);
        textCol.setLayoutParams(colLp);

        headerTitleView = new TextView(context);
        headerTitleView.setTextColor(Color.WHITE);
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        headerTitleView.setTypeface(lyricTypeface);
        headerTitleView.setLetterSpacing(-0.01f);
        headerTitleView.setSingleLine(true);
        headerTitleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(headerTitleView);

        headerArtistView = new TextView(context);
        headerArtistView.setTextColor(Color.argb(195, 255, 255, 255));
        headerArtistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        headerArtistView.setTypeface(artistTypeface);
        headerArtistView.setSingleLine(true);
        headerArtistView.setEllipsize(TextUtils.TruncateAt.END);
        headerArtistView.setPadding(0, dpToPx(2), 0, 0);
        textCol.addView(headerArtistView);

        headerLayout.addView(textCol);

        headerPinBtn = new HeaderActionButton(context, true);
        headerPinBtn.setContentDescription("Keep screen on");
        headerPinBtn.setOnClickListener(v -> {
            screenPinned = !screenPinned;
            headerPinBtn.setPinned(screenPinned);
            updateScreenPin();
        });
        headerLayout.addView(headerPinBtn, new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));

        headerCloseBtn = new HeaderActionButton(context, false);
        headerCloseBtn.setContentDescription("Close lyrics");
        headerCloseBtn.setOnClickListener(v -> {
            if (closeListener != null) closeListener.run();
        });
        headerLayout.addView(headerCloseBtn, new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));

        rootLayout.addView(headerLayout);

        // Lyrics viewport with a stationary edge fade.
        contentContainer = new FrameLayout(context);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1.0f);
        rootLayout.addView(contentContainer, contentLp);

        statusView = new TextView(context);
        statusView.setTextColor(Color.argb(185, 255, 255, 255));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusView.setTypeface(artistTypeface);
        statusView.setGravity(Gravity.CENTER);
        statusView.setText("Loading lyrics...");
        contentContainer.addView(statusView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        fadeContainer = new AlphaFadeFrameLayout(context);
        fadeContainer.setVisibility(GONE);
        contentContainer.addView(fadeContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scrollView = new ScrollView(context) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (userInteracting) {
                    lastUserScrollUptimeMs = SystemClock.uptimeMillis();
                }
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setVerticalFadingEdgeEnabled(false);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);
        scrollView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ViewParent pd = v.getParent();
                    if (pd != null) pd.requestDisallowInterceptTouchEvent(true);
                    userInteracting = true;
                    lastUserScrollUptimeMs = SystemClock.uptimeMillis();
                    settleCascadeForManualDrag();
                    break;
                case MotionEvent.ACTION_MOVE:
                    ViewParent pm = v.getParent();
                    if (pm != null) pm.requestDisallowInterceptTouchEvent(true);
                    userInteracting = true;
                    lastUserScrollUptimeMs = SystemClock.uptimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    userInteracting = false;
                    lastUserScrollUptimeMs = SystemClock.uptimeMillis();
                    manualBrowseLocked = !isRowVisibleInViewport(primaryAnchorIndex);
                    break;
            }
            return false;
        });

        scrollView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
            int vh = b - t;
            if (vh > 0 && vh != lastViewportHeight) {
                lastViewportHeight = vh;
                updateLinesContainerVerticalPadding(vh);
            }
        });

        linesContainer = new LinearLayout(context);
        linesContainer.setOrientation(LinearLayout.VERTICAL);
        linesContainer.setGravity(Gravity.START);
        linesContainer.setClipChildren(false);
        linesContainer.setClipToPadding(false);
        int horizMargin = dpToPx(20); // Apple Music lyrics_line_margin_horiz
        linesContainer.setPadding(horizMargin, dpToPx(36), horizMargin, dpToPx(360));
        linesContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
            if (layoutCompensationArmed && scrollInitialized) {
                layoutCompensationArmed = false;
                applyInterludeLayoutCompensation();
            } else {
                snapshotLaidOutTops();
            }
            if (isSynced && primaryAnchorIndex >= 0
                    && (pendingPostLayoutScroll || !scrollInitialized)) {
                pendingPostLayoutScroll = false;
                scrollToAnchorWithCascade(primaryAnchorIndex, !scrollInitialized);
            }
        });

        plainLyricsView = new TextView(context);
        plainLyricsView.setTextColor(Color.WHITE);
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        plainLyricsView.setTypeface(lyricTypeface);
        plainLyricsView.setLetterSpacing(-0.018f);
        plainLyricsView.setLineSpacing(dpToPx(4), 1.15f);
        plainLyricsView.setGravity(Gravity.START);
        plainLyricsView.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        plainLyricsView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        plainLyricsView.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        plainLyricsView.setVisibility(GONE);

        linesContainer.addView(plainLyricsView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.addView(linesContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        fadeContainer.addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        attributionView = new TextView(context);
        attributionView.setTextColor(Color.argb(120, 255, 255, 255));
        attributionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        attributionView.setGravity(Gravity.START);
        attributionView.setPadding(dpToPx(10), dpToPx(28), dpToPx(10), dpToPx(16));
    }

    private void updateLinesContainerVerticalPadding(int viewportHeight) {
        int horizMargin = dpToPx(20);
        int topPad = Math.max(dpToPx(24), Math.round(viewportHeight * 0.085f));
        int bottomPad = Math.max(dpToPx(260), Math.round(viewportHeight * 0.72f));
        if (linesContainer.getPaddingTop() != topPad || linesContainer.getPaddingBottom() != bottomPad) {
            linesContainer.setPadding(horizMargin, topPad, horizMargin, bottomPad);
        }
    }

    public void setOnLyricSeekListener(OnLyricSeekListener listener) {
        this.seekListener = listener;
    }

    public void setOnCloseListener(Runnable listener) {
        this.closeListener = listener;
    }

    public void setStatusBarInset(int insetPx) {
        int clamped = Math.max(0, insetPx);
        if (this.statusBarInsetPx == clamped) return;
        this.statusBarInsetPx = clamped;
        int padH = dpToPx(24);
        int padTop = clamped + dpToPx(12);
        int padBottom = dpToPx(12);
        headerLayout.setPadding(padH, padTop, padH, padBottom);
    }

    public void setUseLocalBackdrop(boolean useLocal) {
        if (this.useLocalBackdrop == useLocal) return;
        this.useLocalBackdrop = useLocal;
        int vis = useLocal ? VISIBLE : GONE;
        localBackdropScrim.setVisibility(vis);
        localBackdropImage.setVisibility(
                useLocal && currentCoverBitmap != null && !currentCoverBitmap.isRecycled()
                        ? VISIBLE
                        : GONE);
    }

    public void setLyricsTypeface(Typeface primary, Typeface secondary) {
        Typeface nextPrimary = primary != null ? primary : Typeface.DEFAULT_BOLD;
        Typeface nextSecondary = secondary != null ? secondary : nextPrimary;
        if (nextPrimary == lyricTypeface && nextSecondary == artistTypeface) return;
        this.lyricTypeface = nextPrimary;
        this.artistTypeface = nextSecondary;
        headerTitleView.setTypeface(nextPrimary);
        headerArtistView.setTypeface(nextSecondary);
        plainLyricsView.setTypeface(nextPrimary);
        statusView.setTypeface(nextSecondary);
        for (int i = 0; i < rowHolders.size(); i++) {
            RowHolder holder = rowHolders.get(i);
            if (holder.textView != null) {
                holder.textView.setTypeface(holder.line.isBackgroundVocal() ? nextSecondary : nextPrimary);
            }
        }
    }

    private void updateScreenPin() {
        setKeepScreenOn(screenPinned && pageOpen && isShown() && getWindowVisibility() == VISIBLE);
    }

    public void releaseScreenPin() {
        pageOpen = false;
        setKeepScreenOn(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateScreenPin();
    }

    @Override protected void onDetachedFromWindow() {
        setKeepScreenOn(false);
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateScreenPin();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateScreenPin();
    }

    public void prepareForOpen() {
        boolean alreadyVisible = getVisibility() == VISIBLE;
        animate().cancel();
        animate().setListener(null);
        if (!alreadyVisible) setAlpha(0f);
        setVisibility(View.VISIBLE);
        pageOpen = true;
        updateScreenPin();
        if (headerLayout != null) {
            headerLayout.animate().cancel();
            if (!alreadyVisible) {
                headerLayout.setAlpha(0f);
                headerLayout.setTranslationY(-dpToPx(14));
            }
        }
        if (contentContainer != null) {
            contentContainer.animate().cancel();
            if (!alreadyVisible) {
                contentContainer.setAlpha(0f);
                contentContainer.setTranslationY(dpToPx(34));
            }
        }
    }

    public void animateOpen() {
        prepareForOpen();
        scrollInitialized = false;
        lastScrolledAnchorIndex = -1;
        userInteracting = false;
        manualBrowseLocked = false;
        wasUserBrowsing = false;
        lastUserScrollUptimeMs = 0L;
        manualBrowseMix = 0f;

        animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                .setListener(null)
                .start();

        if (headerLayout != null) {
            headerLayout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }

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
        releaseScreenPin();
        animate().cancel();
        animate()
                .alpha(0f)
                .setDuration(210)
                .setInterpolator(new AccelerateInterpolator(1.35f))
                .setListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        cancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (cancelled) return;
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
        setTrackInfo(title, artist, cover, true);
    }

    public void setTrackInfo(String title, String artist, Bitmap cover, boolean isNewTrack) {
        Runnable apply = () -> {
            String nextTitle = title != null ? title : "";
            String nextArtist = artist != null ? artist : "";
            if (!TextUtils.equals(headerTitleView.getText(), nextTitle)) headerTitleView.setText(nextTitle);
            if (!TextUtils.equals(headerArtistView.getText(), nextArtist)) headerArtistView.setText(nextArtist);
            if (cover != null && !cover.isRecycled()) {
                applyCoverBitmap(cover);
            } else if (isNewTrack) {
                currentCoverBitmap = null;
                headerArtView.setImageDrawable(null);
                localBackdropImage.setImageDrawable(null);
                localBackdropImage.setVisibility(GONE);
            }
        };
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) post(apply);
        else apply.run();
    }

    public void setArtwork(Bitmap cover) {
        if (cover == null || cover.isRecycled()) return;
        Runnable apply = () -> applyCoverBitmap(cover);
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) post(apply);
        else apply.run();
    }

    private void applyCoverBitmap(Bitmap cover) {
        if (cover == null || cover.isRecycled()) return;
        if (cover == currentCoverBitmap && headerArtView.getDrawable() != null) return;
        boolean hadDrawable = headerArtView.getDrawable() != null;
        currentCoverBitmap = cover;
        headerArtView.setImageBitmap(cover);
        if (!hadDrawable) {
            headerArtView.setAlpha(0f);
            headerArtView.animate().alpha(1f).setDuration(200).start();
        }
        localBackdropImage.setImageBitmap(cover);
        if (useLocalBackdrop) {
            localBackdropImage.setVisibility(VISIBLE);
        }
    }

    public void setAutoScrollEnabled(boolean enabled) {
        if (autoScrollEnabled == enabled) return;
        this.autoScrollEnabled = enabled;
        if (!enabled) {
            scrollMotion.cancel();
            for (int i = 0; i < rowHolders.size(); i++) {
                RowHolder holder = rowHolders.get(i);
                holder.scrollLagY = holder.scrollLagStartY = 0f;
                holder.cascadeActive = false;
                holder.currentCascadeY = 0f;
                holder.liftY = 0f;
                holder.liftVelY = 0f;
                if (holder.interludeView != null) {
                    holder.view.setVisibility(GONE);
                } else {
                    holder.view.setRenderEffect(null);
                    holder.blurBucket = 0;
                    holder.blurDp = 0f;
                    holder.view.setAlpha(0.92f);
                    holder.view.setScaleX(LINE_SCALE_HIGHLIGHT);
                    holder.view.setScaleY(LINE_SCALE_HIGHLIGHT);
                    holder.view.setTranslationY(0f);
                    if (holder.textView != null) {
                        holder.textView.updateKaraoke(0f, 0f, 0f);
                    }
                }
            }
        }
    }

    public void setTextSizeSp(int sizeSp) {
        int size = Math.max(16, Math.min(38, sizeSp));
        if (this.textSizeSp == size) return;
        this.textSizeSp = size;
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        for (int i = 0; i < rowHolders.size(); i++) {
            RowHolder holder = rowHolders.get(i);
            if (holder.textView != null) {
                float rowSize = holder.line.isBackgroundVocal() ? textSizeSp * 0.74f : textSizeSp;
                holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, rowSize);
            }
        }
    }

    public void setLyrics(LyricsEntry entry) {
        if (lyricsAssigned && java.util.Objects.equals(displayedEntry, entry)) return;
        displayedEntry = entry;
        lyricsAssigned = true;
        isSynced = false;
        scrollMotion.cancel();
        lyricLines = new ArrayList<>();
        rowHolders.clear();
        linesContainer.removeAllViews();
        linesContainer.addView(plainLyricsView);
        primaryAnchorIndex = -1;
        lastScrolledAnchorIndex = -1;
        scrollInitialized = false;
        layoutCompensationArmed = false;
        pendingPostLayoutScroll = false;
        userInteracting = false;
        manualBrowseLocked = false;
        wasUserBrowsing = false;
        lastUserScrollUptimeMs = 0L;
        manualBrowseMix = 0f;

        if (entry == null || !entry.hasLyrics()) {
            statusView.setVisibility(VISIBLE);
            statusView.setText(entry != null && entry.notFound() ? "No lyrics found" : "Loading lyrics...");
            fadeContainer.setVisibility(GONE);
            attributionView.setVisibility(GONE);
            return;
        }

        statusView.setVisibility(GONE);
        fadeContainer.setVisibility(VISIBLE);
        attributionView.setVisibility(VISIBLE);

        List<LyricLine> parsedSynced = !entry.timedLines().isEmpty() ? entry.timedLines()
                : entry.hasSyncedLyrics() ? LrcParser.parse(entry.syncedLyrics()) : List.of();
        if (!parsedSynced.isEmpty()) {
            isSynced = true;
            lyricLines = LrcParser.withInterludes(parsedSynced);
            activeScratch = new boolean[lyricLines.size()];
            plainLyricsView.setVisibility(GONE);

            String src = "lrclib".equalsIgnoreCase(entry.source())
                    ? "LRCLIB \u2022 Synced"
                    : entry.source() + " \u2022 Synced";
            attributionView.setText(src);

            Context ctx = getContext();
            int innerPadH = dpToPx(10); // Glow + italic overhang safe margin (total 30dp with container 20dp)
            int backingPadStart = dpToPx(18);
            int padTop = dpToPx(4);
            int padBottom = dpToPx(5);
            int leadGapBottom = dpToPx(6);
            int backingGapBottom = dpToPx(4);
            boolean rtlTrack = detectTrackRtl(lyricLines);

            for (int i = 0; i < lyricLines.size(); i++) {
                LyricLine line = lyricLines.get(i);
                long nextVocalMs = findNextLyricTimestamp(lyricLines, i);
                if (line.isInterlude()) {
                    InterludeDotsView dotsView = new InterludeDotsView(ctx, rtlTrack);
                    dotsView.setVisibility(GONE);
                    dotsView.setBackground(createAppleLineRipple());
                    if (nextVocalMs >= 0L) {
                        final long seekTargetMs = nextVocalMs;
                        dotsView.setOnClickListener(v -> onLineTappedSeek(seekTargetMs));
                    }
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, dpToPx(44));
                    lp.bottomMargin = dpToPx(8);
                    linesContainer.addView(dotsView, lp);
                    rowHolders.add(new RowHolder(dotsView, line, null, dotsView, nextVocalMs));
                } else {
                    LyricLineView lineTv = new LyricLineView(ctx);
                    lineTv.setWordTimings(line.hasWordTimings());
                    lineTv.setText(line.text());
                    float size = line.isBackgroundVocal() ? textSizeSp * 0.74f : textSizeSp;
                    lineTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
                    lineTv.setTypeface(line.isBackgroundVocal() ? artistTypeface : lyricTypeface);
                    lineTv.setTextColor(Color.WHITE);
                    lineTv.setLetterSpacing(-0.02f);
                    lineTv.setLineSpacing(0f, 1.04f);
                    lineTv.setGravity(Gravity.START);
                    lineTv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
                    lineTv.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
                    lineTv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
                    lineTv.setBackground(createAppleLineRipple());

                    int startPad = line.isBackgroundVocal() ? backingPadStart : innerPadH;
                    lineTv.setPaddingRelative(startPad, padTop, innerPadH, padBottom);

                    float initialAlpha = line.isBackgroundVocal() ? ALPHA_UPCOMING_BACKING : ALPHA_UPCOMING_LEAD;
                    lineTv.setAlpha(line.hasWordTimings() ? 1f : initialAlpha);
                    lineTv.setScaleX(LINE_SCALE_NORMAL);
                    lineTv.setScaleY(LINE_SCALE_NORMAL);

                    final long lineTimestamp = line.timestampMs();
                    lineTv.setOnClickListener(v -> onLineTappedSeek(lineTimestamp));

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = line.isBackgroundVocal() ? backingGapBottom : leadGapBottom;
                    linesContainer.addView(lineTv, lp);
                    rowHolders.add(new RowHolder(lineTv, line, lineTv, null, nextVocalMs));
                }
            }

            linesContainer.addView(attributionView, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            scrollView.scrollTo(0, 0);
        } else if (entry.hasPlainLyrics()) {
            isSynced = false;
            plainLyricsView.setVisibility(VISIBLE);
            plainLyricsView.setText(entry.plainLyrics());
            String src = "lyrics.ovh".equalsIgnoreCase(entry.source()) ? "lyrics.ovh" : entry.source();
            attributionView.setText(src + " \u2022 Plain");
            linesContainer.addView(attributionView, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            scrollView.scrollTo(0, 0);
        } else {
            statusView.setVisibility(VISIBLE);
            statusView.setText("No lyrics found");
            fadeContainer.setVisibility(GONE);
            attributionView.setVisibility(GONE);
        }
    }

    private void onLineTappedSeek(long timestampMs) {
        userInteracting = false;
        manualBrowseLocked = false;
        lastUserScrollUptimeMs = 0L;
        if (seekListener != null) {
            seekListener.onSeek(timestampMs);
        }
    }

    /** Convenience single-step update (e.g. immediately after track load or seek). */
    public void updatePlaybackPosition(long positionMs) {
        updatePlaybackPosition(positionMs, true);
    }

    public void updatePlaybackPosition(long positionMs, boolean isPlaying) {
        stepFrame(positionMs, 0.016f, SystemClock.uptimeMillis(), isPlaying);
    }

    /** Backward-compatible 3-arg frame step delegating with {@code isPlaying = true}. */
    public void stepFrame(long positionMs, float dtSec, long nowUptimeMs) {
        stepFrame(positionMs, dtSec, nowUptimeMs, true);
    }

    /**
     * VSync-driven 120fps animation step running Apple Music's choreography:
     * dynamic lookahead, triangular staggered cascade scroll, 0.98f -> 1.00f scale,
     * spring vertical float lift, 30dp feathered karaoke sweep, emphasis glow,
     * concurrent overlapping lines, and pause-aware 3-dot instrumental interludes.
     */
    public void stepFrame(long positionMs, float dtSec, long nowUptimeMs, boolean isPlaying) {
        if (!isSynced || lyricLines.isEmpty() || rowHolders.isEmpty()) return;

        float dt = Math.max(0.001f, Math.min(0.05f, dtSec));
        float dtMs = dt * 1000f;
        int count = rowHolders.size();
        if (activeScratch.length < count) {
            activeScratch = new boolean[count];
        } else {
            for (int i = 0; i < count; i++) activeScratch[i] = false;
        }

        // Scroll anticipation must not advance the vocal highlight.
        long wordPosMs = positionMs;

        // 2. Dynamic line-advance lookahead (+480..750ms based on gap, or +1300ms exiting interlude)
        int rawIdx = LrcParser.findActiveLineIndex(lyricLines, positionMs);
        long lineLeadMs = LrcParser.computeLineAdvanceLeadMs(lyricLines, Math.max(0, rawIdx), false);
        long lineFocusPosMs = positionMs + lineLeadMs;

        // Reuse the frame buffer instead of allocating two lists on every VSYNC.
        for (int i = 0; i < count; i++) {
            LyricLine line = lyricLines.get(i);
            activeScratch[i] = line.isActiveAt(wordPosMs);
        }

        // Show intro interlude smoothly from t=0 before first line enters
        if (rawIdx < 0 && lyricLines.get(0).isInterlude()) {
            long nextVocal = rowHolders.get(0).nextVocalMs;
            if (nextVocal < 0L || positionMs < nextVocal - 280L) {
                activeScratch[0] = true;
            }
        }

        // Handle interlude visibility & exit handoff:
        // keep interlude visible through its 1.2x -> 0.5x exit pop, then collapse to GONE cleanly
        boolean anyVisibilityChanged = false;
        for (int i = 0; i < count; i++) {
            RowHolder holder = rowHolders.get(i);
            if (holder.interludeView == null) continue;

            LyricLine line = holder.line;
            long nextVocal = holder.nextVocalMs > 0L ? holder.nextVocalMs : line.endTimestampMs();
            long exitLeadMs = Math.min(
                    LrcParser.INTERLUDE_EXIT_LEAD_MS,
                    Math.max(600L, Math.round(line.durationMs() * 0.36f)));
            long collapseAtMs = nextVocal - 260L;
            boolean isIntroInterlude = (i == 0);
            boolean withinWindow = autoScrollEnabled
                    && (isIntroInterlude ? positionMs >= 0L : positionMs >= line.timestampMs() - 120L)
                    && positionMs < collapseAtMs;

            activeScratch[i] = withinWindow;
            int desiredVis = withinWindow ? VISIBLE : GONE;
            if (holder.view.getVisibility() != desiredVis) {
                if (!anyVisibilityChanged && scrollInitialized) {
                    snapshotLaidOutTops();
                    layoutCompensationArmed = true;
                }
                holder.view.setVisibility(desiredVis);
                anyVisibilityChanged = true;
                if (desiredVis == VISIBLE) {
                    holder.interludeView.resetForEntry();
                }
            }
            if (withinWindow) {
                holder.interludeView.updateAppleInterlude(
                        positionMs,
                        isIntroInterlude ? 0L : line.timestampMs(),
                        nextVocal,
                        exitLeadMs,
                        dtMs,
                        isPlaying);
            }
        }

        // 3. Choose scroll anchor (prefer incoming/latest active lead line; if inside interlude before
        //    its final 500ms handoff, anchor on the active interlude)
        int anchorIndex = -1;
        // Prefer latest active non-interlude lead line
        for (int i = count - 1; i >= 0; i--) {
            LyricLine line = lyricLines.get(i);
            if (line.isActiveAt(lineFocusPosMs) && !line.isInterlude() && !line.isBackgroundVocal()) {
                anchorIndex = i;
                break;
            }
        }
        // Fallback: active interlude (before its final 520ms handoff to the next verse)
        if (anchorIndex < 0) {
            for (int i = count - 1; i >= 0; i--) {
                if (activeScratch[i] && lyricLines.get(i).isInterlude()) {
                    long nextVocal = rowHolders.get(i).nextVocalMs;
                    if (nextVocal > 0L && (nextVocal - positionMs) <= LrcParser.DEFAULT_LINE_ADVANCE_LEAD_MS
                            && i + 1 < count) {
                        anchorIndex = i + 1;
                    } else {
                        anchorIndex = i;
                    }
                    break;
                }
            }
        }
        // Fallback: active backing vocal or binary-search nearest line
        if (anchorIndex < 0) {
            for (int i = count - 1; i >= 0; i--) {
                if (activeScratch[i]) {
                    anchorIndex = i;
                    break;
                }
            }
        }
        if (anchorIndex < 0) {
            int fallback = LrcParser.findActiveLineIndex(lyricLines, lineFocusPosMs);
            if (fallback < 0) fallback = 0;
            // Skip collapsed GONE interludes when selecting fallback anchor
            while (fallback < count && rowHolders.get(fallback).view.getVisibility() == GONE) {
                fallback++;
            }
            if (fallback >= count) {
                fallback = count - 1;
                while (fallback >= 0 && rowHolders.get(fallback).view.getVisibility() == GONE) {
                    fallback--;
                }
            }
            anchorIndex = fallback;
        }

        // 4. Apple Music manual-browse state:
        //    stay in browse mode while touching OR while scrolled away from active line (up to lockscreen hold)
        boolean holdElapsed = lastUserScrollUptimeMs <= 0L
                || (nowUptimeMs - lastUserScrollUptimeMs) >= MANUAL_SCROLL_HOLD_MS;
        if (!userInteracting && manualBrowseLocked && (isRowVisibleInViewport(anchorIndex) || holdElapsed)) {
            manualBrowseLocked = false;
        }
        boolean userBrowsing = userInteracting || manualBrowseLocked || (!holdElapsed && lastUserScrollUptimeMs > 0L);

        float targetBrowseMix = userBrowsing ? 1f : 0f;
        float browseK = 1f - (float) Math.exp(-10f * dt);
        manualBrowseMix += (targetBrowseMix - manualBrowseMix) * browseK;
        if (Math.abs(manualBrowseMix - targetBrowseMix) < 0.004f) manualBrowseMix = targetBrowseMix;

        // 5. Apple Music 750ms staggered triangular cascade scroll on anchor transition
        boolean resumeFromBrowse = wasUserBrowsing && !userBrowsing;
        wasUserBrowsing = userBrowsing;
        primaryAnchorIndex = anchorIndex;

        if (autoScrollEnabled && !userBrowsing && anchorIndex >= 0 && anchorIndex < count) {
            if (anyVisibilityChanged && scrollInitialized) {
                // Defer scroll adjustment until linesContainer finishes its post-visibility layout pass
                pendingPostLayoutScroll = true;
            } else if (!scrollInitialized || anchorIndex != lastScrolledAnchorIndex || resumeFromBrowse) {
                scrollToAnchorWithCascade(anchorIndex, !scrollInitialized);
            }
        }

        advanceViewportScroll(nowUptimeMs);

        // 6. Per-row Apple Music animation updates (scale 0.98<->1.00, alpha, spring lift, karaoke sweep, glow)
        float liftMaxPx = dpToPxFloat(2.0f); // Apple Music lyrics_karaoke_syllable_lift = -2dp
        float alphaSmoothK = 1f - (float) Math.exp(-12f * dt);

        for (int i = 0; i < count; i++) {
            RowHolder holder = rowHolders.get(i);
            View v = holder.view;
            if (v.getVisibility() == GONE) continue;

            // Advance staggered cascade translationY
            if (holder.cascadeActive) {
                holder.cascadeElapsedMs += dtMs;
                float elapsedAfterDelay = holder.cascadeElapsedMs - holder.cascadeDelayMs;
                if (elapsedAfterDelay <= 0f) {
                    holder.currentCascadeY = holder.cascadeFromY;
                } else if (elapsedAfterDelay >= holder.cascadeDurationMs) {
                    holder.currentCascadeY = 0f;
                    holder.cascadeFromY = 0f;
                    holder.cascadeActive = false;
                } else {
                    float t = elapsedAfterDelay / holder.cascadeDurationMs;
                    float eased = CASCADE_INTERPOLATOR.getInterpolation(t);
                    holder.currentCascadeY = holder.cascadeFromY * (1f - eased);
                }
            }

            if (holder.interludeView != null) {
                v.setTranslationY(holder.currentCascadeY + holder.scrollLagY);
                continue;
            }

            LyricLine line = holder.line;
            boolean isActive = activeScratch[i];

            // Trigger deterministic Apple Music scale & focus transitions on state flips
            if (!holder.stateInitialized) {
                holder.stateInitialized = true;
                holder.wasActive = isActive;
                holder.currentScale = isActive ? LINE_SCALE_HIGHLIGHT : LINE_SCALE_NORMAL;
                holder.scaleFrom = holder.currentScale;
                holder.scaleTo = holder.currentScale;
                holder.scaleElapsedMs = holder.scaleDurationMs;

                holder.currentFocus = isActive ? 1f : 0f;
                holder.focusFrom = holder.currentFocus;
                holder.focusTo = holder.currentFocus;
                holder.focusElapsedMs = holder.focusDurationMs;
            } else if (isActive != holder.wasActive) {
                holder.wasActive = isActive;
                holder.scaleFrom = holder.currentScale;
                holder.scaleTo = isActive ? LINE_SCALE_HIGHLIGHT : LINE_SCALE_NORMAL;
                holder.scaleElapsedMs = 0f;
                holder.scaleDelayMs = isActive ? SCALE_IN_DELAY_MS : SCALE_OUT_DELAY_MS;
                holder.scaleDurationMs = isActive ? SCALE_IN_DURATION_MS : SCALE_OUT_DURATION_MS;

                holder.focusFrom = holder.currentFocus;
                holder.focusTo = isActive ? 1f : 0f;
                holder.focusElapsedMs = 0f;
                holder.focusDelayMs = isActive ? ALPHA_IN_DELAY_MS : ALPHA_OUT_DELAY_MS;
                holder.focusDurationMs = isActive ? ALPHA_IN_DURATION_MS : ALPHA_OUT_DURATION_MS;
            }

            // Evaluate Apple Music scale curve (0.98f <-> 1.00f)
            if (holder.scaleElapsedMs < holder.scaleDelayMs + holder.scaleDurationMs) {
                holder.scaleElapsedMs += dtMs;
                float sAfter = holder.scaleElapsedMs - holder.scaleDelayMs;
                if (sAfter <= 0f) {
                    holder.currentScale = holder.scaleFrom;
                } else if (sAfter >= holder.scaleDurationMs) {
                    holder.currentScale = holder.scaleTo;
                } else {
                    float se = CASCADE_INTERPOLATOR.getInterpolation(sAfter / holder.scaleDurationMs);
                    holder.currentScale = holder.scaleFrom + (holder.scaleTo - holder.scaleFrom) * se;
                }
            }

            // Evaluate Apple Music alpha/focus curve (0f <-> 1f)
            if (holder.focusElapsedMs < holder.focusDelayMs + holder.focusDurationMs) {
                holder.focusElapsedMs += dtMs;
                float fAfter = holder.focusElapsedMs - holder.focusDelayMs;
                if (fAfter <= 0f) {
                    holder.currentFocus = holder.focusFrom;
                } else if (fAfter >= holder.focusDurationMs) {
                    holder.currentFocus = holder.focusTo;
                } else {
                    float fe = ALPHA_INTERPOLATOR.getInterpolation(fAfter / holder.focusDurationMs);
                    holder.currentFocus = holder.focusFrom + (holder.focusTo - holder.focusFrom) * fe;
                }
            }

            // Exact karaoke word progress and emphasis glow at audio-synced wordPosMs
            float progress;
            float emphasisGlow;
            if (wordPosMs < line.timestampMs()) {
                progress = 0f;
                emphasisGlow = 0f;
            } else if (wordPosMs >= line.endTimestampMs()) {
                // Fade the last displayed fill instead of flashing the entire line white.
                progress = holder.lastSungProgress;
                emphasisGlow = 0f;
            } else {
                progress = line.progressAt(wordPosMs);
                emphasisGlow = line.emphasisGlowAt(wordPosMs);
            }
            if (isActive) holder.lastSungProgress = progress;

            // Exact Apple Music spring float lift (-2dp while actively singing, 0dp at rest)
            boolean singingNow = isActive && progress > 0.005f && progress < 0.995f;
            float liftTarget = autoScrollEnabled && singingNow ? -liftMaxPx : 0f;
            if (Math.abs(liftTarget - holder.liftY) > 0.02f || Math.abs(holder.liftVelY) > 0.05f) {
                float accel = LIFT_SPRING_STIFFNESS * (liftTarget - holder.liftY)
                        - (2f * LIFT_SPRING_DAMPING * LIFT_SPRING_OMEGA) * holder.liftVelY;
                holder.liftVelY += accel * dt;
                holder.liftY += holder.liftVelY * dt;
                if (Math.abs(liftTarget - holder.liftY) < 0.02f && Math.abs(holder.liftVelY) < 0.05f) {
                    holder.liftY = liftTarget;
                    holder.liftVelY = 0f;
                }
            }

            // Browsing lifts inactive lines enough to read without flattening the active line.
            float inactiveAlpha;
            boolean isPast = positionMs >= line.endTimestampMs();
            if (line.isBackgroundVocal()) {
                inactiveAlpha = isPast ? ALPHA_PAST_BACKING : ALPHA_UPCOMING_BACKING;
            } else {
                inactiveAlpha = isPast ? ALPHA_PAST_LEAD : ALPHA_UPCOMING_LEAD;
            }
            float activeAlpha = line.isBackgroundVocal() ? ALPHA_ACTIVE_BACKING : ALPHA_ACTIVE_LEAD;

            float baseAlpha;
            if (!autoScrollEnabled) {
                baseAlpha = 0.92f;
            } else if (line.hasWordTimings()) {
                float browseAlpha = isActive ? activeAlpha : ALPHA_MANUAL_BROWSE;
                baseAlpha = inactiveAlpha + (Math.max(inactiveAlpha, browseAlpha) - inactiveAlpha)
                        * manualBrowseMix;
            } else {
                baseAlpha = inactiveAlpha + (activeAlpha - inactiveAlpha) * holder.currentFocus;
                float browseAlpha = isActive ? activeAlpha : ALPHA_MANUAL_BROWSE;
                baseAlpha = baseAlpha + (browseAlpha - baseAlpha) * manualBrowseMix;
            }
            holder.currentAlpha += (baseAlpha - holder.currentAlpha) * alphaSmoothK;
            if (Math.abs(holder.currentAlpha - baseAlpha) < 0.003f) {
                holder.currentAlpha = baseAlpha;
            }

            // Word glyphs own their opacity. Applying another whole-view fade changes
            // the unsung baseline and produces a brightness pulse at line handoff.
            v.setAlpha(line.hasWordTimings() ? 1f : holder.currentAlpha);

            // Pivot at start padding (or right padding for RTL) so 0.98 <-> 1.00 stays flush & clip-proof
            boolean rtlLine = holder.textView.isRtlParagraph();
            float pivotX = rtlLine ? (v.getWidth() - v.getPaddingRight()) : v.getPaddingLeft();
            v.setPivotX(pivotX);
            v.setPivotY(v.getHeight() * 0.5f);

            float appliedScale = autoScrollEnabled
                    ? (holder.currentScale + ((isActive ? LINE_SCALE_HIGHLIGHT : 0.96f)
                            - holder.currentScale) * manualBrowseMix)
                    : LINE_SCALE_HIGHLIGHT;
            v.setScaleX(appliedScale);
            v.setScaleY(appliedScale);
            v.setTranslationY(holder.currentCascadeY + holder.scrollLagY + holder.liftY);
            updateLineBlur(holder, i, rawIdx >= 0 ? rawIdx : anchorIndex, isActive, dt);

            holder.textView.updateKaraoke(
                    autoScrollEnabled ? holder.currentFocus : 0f,
                    progress,
                    emphasisGlow,
                    holder.currentAlpha);
        }
    }

    private void updateLineBlur(RowHolder holder, int index, int focusIndex,
            boolean active, float dt) {
        float distance = Math.abs(index - focusIndex);
        float target = autoScrollEnabled && !active
                ? Math.min(4f, Math.max(0f, distance - 0.25f) * 0.9f)
                        * (1f - holder.currentFocus) * (1f - 0.75f * manualBrowseMix)
                : 0f;
        holder.blurDp += (target - holder.blurDp) * (1f - (float) Math.exp(-10f * dt));
        if (Math.abs(holder.blurDp - target) < 0.02f) holder.blurDp = target;
        int bucket = Math.max(0, Math.min(MAX_BLUR_BUCKET, Math.round(holder.blurDp / BLUR_STEP_DP)));

        // Keep GPU blur layers bounded to the viewport, including room for the feather.
        View row = holder.view;
        float top = row.getTop() + row.getTranslationY() - scrollView.getScrollY();
        int margin = dpToPx(24);
        if (top + row.getHeight() < -margin || top > scrollView.getHeight() + margin) bucket = 0;
        if (holder.blurBucket == bucket) return;
        holder.blurBucket = bucket;
        if (bucket > 0 && lineBlurEffects[bucket] == null) {
            float radius = dpToPxFloat(bucket * BLUR_STEP_DP);
            lineBlurEffects[bucket] = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL);
        }
        row.setRenderEffect(lineBlurEffects[bucket]);
    }

    private void scrollToAnchorWithCascade(int anchorIndex, boolean snapImmediate) {
        int count = rowHolders.size();
        int scrollHeight = scrollView.getHeight();
        if (anchorIndex < 0 || anchorIndex >= count || scrollHeight <= 0) return;
        View anchorView = rowHolders.get(anchorIndex).view;
        int contentHeight = linesContainer.getHeight();
        if (contentHeight <= 0 || anchorView.getVisibility() != VISIBLE || anchorView.getHeight() <= 0) {
            return;
        }
        int anchorTopOffset = Math.max(dpToPx(24), Math.round(scrollHeight * 0.085f));
        int maxScrollY = Math.max(0, contentHeight - scrollHeight);
        int targetScrollY = Math.max(0, Math.min(maxScrollY, anchorView.getTop() - anchorTopOffset));

        lastScrolledAnchorIndex = anchorIndex;
        if (snapImmediate) {
            scrollMotion.cancel();
            scrollInitialized = true;
            for (RowHolder holder : rowHolders) {
                holder.scrollLagY = holder.scrollLagStartY = 0f;
                holder.cascadeActive = false;
                holder.currentCascadeY = 0f;
                holder.cascadeFromY = 0f;
                holder.view.setTranslationY(holder.liftY);
            }
            scrollView.scrollTo(0, targetScrollY);
            snapshotLaidOutTops();
            return;
        }

        int currentScrollY = scrollView.getScrollY();
        int deltaY = targetScrollY - currentScrollY;
        if (Math.abs(deltaY) < 1) return;

        boolean hugeJump = Math.abs(deltaY) > scrollHeight * 1.6f;
        float durMs = hugeJump ? SEEK_SETTLE_DURATION_MS : CASCADE_DURATION_MS;
        scrollMotion.begin(currentScrollY, targetScrollY, SystemClock.uptimeMillis(), durMs);
        for (int i = 0; i < count; i++) {
            RowHolder h = rowHolders.get(i);
            h.scrollLagStartY = h.scrollLagY;
            h.scrollDelayMs = hugeJump
                    ? 0L
                    : LrcParser.computeCascadeDelayMs(i, anchorIndex);
        }
    }

    private void advanceViewportScroll(long now) {
        if (!scrollMotion.active()) return;
        scrollView.scrollTo(0, scrollMotion.position(now));
        long longestDelay = 0L;
        for (RowHolder holder : rowHolders) {
            holder.scrollLagY = scrollMotion.rowOffset(now, holder.scrollDelayMs, holder.scrollLagStartY);
            longestDelay = Math.max(longestDelay, holder.scrollDelayMs);
        }
        if (scrollMotion.finished(now, longestDelay)) {
            scrollMotion.cancel();
            for (RowHolder holder : rowHolders) holder.scrollLagY = holder.scrollLagStartY = 0f;
        }
    }

    /**
     * Absorbs average visible cascade offset into {@code scrollView} and settles residual per-row
     * offsets cleanly when the user grabs the list manually.
     */
    private void settleCascadeForManualDrag() {
        scrollMotion.cancel();
        int count = rowHolders.size();
        if (count == 0) return;
        for (int i = 0; i < count; i++) {
            RowHolder h = rowHolders.get(i);
            h.currentCascadeY += h.scrollLagY;
            h.scrollLagY = h.scrollLagStartY = 0f;
            h.startCascade(0f, 0L, 140f);
        }
    }

    private void snapshotLaidOutTops() {
        for (int i = 0; i < rowHolders.size(); i++) {
            RowHolder h = rowHolders.get(i);
            if (h.view.getVisibility() == VISIBLE) {
                h.lastLaidOutTop = h.view.getTop();
            }
        }
    }

    /**
     * Replicates RecyclerView predictive {@code animateMove} when an interlude row expands or
     * collapses, compensating each shifted sibling's {@code translationY} so there is zero snap.
     */
    private void applyInterludeLayoutCompensation() {
        for (int i = 0; i < rowHolders.size(); i++) {
            RowHolder h = rowHolders.get(i);
            if (h.view.getVisibility() != VISIBLE) continue;
            int newTop = h.view.getTop();
            if (h.lastLaidOutTop != Integer.MIN_VALUE) {
                int shift = newTop - h.lastLaidOutTop;
                if (Math.abs(shift) >= 2 && Math.abs(shift) < dpToPx(240)) {
                    long delay = LrcParser.computeCascadeDelayMs(i, primaryAnchorIndex);
                    h.startCascade(-shift, delay, CASCADE_DURATION_MS);
                }
            }
            h.lastLaidOutTop = newTop;
        }
    }

    private boolean isRowVisibleInViewport(int index) {
        if (index < 0 || index >= rowHolders.size()) return true;
        View row = rowHolders.get(index).view;
        if (row.getVisibility() != VISIBLE || scrollView.getHeight() <= 0) return true;
        int sy = scrollView.getScrollY();
        int sh = scrollView.getHeight();
        return row.getBottom() >= sy && row.getTop() <= (sy + sh);
    }

    private RippleDrawable createAppleLineRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dpToPxFloat(16f)); // Apple Music lyrics_ripple_corner_radius = 16dp
        return new RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), null, mask);
    }

    private static boolean detectTrackRtl(List<LyricLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (line.isInterlude() || line.text().isEmpty()) continue;
            String t = line.text();
            for (int c = 0; c < t.length(); c++) {
                byte dir = Character.getDirectionality(t.charAt(c));
                if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                        || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                    return true;
                }
                if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                    return false;
                }
            }
        }
        return false;
    }

    private static long findNextLyricTimestamp(List<LyricLine> lines, int index) {
        for (int i = index + 1; i < lines.size(); i++) {
            if (!lines.get(i).isInterlude()) {
                return lines.get(i).timestampMs();
            }
        }
        return -1L;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return event != null && event.getY() >= statusBarInsetPx;
    }

    private int dpToPx(int dp) {
        return Math.round(dpToPxFloat(dp));
    }

    private float dpToPxFloat(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private static final class HeaderActionButton extends View {
        private final boolean pin;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path pinPath = new Path();

        HeaderActionButton(Context context, boolean pin) {
            super(context);
            this.pin = pin;
            setClickable(true);
            setFocusable(true);
            iconPaint.setStrokeWidth(1.65f);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStyle(Paint.Style.STROKE);
            pinPath.moveTo(8f, 3.5f);
            pinPath.lineTo(16f, 3.5f);
            pinPath.moveTo(9f, 3.5f);
            pinPath.lineTo(9f, 9f);
            pinPath.lineTo(6.5f, 12.5f);
            pinPath.lineTo(17.5f, 12.5f);
            pinPath.lineTo(15f, 9f);
            pinPath.lineTo(15f, 3.5f);
            pinPath.moveTo(12f, 12.5f);
            pinPath.lineTo(12f, 20.5f);
            setPinned(false);
            setOnTouchListener((v, event) -> {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }

        void setPinned(boolean pinned) {
            setSelected(pinned);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(pinned ? 0xEFFFFFFF : 0x28FFFFFF);
            RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x35FFFFFF), circle, null);
            int inset = Math.round(4f * getResources().getDisplayMetrics().density);
            setBackground(new InsetDrawable(ripple, inset));
            if (pin) setStateDescription(pinned ? "On" : "Off");
            invalidate();
        }

        @Override public CharSequence getAccessibilityClassName() {
            return pin ? "android.widget.ToggleButton" : "android.widget.Button";
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (pin) {
                info.setCheckable(true);
                info.setChecked(isSelected());
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = 20f * getResources().getDisplayMetrics().density;
            int save = canvas.save();
            canvas.translate((getWidth() - size) * 0.5f, (getHeight() - size) * 0.5f);
            canvas.scale(size / 24f, size / 24f);
            iconPaint.setColor(isSelected() ? 0xFF222226 : Color.WHITE);
            if (pin) {
                if (!isSelected()) canvas.rotate(35f, 12f, 12f);
                canvas.drawPath(pinPath, iconPaint);
            } else {
                canvas.drawLine(7f, 7f, 17f, 17f, iconPaint);
                canvas.drawLine(17f, 7f, 7f, 17f, iconPaint);
            }
            canvas.restoreToCount(save);
        }
    }

    /** Fades viewport edges without compositing the scrolling text into a separate layer. */
    private static final class AlphaFadeFrameLayout extends FrameLayout {
        private static final int[] FADE_COLORS = {
                0x00000000, 0xFF000000, 0xFF000000, 0x99000000, 0x4D000000, 0x00000000};
        private Shader edgeMask;
        private boolean masksDirty = true;

        AlphaFadeFrameLayout(Context context) {
            super(context);
            setClipChildren(true);
            setClipToPadding(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w != oldw || h != oldh) {
                masksDirty = true;
            }
        }

        private void rebuildMasksIfNeeded(int w, int h) {
            if (!masksDirty || w <= 0 || h <= 0) return;
            masksDirty = false;
            float density = getResources().getDisplayMetrics().density;
            float topFade = Math.min(h / 3f, Math.max(24f * density, h * 0.07f));
            float bottomFade = Math.min(h / 2f, Math.max(56f * density, h * 0.26f));
            float bottomTop = h - bottomFade;
            edgeMask = new LinearGradient(0f, 0f, 0f, h, FADE_COLORS,
                    new float[]{0f, topFade / h, bottomTop / h,
                            (bottomTop + bottomFade * 0.30f) / h,
                            (bottomTop + bottomFade * 0.60f) / h, 1f}, Shader.TileMode.CLAMP);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (getVisibility() == GONE || w <= 0 || h <= 0) {
                super.dispatchDraw(canvas);
                return;
            }
            rebuildMasksIfNeeded(w, h);
            int save = canvas.save();
            try {
                canvas.clipShader(edgeMask);
                super.dispatchDraw(canvas);
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    /**
     * Clean-room Apple Music 3-dot {@code ...} instrumental interlude view:
     * <ul>
     *   <li>10dp dots, 6dp horizontal gap (16dp center-to-center), aligned with lyric text start.</li>
     *   <li>Staggered entry fade (750ms, 0/50/100ms offsets, AccelerateInterpolator).</li>
     *   <li>Sequential fill ({@code PathInterpolator(0, 0.25, 1, 0.58)}) with final 750ms linear dot-3 ramp.</li>
     *   <li>Integer-cycle ~4000ms sinusoidal breathing pulse ({@code 1.0f -> 1.2f -> 1.0f}) that pauses cleanly.</li>
     *   <li>Anticipatory exit pop: swell {@code -> 1.2f} (750ms) then shrink {@code 1.2f -> 0.5f} &amp; fade {@code -> 0f} (250ms).</li>
     * </ul>
     */
    public static final class InterludeDotsView extends View {
        private static final int ALPHA_DOT_DIM = 82;    // ~32% translucent white
        private static final int ALPHA_DOT_LIT = 245;   // ~96% vibrant white

        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean rtl;

        private float entryElapsedMs = 0f;
        private float breathClockMs = 0f;
        private float dotFill0 = 0f;
        private float dotFill1 = 0f;
        private float dotFill2 = 0f;
        private float groupScale = 1.0f;
        private float groupAlpha = 1.0f;
        private float exitStartScale = 1.0f;
        private boolean inExitPhase = false;

        public InterludeDotsView(Context context) {
            this(context, false);
        }

        public InterludeDotsView(Context context, boolean rtl) {
            super(context);
            this.rtl = rtl;
            dotPaint.setStyle(Paint.Style.FILL);
            setClickable(true);
            setFocusable(true);
        }

        void resetForEntry() {
            entryElapsedMs = 0f;
            breathClockMs = 0f;
            dotFill0 = 0f;
            dotFill1 = 0f;
            dotFill2 = 0f;
            groupScale = 1.0f;
            groupAlpha = 1.0f;
            exitStartScale = 1.0f;
            inExitPhase = false;
            invalidate();
        }

        void updateAppleInterlude(
                long positionMs,
                long startMs,
                long nextVocalMs,
                long exitLeadMs,
                float dtMs,
                boolean isPlaying) {
            long totalSpanMs = Math.max(1200L, nextVocalMs - startMs);
            long elapsedMs = Math.max(0L, positionMs - startMs);
            long remainingMs = Math.max(0L, nextVocalMs - positionMs);

            if (isPlaying) {
                entryElapsedMs = Math.min(1000f, entryElapsedMs + dtMs);
                breathClockMs += dtMs;
            } else if (entryElapsedMs < 1f) {
                // Ensure dots are visible immediately when paused inside an interlude
                entryElapsedMs = 1000f;
            }

            // Active fill window ends 800ms before next vocal (Apple Music: w = gap - 800)
            float activeWindowMs = Math.max(600f, totalSpanMs - 800f);
            float segmentMs = Math.max(200f, (activeWindowMs + 750f) / 3.0f);

            dotFill0 = evalDotFill(elapsedMs, 0f, segmentMs);
            dotFill1 = evalDotFill(elapsedMs, segmentMs, segmentMs);

            // Dot 3 fills to partial ratio during activeWindowMs, then completes linearly over 750ms exit swell
            float dot2Start = segmentMs * 2f;
            float dot2MainDur = Math.max(100f, activeWindowMs - dot2Start);
            float dot2Partial = Math.min(1f, dot2MainDur / segmentMs);
            if (elapsedMs <= dot2Start) {
                dotFill2 = 0f;
            } else if (elapsedMs <= activeWindowMs) {
                float t = (elapsedMs - dot2Start) / dot2MainDur;
                dotFill2 = DOT_FILL_INTERPOLATOR.getInterpolation(clamp01(t)) * dot2Partial;
            } else {
                float tailT = clamp01((elapsedMs - activeWindowMs) / 750f);
                dotFill2 = dot2Partial + (1f - dot2Partial) * tailT;
            }

            // Breathing pulse vs anticipatory exit pop (1.2x swell -> 0.5x shrink + alpha 0)
            float swellDurMs = Math.min(750f, Math.max(220f, (exitLeadMs - 260f) * 0.75f));
            float shrinkDurMs = Math.min(250f, Math.max(120f, (exitLeadMs - 260f) * 0.25f));
            float exitTotalMs = swellDurMs + shrinkDurMs;
            float exitStartRemainingMs = 260f + exitTotalMs;

            if (remainingMs <= exitStartRemainingMs) {
                if (!inExitPhase) {
                    inExitPhase = true;
                    exitStartScale = groupScale;
                }
                float exitElapsed = exitStartRemainingMs - remainingMs;
                if (exitElapsed <= swellDurMs) {
                    float t = clamp01(exitElapsed / swellDurMs);
                    float e = EASE_INTERPOLATOR.getInterpolation(t);
                    groupScale = exitStartScale + (1.2f - exitStartScale) * e;
                    groupAlpha = 1f;
                } else {
                    float t = clamp01((exitElapsed - swellDurMs) / shrinkDurMs);
                    float e = DOT_EXIT_SHRINK_INTERPOLATOR.getInterpolation(t);
                    groupScale = 1.2f + (0.5f - 1.2f) * e;
                    groupAlpha = 1f - e;
                }
            } else {
                inExitPhase = false;
                int cycles = Math.max(1, (int) (activeWindowMs / 4000f));
                float periodMs = Math.max(1600f, activeWindowMs / cycles);
                float phase = (breathClockMs % periodMs) / periodMs;
                // Analytical Apple Music double-ease breathing curve (1.0f -> 1.2f -> 1.0f)
                float breath = 0.5f - 0.5f * (float) Math.cos(2.0 * Math.PI * phase);
                groupScale = 1.0f + 0.2f * breath;
                groupAlpha = 1f;
            }

            invalidate();
        }

        private static float evalDotFill(float elapsedMs, float startMs, float durMs) {
            if (elapsedMs <= startMs) return 0f;
            if (elapsedMs >= startMs + durMs) return 1f;
            return DOT_FILL_INTERPOLATOR.getInterpolation((elapsedMs - startMs) / durMs);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (groupAlpha <= 0.005f) return;
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            float radius = dpToPx(5f);       // 10dp diameter (lyrics_instrumental_dot_size)
            float step = dpToPx(16f);        // 10dp dot + 6dp margin (lyrics_instrumental_dot_margin_horiz)
            float insetX = dpToPx(10f);
            float cy = h * 0.5f;
            float pivotX = rtl ? (w - insetX) : insetX;

            canvas.save();
            canvas.scale(groupScale, groupScale, pivotX, cy);

            for (int i = 0; i < 3; i++) {
                float fill = (i == 0) ? dotFill0 : (i == 1 ? dotFill1 : dotFill2);
                float entryT = clamp01((entryElapsedMs - i * 50f) / 750f);
                float entryAlpha = DOT_ENTRY_INTERPOLATOR.getInterpolation(entryT);
                int baseAlpha = Math.round(ALPHA_DOT_DIM + (ALPHA_DOT_LIT - ALPHA_DOT_DIM) * fill);
                int finalAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * entryAlpha * groupAlpha)));

                float cx = rtl
                        ? (w - insetX - radius - i * step)
                        : (insetX + radius + i * step);
                dotPaint.setColor(Color.argb(finalAlpha, 255, 255, 255));
                canvas.drawCircle(cx, cy, radius, dotPaint);
            }

            canvas.restore();
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }
    }

    /**
     * Custom TextView rendering Apple Music's 30dp feathered karaoke gradient sweep
     * and 5dp white emphasis glow with zero per-frame allocations and strict horizontal
     * bounds safety.
     */
    public static class LyricLineView extends TextView {
        private static final int ALPHA_UNSUNG_ON_ACTIVE = 98; // ~38% translucent white

        // Reusable 4-stop Apple Music horizontal feather gradient (allocated once)
        private final LinearGradient sweepGradient = new LinearGradient(
                0f, 0f, 1f, 0f,
                new int[]{0xFFFFFFFF, 0xF5FFFFFF, 0x55FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.72f, 1f},
                Shader.TileMode.CLAMP);
        private final Matrix sweepMatrix = new Matrix();

        private float focus = 0f;
        private float progress = 0f;
        private float emphasisGlow = 0f;
        private boolean wordTimings = true;
        private float baseOpacity = 0.38f;

        public LyricLineView(Context context) {
            super(context);
            // Match Apple's smooth-scaling text setup before measuring the line.
            // Keep fractional motion from switching between hinted glyph instances.
            setPaintFlags(getPaintFlags() | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        }

        public void setWordTimings(boolean enabled) {
            wordTimings = enabled;
        }

        public void updateKaraoke(float nextFocus, float nextProgress, float nextEmphasisGlow) {
            updateKaraoke(nextFocus, nextProgress, nextEmphasisGlow, baseOpacity);
        }

        public void updateKaraoke(float nextFocus, float nextProgress, float nextEmphasisGlow,
                float nextBaseOpacity) {
            float f = Math.max(0f, Math.min(1f, nextFocus));
            float p = Math.max(0f, Math.min(1f, nextProgress));
            float g = Math.max(0f, Math.min(1f, nextEmphasisGlow));
            float b = Math.max(0f, Math.min(1f, nextBaseOpacity));
            if (Math.abs(f - focus) < 0.002f
                    && Math.abs(p - progress) < 0.001f
                    && Math.abs(g - emphasisGlow) < 0.005f
                    && Math.abs(b - baseOpacity) < 0.002f) {
                return;
            }
            this.focus = f;
            this.progress = p;
            this.emphasisGlow = g;
            this.baseOpacity = b;
            invalidate();
        }

        public void updateKaraoke(float nextFocus, float nextProgress) {
            updateKaraoke(nextFocus, nextProgress, 0f);
        }

        public void setActive(boolean active) {
            updateKaraoke(active ? 1f : 0f, active ? progress : 0f, 0f);
        }

        public void setProgress(float nextProgress) {
            updateKaraoke(focus, nextProgress, emphasisGlow);
        }

        boolean isRtlParagraph() {
            Layout layout = getLayout();
            return layout != null && layout.getLineCount() > 0
                    && layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // Apple's line-synced renderer highlights whole lines. A word sweep
            // is only meaningful when the source actually supplies word timestamps.
            if (!wordTimings) {
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
            int padBottom = getCompoundPaddingBottom();
            int viewW = getWidth();
            float glowRadiusPx = dpToPx(5f); // Apple Music lyrics_karaoke_emphasis_shadow_radius = 5dp
            int glowAlpha = Math.round(128f * emphasisGlow * focus);

            try {
                // Stable unsung text; only the foreground karaoke mask follows focus.
                int baseAlpha = Math.round(255f * baseOpacity);
                canvas.save();
                canvas.translate(padLeft, padTop);
                paint.setShader(null);
                paint.clearShadowLayer();
                paint.setColor(Color.argb(baseAlpha, 255, 255, 255));
                layout.draw(canvas);
                canvas.restore();

                int sungAlpha = Math.round(255f * focus);
                if (progress <= 0.001f || sungAlpha <= 1) {
                    return;
                }

                int sungColor = Color.argb(sungAlpha, 255, 255, 255);
                if (glowAlpha >= 4) {
                    paint.setShadowLayer(glowRadiusPx, 0f, 0f, Color.argb(glowAlpha, 255, 255, 255));
                }

                if (progress >= 0.999f) {
                    canvas.save();
                    canvas.translate(padLeft, padTop);
                    paint.setShader(null);
                    paint.setColor(sungColor);
                    layout.draw(canvas);
                    canvas.restore();
                    return;
                }

                CharSequence text = getText();
                int totalChars = text != null ? text.length() : 0;
                if (totalChars == 0) return;

                float charPos = progress * totalChars;
                int activeChar = Math.min(totalChars - 1, Math.max(0, (int) charPos));
                int activeLine = layout.getLineForOffset(activeChar);

                // Bounds stay strictly within view container [0..viewW] (expressed in translated coords)
                float maxClipLeft = -padLeft;
                float maxClipRight = (viewW - padLeft);

                // 2a. Completed wrapped sub-lines above activeLine -> solid bright white
                if (activeLine > 0) {
                    canvas.save();
                    canvas.translate(padLeft, padTop);
                    canvas.clipRect(
                            maxClipLeft,
                            layout.getLineTop(0) - padTop,
                            maxClipRight,
                            layout.getLineTop(activeLine));
                    paint.setShader(null);
                    paint.setColor(sungColor);
                    layout.draw(canvas);
                    canvas.restore();
                }

                // 2b. Active wrapped sub-line -> Apple Music 30dp feathered gradient sweep
                int lineStart = layout.getLineStart(activeLine);
                int lineEnd = layout.getLineEnd(activeLine);
                while (lineEnd > lineStart && Character.isWhitespace(text.charAt(lineEnd - 1))) {
                    lineEnd--;
                }
                float lineLeft = layout.getLineLeft(activeLine);
                float lineRight = layout.getLineRight(activeLine);
                float lineSpan = Math.max(1f, lineRight - lineLeft);
                // Apple Music lyrics_karaoke_gradient_feather_width = 30dp (clamped for tiny lines)
                float featherWidth = Math.min(dpToPx(30f), Math.max(dpToPx(12f), lineSpan * 0.48f));
                boolean rtl = layout.getParagraphDirection(activeLine) == Layout.DIR_RIGHT_TO_LEFT;
                // Follow shaped glyph positions rather than assuming equal-width characters.
                int offset = Math.max(lineStart, Math.min(lineEnd, activeChar));
                int nextOffset = Math.min(lineEnd, offset + 1);
                if (offset > lineStart && offset < totalChars
                        && Character.isLowSurrogate(text.charAt(offset))) offset--;
                if (nextOffset < lineEnd && Character.isLowSurrogate(text.charAt(nextOffset))) nextOffset++;
                float edge = rtl ? lineLeft : lineRight;
                float fromX = offset >= lineEnd ? edge : layout.getPrimaryHorizontal(offset);
                float toX = nextOffset >= lineEnd ? edge : layout.getPrimaryHorizontal(nextOffset);
                float fraction = Math.max(0f, Math.min(1f,
                        (charPos - offset) / Math.max(1, nextOffset - offset)));
                float caretX = fromX + (toX - fromX) * fraction;
                float lineFrac = Math.max(0f, Math.min(1f,
                        (rtl ? lineRight - caretX : caretX - lineLeft) / lineSpan));

                sweepMatrix.reset();
                float clipLeft;
                float clipRight;
                if (!rtl) {
                    float x0 = (lineLeft - featherWidth) + (lineSpan + featherWidth) * lineFrac;
                    float x1 = x0 + featherWidth;
                    sweepMatrix.setScale(featherWidth, 1f);
                    sweepMatrix.postTranslate(x0, 0f);
                    clipLeft = maxClipLeft;
                    clipRight = Math.min(maxClipRight, x1 + glowRadiusPx);
                } else {
                    float x0 = (lineRight + featherWidth) - (lineSpan + featherWidth) * lineFrac;
                    float x1 = x0 - featherWidth;
                    sweepMatrix.setScale(-featherWidth, 1f);
                    sweepMatrix.postTranslate(x0, 0f);
                    clipLeft = Math.max(maxClipLeft, x1 - glowRadiusPx);
                    clipRight = maxClipRight;
                }
                sweepGradient.setLocalMatrix(sweepMatrix);

                canvas.save();
                canvas.translate(padLeft, padTop);
                canvas.clipRect(
                        clipLeft,
                        activeLine == 0 ? -padTop : layout.getLineTop(activeLine),
                        clipRight,
                        activeLine == layout.getLineCount() - 1
                                ? layout.getHeight() + padBottom : layout.getLineTop(activeLine + 1));
                paint.setColor(sungColor);
                paint.setShader(sweepGradient);
                layout.draw(canvas);
                canvas.restore();
            } finally {
                paint.clearShadowLayer();
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
