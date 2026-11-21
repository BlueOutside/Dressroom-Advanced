package dev.denver.dressroomadvanced.lyrics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom lyrics overlay view for lockscreen media player.
 * Supports synchronized lyrics with auto-centering smooth scroll and active line highlight,
 * or scrollable plain lyrics, matching Dressroom's adaptive contrast aesthetics.
 */
public final class LyricsView extends FrameLayout {
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
    private int textSizeSp = 16;
    private boolean userInteracting = false;
    private long lastUserScrollMs = 0L;

    public interface OnLyricSeekListener {
        void onSeek(long timestampMs);
    }

    private OnLyricSeekListener seekListener;

    public LyricsView(Context context) {
        super(context);

        // Semi-translucent dark background with rounded corners for adaptive contrast
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(195, 12, 12, 18));
        bg.setCornerRadius(dpToPx(24));
        setBackground(bg);
        setClipToOutline(true);

        int pad = dpToPx(16);
        setPadding(pad, pad, pad, pad);

        // Status view (Loading / No lyrics)
        statusView = new TextView(context);
        statusView.setTextColor(Color.argb(180, 255, 255, 255));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setText("Loading lyrics...");
        addView(statusView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Scroll view for lyrics content
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
        linesContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        linesContainer.setPadding(0, dpToPx(80), 0, dpToPx(80)); // vertical clearance for centering

        plainLyricsView = new TextView(context);
        plainLyricsView.setTextColor(Color.WHITE);
        plainLyricsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        plainLyricsView.setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0));
        plainLyricsView.setLineSpacing(dpToPx(6), 1.0f);
        plainLyricsView.setGravity(Gravity.CENTER_HORIZONTAL);
        plainLyricsView.setPadding(dpToPx(12), dpToPx(20), dpToPx(12), dpToPx(20));
        plainLyricsView.setVisibility(GONE);

        linesContainer.addView(plainLyricsView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.addView(linesContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scrollView.setVisibility(GONE);
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Source attribution label
        attributionView = new TextView(context);
        attributionView.setTextColor(Color.argb(120, 255, 255, 255));
        attributionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        attributionView.setGravity(Gravity.END | Gravity.BOTTOM);
        attributionView.setPadding(0, 0, dpToPx(8), dpToPx(4));
        addView(attributionView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM));
    }

    public void setOnLyricSeekListener(OnLyricSeekListener listener) {
        this.seekListener = listener;
    }

    public void setAutoScrollEnabled(boolean enabled) {
        this.autoScrollEnabled = enabled;
        if (!enabled) {
            // Restore all line views to equal readable opacity
            for (TextView tv : lineViews) {
                tv.setAlpha(0.9f);
                tv.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    public void setTextSizeSp(int sizeSp) {
        this.textSizeSp = Math.max(12, Math.min(28, sizeSp));
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
            int linePadding = dpToPx(8);
            for (int i = 0; i < lyricLines.size(); i++) {
                LyricLine line = lyricLines.get(i);
                TextView lineTv = new TextView(ctx);
                lineTv.setText(line.text());
                lineTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                lineTv.setTextColor(Color.WHITE);
                lineTv.setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0));
                lineTv.setAlpha(0.45f);
                lineTv.setGravity(Gravity.CENTER_HORIZONTAL);
                lineTv.setPadding(dpToPx(12), linePadding, dpToPx(12), linePadding);

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
     * Updates active highlighted lyric line and triggers auto-centering smooth scroll.
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
            oldTv.animate().alpha(0.45f).scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            oldTv.setTypeface(Typeface.DEFAULT);
        }

        if (newIndex >= 0 && newIndex < lineViews.size()) {
            TextView newTv = lineViews.get(newIndex);
            newTv.animate().alpha(1.0f).scaleX(1.04f).scaleY(1.04f).setDuration(200).start();
            newTv.setTypeface(Typeface.DEFAULT_BOLD);

            // Don't fight the user if they recently manually scrolled
            long now = System.currentTimeMillis();
            if (!userInteracting && (now - lastUserScrollMs > 2500L)) {
                post(() -> {
                    int viewTop = newTv.getTop();
                    int viewHeight = newTv.getHeight();
                    int scrollHeight = scrollView.getHeight();
                    int targetY = viewTop - (scrollHeight - viewHeight) / 2;
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
