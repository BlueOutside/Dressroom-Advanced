package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

/** Crisp procedural icons, emoji badges, app icons, and cropped photos for DIY Now Bar cards. */
final class DiyNowBarIcons {
    private DiyNowBarIcons() {}

    static Icon pillIcon(Context context, DiyNowBarCard card) {
        if (card.iconType == DiyNowBarCard.ICON_SYMBOL) {
            // Monochrome white glyph on transparent lets Samsung's NowBarOngoingCardView apply
            // accent badge fill + radial glow natively.
            return Icon.createWithBitmap(drawSymbolGlyph(card.iconValue, 96, Color.WHITE));
        }
        return Icon.createWithBitmap(renderBadgeBitmap(context, card, 128));
    }

    static Bitmap renderBadgeBitmap(Context context, DiyNowBarCard card, int sizePx) {
        int s = Math.max(32, sizePx);
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        boolean squircle = card.iconShape == DiyNowBarCard.SHAPE_SQUIRCLE;
        float radius = squircle ? s * 0.28f : s * 0.5f;

        if (card.iconType == DiyNowBarCard.ICON_PHOTO) {
            Bitmap photo = DiyNowBarStore.loadPhotoIcon(context, card.iconValue);
            if (photo != null) {
                drawClippedBitmap(canvas, photo, s, radius);
                return out;
            }
        }
        if (card.iconType == DiyNowBarCard.ICON_APP && !card.iconValue.isEmpty()) {
            Drawable appIcon = loadAppIcon(context, card.iconValue);
            if (appIcon != null) {
                Bitmap appBmp = drawableToBitmap(appIcon, s);
                drawClippedBitmap(canvas, appBmp, s, radius);
                appBmp.recycle();
                return out;
            }
        }

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(card.accentColor);
        canvas.drawRoundRect(new RectF(0, 0, s, s), radius, radius, bg);

        if (card.iconType == DiyNowBarCard.ICON_EMOJI && !card.iconValue.isEmpty()) {
            drawCenteredEmoji(canvas, card.iconValue, s);
        } else {
            Bitmap glyph = drawSymbolGlyph(card.iconValue, Math.round(s * 0.62f), Color.WHITE);
            float offset = (s - glyph.getWidth()) * 0.5f;
            canvas.drawBitmap(glyph, offset, offset, null);
            glyph.recycle();
        }
        return out;
    }

    static Bitmap renderShortcutBitmap(Context context, DiyNowBarCard.ShortcutSlot slot, int accent, int sizePx) {
        int s = Math.max(32, sizePx);
        if (slot.action == DiyNowBarCard.ACTION_OPEN_APP && !slot.target.isEmpty()) {
            Drawable d = loadAppIcon(context, slot.target);
            if (d != null) {
                Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Bitmap raw = drawableToBitmap(d, s);
                drawClippedBitmap(canvas, raw, s, s * 0.26f);
                raw.recycle();
                return out;
            }
        }
        String glyphKey =
                switch (slot.action) {
                    case DiyNowBarCard.ACTION_FLASHLIGHT -> "bolt";
                    case DiyNowBarCard.ACTION_OPEN_URL -> "rocket";
                    case DiyNowBarCard.ACTION_OPEN_EDITOR -> "edit";
                    default -> "sparkles";
                };
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(blendAlpha(accent, 0xCC));
        canvas.drawRoundRect(new RectF(0, 0, s, s), s * 0.28f, s * 0.28f, bg);
        Bitmap g = drawSymbolGlyph(glyphKey, Math.round(s * 0.58f), Color.WHITE);
        float off = (s - g.getWidth()) * 0.5f;
        canvas.drawBitmap(g, off, off, null);
        g.recycle();
        return out;
    }

