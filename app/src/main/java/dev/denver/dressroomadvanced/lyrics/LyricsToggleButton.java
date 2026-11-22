package dev.denver.dressroomadvanced.lyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.TypedValue;
import android.view.View;

/**
 * Apple Music style lyrics button: a circular frosted glass icon with
 * Apple's quotation speech bubble icon, dynamically toggling between
 * inactive (translucent/white) and active (bright disc/dark bubble) states.
 */
public final class LyricsToggleButton extends View {
    private boolean isActive = false;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LyricsToggleButton(Context context) {
        super(context);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setActive(boolean active) {
        if (this.isActive == active) return;
        this.isActive = active;
        invalidate();
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dpToPx(1.5f);

        // 1. Circular disc
        if (isActive) {
            bgPaint.setColor(Color.argb(240, 255, 255, 255));
        } else {
            bgPaint.setColor(Color.argb(55, 255, 255, 255));
        }
        canvas.drawCircle(cx, cy, radius, bgPaint);

        // 2. Bubble in a layer so CLEAR mode punches cleanly through the bubble to the disc
        int saveCount = canvas.saveLayer(0, 0, w, h, null);

        float bw = dpToPx(16f);
        float bh = dpToPx(11.2f);
        float br = dpToPx(3.4f);
        float bx = cx - bw / 2f;
        float by = cy - bh / 2f - dpToPx(0.8f);

        Path bubblePath = new Path();
        bubblePath.addRoundRect(bx, by, bx + bw, by + bh, br, br, Path.Direction.CW);

        // Curved tail pointing down-left
        Path tailPath = new Path();
        tailPath.moveTo(bx + dpToPx(5.2f), by + bh);
        tailPath.cubicTo(
                bx + dpToPx(3.2f), by + bh + dpToPx(2.4f),
                bx + dpToPx(0.8f), by + bh + dpToPx(4.0f),
                bx - dpToPx(0.8f), by + bh + dpToPx(4.4f)
        );
        tailPath.cubicTo(
                bx + dpToPx(1.2f), by + bh + dpToPx(2.8f),
                bx + dpToPx(2.4f), by + bh + dpToPx(1.2f),
                bx + dpToPx(2.4f), by + bh
        );
        tailPath.close();
        bubblePath.op(tailPath, Path.Op.UNION);

        if (isActive) {
            bubblePaint.setColor(Color.argb(230, 20, 20, 20));
        } else {
            bubblePaint.setColor(Color.argb(242, 255, 255, 255));
        }
        canvas.drawPath(bubblePath, bubblePaint);

        // 3. Cutout quotation marks inside speech bubble
        float qRadius = dpToPx(1.3f);
        float qy = cy - dpToPx(1.2f);

        drawQuoteMark(canvas, cx - dpToPx(2.6f), qy, qRadius, clearPaint);
        drawQuoteMark(canvas, cx + dpToPx(2.6f), qy, qRadius, clearPaint);

        canvas.restoreToCount(saveCount);
    }

    private void drawQuoteMark(Canvas canvas, float qx, float qy, float r, Paint paint) {
        canvas.drawCircle(qx, qy, r, paint);
        Path commaPath = new Path();
        commaPath.moveTo(qx + r, qy);
        commaPath.cubicTo(
                qx + r, qy + r * 1.6f,
                qx - r * 0.3f, qy + r * 2.5f,
                qx - r * 1.3f, qy + r * 3.0f
        );
        commaPath.lineTo(qx - r * 1.0f, qy + r * 2.2f);
        commaPath.cubicTo(
                qx, qy + r * 1.9f,
                qx + r * 0.7f, qy + r * 1.0f,
                qx + r * 0.7f, qy
        );
        commaPath.close();
        canvas.drawPath(commaPath, paint);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
