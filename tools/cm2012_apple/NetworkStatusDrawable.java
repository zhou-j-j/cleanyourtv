package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** CM2012 V5.2: green validated Ethernet, red unvalidated Ethernet, RSSI Wi-Fi. */
public final class NetworkStatusDrawable extends Drawable {
    public static final int DISCONNECTED = 0;
    public static final int WIFI = 1;
    public static final int ETHERNET = 2;
    private static final int ETHERNET_OK = Color.rgb(45, 210, 105);
    private static final int ETHERNET_BAD = Color.rgb(255, 78, 78);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int mode;
    private final int level;
    private final boolean ethernetOnline;
    private int alpha = 255;

    public NetworkStatusDrawable(float density, int mode, int level, boolean ethernetOnline) {
        this.size = Math.max(1, Math.round(24f * density));
        this.mode = mode;
        this.level = Math.max(0, Math.min(4, level));
        this.ethernetOnline = ethernetOnline;
        paint.setStrokeWidth(2.1f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    // Compatibility with earlier V5.2 caller; new caller must pass validated state.
    public NetworkStatusDrawable(float density, int mode, int level) {
        this(density, mode, level, false);
    }

    private void line(Canvas c, float... xy) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        c.drawPath(path, paint);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int save = canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(b.width() / 24f, b.height() / 24f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(alpha);
        if (mode == ETHERNET) {
            paint.setColor(ethernetOnline ? ETHERNET_OK : ETHERNET_BAD);
            paint.setAlpha(alpha);
            // RJ45 socket + short cable: keep shape identical in both states.
            line(canvas, 4f, 7f, 9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f,
                    20f, 7f, 20f, 17f, 4f, 17f, 4f, 7f);
            for (int i = 0; i < 4; i++)
                line(canvas, 9f + i * 2f, 9f, 9f + i * 2f, 12f);
            line(canvas, 12f, 17f, 12f, 21f);
            line(canvas, 8f, 21f, 16f, 21f);
        } else if (mode == WIFI) {
            paint.setColor(Color.WHITE);
            for (int ring = 4; ring >= 1; ring--) {
                paint.setAlpha(level >= ring ? alpha : Math.round(alpha * .20f));
                float radius = ring == 1 ? 4.4f : ring == 2 ? 7f : ring == 3 ? 9.6f : 12f;
                canvas.drawArc(new RectF(12f - radius, 22f - radius,
                        12f + radius, 22f + radius), 225f, 90f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(level >= 1 ? alpha : Math.round(alpha * .30f));
            canvas.drawCircle(12f, 20.8f, 2.1f, paint);
        } else {
            paint.setColor(ETHERNET_BAD);
            paint.setAlpha(alpha);
            line(canvas, 3f, 8f, 8f, 8f, 8f, 5f, 16f, 5f, 16f, 8f,
                    21f, 8f, 21f, 18f, 3f, 18f, 3f, 8f);
            line(canvas, 7f, 10f, 17f, 16f);
            line(canvas, 17f, 10f, 7f, 16f);
        }
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        invalidateSelf();
    }
    @Override public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
}