    /** Renders a circular/squircle badge for any live or built-in Now Bar stack slot. */
    static Bitmap renderStackSlotBitmap(
            Context context, DiyNowBarStackOrder.LiveEntry entry, DiyNowBarCard diyCard, int sizePx) {
        if (diyCard != null) return renderBadgeBitmap(context, diyCard, sizePx);
        int s = Math.max(32, sizePx);
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float radius = s * 0.32f;
        if (entry != null
                && DiyNowBarStackOrder.KIND_APP.equals(entry.kind)
                && !entry.packageName.isEmpty()) {
            Drawable d = loadAppIcon(context, entry.packageName);
            if (d != null) {
                Bitmap raw = drawableToBitmap(d, s);
                drawClippedBitmap(canvas, raw, s, radius);
                raw.recycle();
                return out;
            }
        }
        int bgColor = entry == null ? 0xFF3B82F6 : entry.accentColor;
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(bgColor);
        canvas.drawRoundRect(new RectF(0, 0, s, s), radius, radius, bg);
        String sym = DiyNowBarStackOrder.symbolForKind(entry == null ? null : entry.kind);
        Bitmap glyph = drawSymbolGlyph(sym, Math.round(s * 0.58f), Color.WHITE);
        float off = (s - glyph.getWidth()) * 0.5f;
        canvas.drawBitmap(glyph, off, off, null);
        glyph.recycle();
        return out;
    }

    /** Draws a crisp 6-dot vertical drag grip handle for pill reordering. */
    static Bitmap drawDragHandle(int widthPx, int heightPx, int color) {
        int w = Math.max(16, widthPx);
        int h = Math.max(20, heightPx);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        float r = Math.max(1.8f, w * 0.095f);
        float cx1 = w * 0.36f;
        float cx2 = w * 0.64f;
        float cy1 = h * 0.28f;
        float cy2 = h * 0.50f;
        float cy3 = h * 0.72f;
        c.drawCircle(cx1, cy1, r, p);
        c.drawCircle(cx2, cy1, r, p);
        c.drawCircle(cx1, cy2, r, p);
        c.drawCircle(cx2, cy2, r, p);
        c.drawCircle(cx1, cy3, r, p);
        c.drawCircle(cx2, cy3, r, p);
        return out;
    }

    static Bitmap drawActionGlyph(String kind, int sizePx, int color) {
        return drawSymbolGlyph(kind, sizePx, color);
    }

