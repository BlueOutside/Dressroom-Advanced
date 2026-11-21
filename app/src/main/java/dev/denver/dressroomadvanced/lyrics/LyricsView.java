package dev.denver.dressroomadvanced.lyrics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
 * and large, bold, left-aligned auto-scrolling lyrics with fading edges.
 */
public final class LyricsView extends FrameLayout {
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
    private final List<TextView> lineViews = new ArrayList<>();
    private int activeLineIndex = -1;
    private boolean isSynced = false;
    private boolean autoScrollEnabled = true;
    private int textSizeSp = 22;
    private boolean userInteracting = false;
    private long lastUserScrollMs = 0L;

    public interface OnLyricSeekListener {
        void onSeek(long timestampMs);
    }

    private OnLyricSeekListener seekListener;
    private Runnable closeListener;

    public LyricsView(Context context) {
        super(context);

        // Apple Music style frosted backdrop: deep translucent tint over artwork
        // Top corners rounded to match FaceWidgetMusicPage, bottom corners flat
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(180, 12, 12, 18));
        float topRadius = dpToPx(26);
        bg.setCornerRadii(new float[]{topRadius, topRadius, topRadius, topRadius, 0, 0, 0, 0});
        setBackground(bg);
        setClipToOutline(true);

        // Main vertical structure: Header bar on top, lyrics ScrollView below
        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        addView(rootLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 1. Apple Music Top Header Bar
        headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dpToPx(22);
        int padTop = dpToPx(20);
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
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        headerTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitleView.setSingleLine(true);
        headerTitleView.setEllipsize(TextUtils.TruncateAt.END);
        headerTitleView.setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0));
        textCol.addView(headerTitleView);

        headerArtistView = new TextView(context);
        headerArtistView.setTextColor(Color.argb(190, 255, 255, 255));
        headerArtistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        headerArtistView.setSingleLine(true);
        headerArtistView.setEllipsize(TextUtils.TruncateAt.END);
        headerArtistView.setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0));
        headerArtistView.setPadding(0, dpToPx(2), 0, 0);
        textCol.addView(headerArtistView);

        headerLayout.addView(textCol);

        // Header Close Button (circular pill button)
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
        closeBg.setColor(Color.argb(90, 255, 255, 255));
        headerCloseBtn.setBackground(closeBg);
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
        linesContainer.setPadding(dpToPx(24), dpToPx(30), dpToPx(24), dpToPx(130));

        plainLyricsView = new TextView(context);
        plainLyricsView.setTextColor(Color.WHITE);
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        plainLyricsView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        plainLyricsView.setShadowLayer(5f, 0f, 2f, Color.argb(180, 0, 0, 0));
        plainLyricsView.setLineSpacing(dpToPx(8), 1.0f);
        plainLyricsView.setGravity(Gravity.START);
        plainLyricsView.setPadding(0, dpToPx(10), 0, dpToPx(10));
        plainLyricsView.setVisibility(GONE);

        linesContainer.addView(plainLyricsView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.addView(linesContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.setVisibility(GONE);
        contentContainer.addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Source attribution label
        attributionView = new TextView(context);
        attributionView.setTextColor(Color.argb(130, 255, 255, 255));
        attributionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        attributionView.setGravity(Gravity.END | Gravity.BOTTOM);
        attributionView.setPadding(0, 0, dpToPx(14), dpToPx(6));
        contentContainer.addView(attributionView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM));
    }

    public void setOnLyricSeekListener(OnLyricSeekListener listener) {
        this.seekListener = listener;
    }

    public void setOnCloseListener(Runnable listener) {
        this.closeListener = listener;
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
            }
        }
    }

    public void setTextSizeSp(int sizeSp) {
        this.textSizeSp = Math.max(14, Math.min(32, sizeSp));
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        for (TextView tv : lineViews) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        }
    }

    public void setLyrics(LyricsEntry entry) {
        lyricLines.clear();
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
            int linePadding = dpToPx(13);
            for (int i = 0; i < lyricLines.size(); i++) {
                LyricLine line = lyricLines.get(i);
                TextView lineTv = new TextView(ctx);
                lineTv.setText(line.text());
                lineTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                lineTv.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                lineTv.setTextColor(Color.WHITE);
                lineTv.setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0));
                lineTv.setAlpha(0.32f);
                lineTv.setLineSpacing(dpToPx(4), 1.15f);
                lineTv.setGravity(Gravity.START);
                lineTv.setPadding(0, linePadding, 0, linePadding);

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
            scrollView.scrollTo(0, 0);
        } else if (entry.hasPlainLyrics()) {
            isSynced = false;
            plainLyricsView.setVisibility(VISIBLE);
            plainLyricsView.setText(entry.plainLyrics());
            String src = "lyrics.ovh".equalsIgnoreCase(entry.source()) ? "lyrics.ovh" : entry.source();
            attributionView.setText(src + " • Plain");
            scrollView.scrollTo(0, 0);
        }
    }

    /**
     * Updates active highlighted lyric line and triggers smooth scroll (Apple Music style).
     */
    public void updatePlaybackPosition(long positionMs) {
        if (!isSynced || lyricLines.isEmpty() || lineViews.isEmpty()) return;

        int newIndex = LrcParser.findActiveLineIndex(lyricLines, positionMs);
        if (newIndex == activeLineIndex) return;

        int oldIndex = activeLineIndex;
        activeLineIndex = newIndex;

        if (!autoScrollEnabled) {
            return;
        }

        // Highlight active line and dim others
        if (oldIndex >= 0 && oldIndex < lineViews.size()) {
            TextView oldTv = lineViews.get(oldIndex);
            oldTv.animate().alpha(0.35f).scaleX(1.0f).scaleY(1.0f).setDuration(220).start();
        }

        if (newIndex >= 0 && newIndex < lineViews.size()) {
            TextView newTv = lineViews.get(newIndex);
            newTv.animate().alpha(1.0f).scaleX(1.03f).scaleY(1.03f).setDuration(220).start();

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

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return true;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
