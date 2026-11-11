package com.muklashdev.tmsounds;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Decorative symmetric waveform, like the hero art on premium audio apps. */
public class WaveView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active = false;

    public WaveView(Context c) { super(c); }
    public WaveView(Context c, AttributeSet a) { super(c, a); }

    public void setActive(boolean on) {
        active = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int bars = 44;
        float gap = w / (float) bars;
        float barW = gap * 0.42f;
        float mid = h / 2f;
        paint.setColor(active ? 0xFFB7BEE9 : 0xFF55556E);
        for (int i = 0; i < bars; i++) {
            double t = i / (double) (bars - 1);           // 0..1
            double env = Math.sin(Math.PI * t);           // arch envelope
            double ripple = 0.55 + 0.45 * Math.sin(t * 22.0 + 1.3)
                    * Math.sin(t * 7.0);
            double bh = (0.10 + 0.85 * env * Math.abs(ripple)) * (h * 0.46);
            float x = i * gap + (gap - barW) / 2f;
            float r = barW / 2f;
            canvas.drawRoundRect(x, (float) (mid - bh), x + barW,
                    (float) (mid + bh), r, r, paint);
        }
        // center play dots
        paint.setColor(active ? 0xFFE3E6FA : 0xFF77778F);
        float cr = h * 0.13f;
        canvas.drawCircle(w / 2f - cr * 0.75f, mid, cr, paint);
        canvas.drawCircle(w / 2f + cr * 0.75f, mid, cr, paint);
    }
}