    static Bitmap drawCheckbox(boolean checked, int accentColor, int sizePx) {
        int s = Math.max(24, sizePx);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float stroke = Math.max(2f, s * 0.1f);
        float pad = stroke;
        RectF box = new RectF(pad, pad, s - pad, s - pad);
        float r = s * 0.28f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (checked) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(accentColor);
            c.drawRoundRect(box, r, r, p);
            Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
            check.setStyle(Paint.Style.STROKE);
            check.setColor(Color.WHITE);
            check.setStrokeWidth(Math.max(2.2f, s * 0.11f));
            check.setStrokeCap(Paint.Cap.ROUND);
            check.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            path.moveTo(s * 0.27f, s * 0.52f);
            path.lineTo(s * 0.44f, s * 0.69f);
            path.lineTo(s * 0.74f, s * 0.34f);
            c.drawPath(path, check);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(0xB3FFFFFF);
            c.drawRoundRect(box, r, r, p);
        }
        return bmp;
    }

    static Bitmap drawSymbolGlyph(String key, int sizePx, int color) {
        int s = Math.max(24, sizePx);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(2.2f, s * 0.095f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        float cx = s * 0.5f;
        float cy = s * 0.5f;
        float u = s / 24f;
        String k = key == null ? "sparkles" : key;
        Path p = new Path();

        switch (k) {
            case "star" -> {
                for (int i = 0; i < 10; i++) {
                    double angle = Math.toRadians(-90 + i * 36);
                    float r = (i % 2 == 0) ? 9.5f * u : 4.2f * u;
                    float x = cx + (float) Math.cos(angle) * r;
                    float y = cy + (float) Math.sin(angle) * r;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, fill);
            }
            case "heart" -> {
                p.moveTo(12 * u, 20.5f * u);
                p.cubicTo(4 * u, 15 * u, 2.5f * u, 9.5f * u, 5.5f * u, 6.2f * u);
                p.cubicTo(7.8f * u, 3.8f * u, 11 * u, 5 * u, 12 * u, 7.6f * u);
                p.cubicTo(13 * u, 5 * u, 16.2f * u, 3.8f * u, 18.5f * u, 6.2f * u);
                p.cubicTo(21.5f * u, 9.5f * u, 20 * u, 15 * u, 12 * u, 20.5f * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "bolt" -> {
                p.moveTo(13.5f * u, 2.5f * u);
                p.lineTo(5.5f * u, 13.5f * u);
                p.lineTo(11.5f * u, 13.5f * u);
                p.lineTo(10.5f * u, 21.5f * u);
                p.lineTo(18.5f * u, 10.5f * u);
                p.lineTo(12.5f * u, 10.5f * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "flame" -> {
                p.moveTo(12.5f * u, 2.5f * u);
                p.cubicTo(17.5f * u, 7.5f * u, 19.5f * u, 12 * u, 18 * u, 16.5f * u);
                p.cubicTo(16.8f * u, 19.8f * u, 14.5f * u, 21.2f * u, 12 * u, 21.2f * u);
                p.cubicTo(8.2f * u, 21.2f * u, 5.5f * u, 18.2f * u, 5.8f * u, 14.2f * u);
                p.cubicTo(6 * u, 11 * u, 8.5f * u, 8.5f * u, 9.5f * u, 6.2f * u);
                p.cubicTo(10.8f * u, 8 * u, 12 * u, 6 * u, 12.5f * u, 2.5f * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "clock" -> {
                c.drawCircle(cx, cy, 8.5f * u, stroke);
                c.drawLine(cx, cy, cx, 7.5f * u, stroke);
                c.drawLine(cx, cy, 16 * u, 14.5f * u, stroke);
            }
            case "target" -> {
                c.drawCircle(cx, cy, 8.8f * u, stroke);
                c.drawCircle(cx, cy, 4.8f * u, stroke);
                c.drawCircle(cx, cy, 1.8f * u, fill);
            }
            case "check" -> {
                p.moveTo(5 * u, 12.5f * u);
                p.lineTo(10 * u, 17.5f * u);
                p.lineTo(19.5f * u, 6.8f * u);
                stroke.setStrokeWidth(Math.max(2.6f, s * 0.12f));
                c.drawPath(p, stroke);
            }
            case "music" -> {
                p.moveTo(9.5f * u, 17 * u);
                p.lineTo(9.5f * u, 6.2f * u);
                p.lineTo(18.5f * u, 4.5f * u);
                p.lineTo(18.5f * u, 15.2f * u);
                c.drawPath(p, stroke);
                c.drawCircle(7.4f * u, 17.5f * u, 2.5f * u, fill);
                c.drawCircle(16.4f * u, 15.7f * u, 2.5f * u, fill);
            }
            case "coffee" -> {
                c.drawRoundRect(new RectF(4.5f * u, 8 * u, 16 * u, 18.5f * u), 2.5f * u, 2.5f * u, stroke);
                c.drawArc(new RectF(14.5f * u, 9.5f * u, 20 * u, 15.5f * u), -90, 180, false, stroke);
                c.drawLine(7.5f * u, 4.5f * u, 7.5f * u, 6.2f * u, stroke);
                c.drawLine(11.5f * u, 3.8f * u, 11.5f * u, 6.2f * u, stroke);
            }
            case "dumbbell" -> {
                c.drawLine(6.5f * u, cy, 17.5f * u, cy, stroke);
                c.drawRoundRect(new RectF(4 * u, 7.5f * u, 7.2f * u, 16.5f * u), 1.4f * u, 1.4f * u, fill);
                c.drawRoundRect(new RectF(16.8f * u, 7.5f * u, 20 * u, 16.5f * u), 1.4f * u, 1.4f * u, fill);
                c.drawRoundRect(new RectF(2 * u, 9.5f * u, 4.4f * u, 14.5f * u), u, u, fill);
                c.drawRoundRect(new RectF(19.6f * u, 9.5f * u, 22 * u, 14.5f * u), u, u, fill);
            }
            case "book" -> {
                p.moveTo(4 * u, 6.5f * u);
                p.quadTo(8 * u, 5 * u, 12 * u, 7.2f * u);
                p.quadTo(16 * u, 5 * u, 20 * u, 6.5f * u);
                p.lineTo(20 * u, 18.2f * u);
                p.quadTo(16 * u, 16.8f * u, 12 * u, 18.8f * u);
                p.quadTo(8 * u, 16.8f * u, 4 * u, 18.2f * u);
                p.close();
                c.drawPath(p, stroke);
                c.drawLine(12 * u, 7.2f * u, 12 * u, 18.8f * u, stroke);
            }
            case "rocket" -> {
                p.moveTo(19.5f * u, 4.5f * u);
                p.quadTo(13 * u, 4.5f * u, 9 * u, 9.5f * u);
                p.lineTo(14.5f * u, 15 * u);
                p.quadTo(19.5f * u, 11 * u, 19.5f * u, 4.5f * u);
                p.close();
                c.drawPath(p, fill);
                c.drawLine(5 * u, 19 * u, 10 * u, 14 * u, stroke);
                c.drawLine(5.5f * u, 14 * u, 8.8f * u, 10.7f * u, stroke);
                c.drawLine(10 * u, 18.5f * u, 13.3f * u, 15.2f * u, stroke);
            }
            case "calendar" -> {
                c.drawRoundRect(new RectF(4 * u, 6 * u, 20 * u, 19.5f * u), 2.4f * u, 2.4f * u, stroke);
                c.drawLine(4 * u, 10.5f * u, 20 * u, 10.5f * u, stroke);
                c.drawLine(8 * u, 3.8f * u, 8 * u, 7 * u, stroke);
                c.drawLine(16 * u, 3.8f * u, 16 * u, 7 * u, stroke);
            }
            case "water" -> {
                p.moveTo(12 * u, 3 * u);
                p.cubicTo(12 * u, 3 * u, 19 * u, 11 * u, 19 * u, 15 * u);
                p.cubicTo(19 * u, 18.9f * u, 15.9f * u, 21.2f * u, 12 * u, 21.2f * u);
                p.cubicTo(8.1f * u, 21.2f * u, 5 * u, 18.9f * u, 5 * u, 15 * u);
                p.cubicTo(5 * u, 11 * u, 12 * u, 3 * u, 12 * u, 3 * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "battery" -> {
                c.drawRoundRect(new RectF(3 * u, 7.2f * u, 19 * u, 16.8f * u), 2.2f * u, 2.2f * u, stroke);
                c.drawRoundRect(new RectF(6 * u, 9.8f * u, 14.5f * u, 14.2f * u), u, u, fill);
                c.drawRoundRect(new RectF(20 * u, 10 * u, 21.8f * u, 14 * u), u, u, fill);
            }
            case "cpu" -> {
                c.drawRoundRect(new RectF(6.5f * u, 6.5f * u, 17.5f * u, 17.5f * u), 2 * u, 2 * u, stroke);
                c.drawRoundRect(new RectF(9.8f * u, 9.8f * u, 14.2f * u, 14.2f * u), u, u, fill);
                for (float pos : new float[] {9.5f, 14.5f}) {
                    c.drawLine(pos * u, 3.5f * u, pos * u, 6 * u, stroke);
                    c.drawLine(pos * u, 18 * u, pos * u, 20.5f * u, stroke);
                    c.drawLine(3.5f * u, pos * u, 6 * u, pos * u, stroke);
                    c.drawLine(18 * u, pos * u, 20.5f * u, pos * u, stroke);
                }
            }
            case "grid" -> {
                c.drawRoundRect(new RectF(4 * u, 4 * u, 10.5f * u, 10.5f * u), 2 * u, 2 * u, fill);
                c.drawRoundRect(new RectF(13.5f * u, 4 * u, 20 * u, 10.5f * u), 2 * u, 2 * u, fill);
                c.drawRoundRect(new RectF(4 * u, 13.5f * u, 10.5f * u, 20 * u), 2 * u, 2 * u, fill);
                c.drawRoundRect(new RectF(13.5f * u, 13.5f * u, 20 * u, 20 * u), 2 * u, 2 * u, fill);
            }
            case "note" -> {
                c.drawRoundRect(new RectF(5 * u, 3.8f * u, 19 * u, 20.2f * u), 2.4f * u, 2.4f * u, stroke);
                c.drawLine(8.2f * u, 9 * u, 15.8f * u, 9 * u, stroke);
                c.drawLine(8.2f * u, 12.6f * u, 15.8f * u, 12.6f * u, stroke);
                c.drawLine(8.2f * u, 16.2f * u, 13 * u, 16.2f * u, stroke);
            }
            case "pin" -> {
                c.drawCircle(cx, 8.8f * u, 4.5f * u, fill);
                c.drawLine(cx, 13 * u, cx, 21 * u, stroke);
            }
            case "moon" -> {
                p.addCircle(12 * u, 12 * u, 8 * u, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(15.5f * u, 9 * u, 6.8f * u, Path.Direction.CW);
                p.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(p, fill);
            }
            case "sun" -> {
                c.drawCircle(cx, cy, 4.6f * u, fill);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    c.drawLine(
                            cx + (float) Math.cos(a) * 6.8f * u,
                            cy + (float) Math.sin(a) * 6.8f * u,
                            cx + (float) Math.cos(a) * 9.5f * u,
                            cy + (float) Math.sin(a) * 9.5f * u,
                            stroke);
                }
            }
            case "shield" -> {
                p.moveTo(12 * u, 3 * u);
                p.lineTo(19 * u, 6 * u);
                p.lineTo(19 * u, 12.2f * u);
                p.cubicTo(19 * u, 16.8f * u, 15.5f * u, 19.6f * u, 12 * u, 21 * u);
                p.cubicTo(8.5f * u, 19.6f * u, 5 * u, 16.8f * u, 5 * u, 12.2f * u);
                p.lineTo(5 * u, 6 * u);
                p.close();
                c.drawPath(p, stroke);
            }
            case "bell" -> {
                p.moveTo(12 * u, 4 * u);
                p.cubicTo(8 * u, 4 * u, 6.5f * u, 7.2f * u, 6.5f * u, 11.5f * u);
                p.lineTo(4.8f * u, 16.5f * u);
                p.lineTo(19.2f * u, 16.5f * u);
                p.lineTo(17.5f * u, 11.5f * u);
                p.cubicTo(17.5f * u, 7.2f * u, 16 * u, 4 * u, 12 * u, 4 * u);
                p.close();
                c.drawPath(p, fill);
                c.drawCircle(cx, 19.2f * u, 1.8f * u, fill);
            }
            case "plus" -> {
                stroke.setStrokeWidth(Math.max(2.6f, s * 0.12f));
                c.drawLine(cx, 5 * u, cx, 19 * u, stroke);
                c.drawLine(5 * u, cy, 19 * u, cy, stroke);
            }
            case "minus" -> {
                stroke.setStrokeWidth(Math.max(2.6f, s * 0.12f));
                c.drawLine(5 * u, cy, 19 * u, cy, stroke);
            }
            case "play" -> {
                p.moveTo(7.5f * u, 5 * u);
                p.lineTo(19 * u, cy);
                p.lineTo(7.5f * u, 19 * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "pause" -> {
                c.drawRoundRect(new RectF(6.5f * u, 5.5f * u, 10.2f * u, 18.5f * u), 1.4f * u, 1.4f * u, fill);
                c.drawRoundRect(new RectF(13.8f * u, 5.5f * u, 17.5f * u, 18.5f * u), 1.4f * u, 1.4f * u, fill);
            }
            case "reset" -> {
                c.drawArc(new RectF(5 * u, 5 * u, 19 * u, 19 * u), 40, 285, false, stroke);
                p.moveTo(15.5f * u, 3.8f * u);
                p.lineTo(19.2f * u, 7.2f * u);
                p.lineTo(14.8f * u, 8.8f * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "edit" -> {
                p.moveTo(5 * u, 19 * u);
                p.lineTo(6.6f * u, 14.2f * u);
                p.lineTo(15.8f * u, 5 * u);
                p.lineTo(19 * u, 8.2f * u);
                p.lineTo(9.8f * u, 17.4f * u);
                p.close();
                c.drawPath(p, fill);
            }
            case "open" -> {
                c.drawLine(8 * u, 16 * u, 18 * u, 6 * u, stroke);
                c.drawLine(11 * u, 6 * u, 18 * u, 6 * u, stroke);
                c.drawLine(18 * u, 6 * u, 18 * u, 13 * u, stroke);
            }
            case "back" -> {
                stroke.setStrokeWidth(Math.max(2.2f, s * 0.085f));
                p.moveTo(15 * u, 5.5f * u);
                p.lineTo(8.5f * u, 12 * u);
                p.lineTo(15 * u, 18.5f * u);
                c.drawPath(p, stroke);
            }
            case "chevron" -> {
                stroke.setStrokeWidth(Math.max(2f, s * 0.08f));
                p.moveTo(9.5f * u, 6.5f * u);
                p.lineTo(15 * u, 12 * u);
                p.lineTo(9.5f * u, 17.5f * u);
                c.drawPath(p, stroke);
            }
            case "more" -> {
                float r = 1.65f * u;
                c.drawCircle(cx, 6.2f * u, r, fill);
                c.drawCircle(cx, 12 * u, r, fill);
                c.drawCircle(cx, 17.8f * u, r, fill);
            }
            case "trash" -> {
                stroke.setStrokeWidth(Math.max(2f, s * 0.082f));
                c.drawLine(5.5f * u, 7 * u, 18.5f * u, 7 * u, stroke);
                c.drawLine(9.5f * u, 4.5f * u, 14.5f * u, 4.5f * u, stroke);
                c.drawRoundRect(new RectF(7 * u, 7 * u, 17 * u, 19.5f * u), 1.8f * u, 1.8f * u, stroke);
                c.drawLine(10.2f * u, 10.5f * u, 10.2f * u, 16 * u, stroke);
                c.drawLine(13.8f * u, 10.5f * u, 13.8f * u, 16 * u, stroke);
            }
            case "sliders" -> {
                stroke.setStrokeWidth(Math.max(2f, s * 0.085f));
                c.drawLine(4.5f * u, 7 * u, 19.5f * u, 7 * u, stroke);
                c.drawLine(4.5f * u, 12 * u, 19.5f * u, 12 * u, stroke);
                c.drawLine(4.5f * u, 17 * u, 19.5f * u, 17 * u, stroke);
                c.drawCircle(9 * u, 7 * u, 2.3f * u, fill);
                c.drawCircle(15.5f * u, 12 * u, 2.3f * u, fill);
                c.drawCircle(10.5f * u, 17 * u, 2.3f * u, fill);
            }
            default -> { // sparkles (Galaxy AI style 4-point star + companion spark)
                drawFourPointStar(c, fill, 10.8f * u, 12.2f * u, 7.6f * u);
                drawFourPointStar(c, fill, 18.2f * u, 6.2f * u, 3.4f * u);
            }
        }
        return bmp;
    }

    private static void drawFourPointStar(Canvas c, Paint p, float cx, float cy, float r) {
        Path star = new Path();
        float inner = r * 0.28f;
        star.moveTo(cx, cy - r);
        star.quadTo(cx + inner * 0.5f, cy - inner * 0.5f, cx + r, cy);
        star.quadTo(cx + inner * 0.5f, cy + inner * 0.5f, cx, cy + r);
        star.quadTo(cx - inner * 0.5f, cy + inner * 0.5f, cx - r, cy);
        star.quadTo(cx - inner * 0.5f, cy - inner * 0.5f, cx, cy - r);
        star.close();
        c.drawPath(star, p);
    }

    private static void drawCenteredEmoji(Canvas canvas, String text, int size) {
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        int codePoints = text.codePointCount(0, text.length());
        tp.setTextSize(codePoints <= 2 ? size * 0.52f : size * 0.36f);
        Paint.FontMetrics fm = tp.getFontMetrics();
        float baseline = (size * 0.5f) - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(text, size * 0.5f, baseline, tp);
    }

    private static void drawClippedBitmap(Canvas target, Bitmap src, int size, float radius) {
        int save = target.saveLayer(0, 0, size, size, null);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(Color.WHITE);
        target.drawRoundRect(new RectF(0, 0, size, size), radius, radius, mask);
        Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        img.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        target.drawBitmap(src, null, new RectF(0, 0, size, size), img);
        target.restoreToCount(save);
    }

    private static Bitmap drawableToBitmap(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    static Drawable loadAppIcon(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return null;
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationIcon(packageName);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String loadAppLabel(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return "";
        try {
            PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
            return label == null ? packageName : label.toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private static int blendAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
