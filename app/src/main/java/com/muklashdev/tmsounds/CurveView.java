package com.muklashdev.tmsounds;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Interactive frequency-response curve. Points are the device's real EQ
 * bands, positioned on a log frequency scale; dragging a point changes
 * that band's gain. The curve between points is a smooth interpolation
 * (display only) - the real control is the bands themselves.
 */
public class CurveView extends View {

    public interface OnBandChanged {
        void onBandChanged(int band, int millibels);
    }

    private static final double LOG_MIN = Math.log10(30);
    private static final double LOG_MAX = Math.log10(20000);

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] centers = new int[0];
    private int[] levels = new int[0];
    private int minMb = -1500, maxMb = 1500;
    private int selected = -1;
    private int dragBand = -1;
    private OnBandChanged listener;

    public CurveView(Context c) { super(c); init(); }
    public CurveView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        gridPaint.setColor(0x2EFFFFFF);
        gridPaint.setStrokeWidth(1.5f);
        linePaint.setColor(0xFFB7BEE9);
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setColor(0x2EB7BEE9);
        fillPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(0xFFE3E6FA);
        textPaint.setColor(0xFF9D9DB4);
        textPaint.setTextSize(26f);
    }

    public void setData(int[] centersHz, int[] levelsMb, int min, int max) {
        centers = centersHz;
        levels = levelsMb.clone();
        minMb = min;
        maxMb = max;
        invalidate();
    }

    public void setLevel(int band, int millibels) {
        if (band >= 0 && band < levels.length) {
            levels[band] = millibels;
            invalidate();
        }
    }

    public void setSelected(int band) {
        selected = band;
        invalidate();
    }

    public void setOnBandChanged(OnBandChanged l) { listener = l; }

    private float xFor(int hz) {
        double t = (Math.log10(Math.max(30, hz)) - LOG_MIN) / (LOG_MAX - LOG_MIN);
        float pad = 34f;
        return pad + (float) t * (getWidth() - pad * 2);
    }

    private float yFor(int mb) {
        float pad = 26f;
        double t = (maxMb - mb) / (double) (maxMb - minMb);
        return pad + (float) t * (getHeight() - pad * 2);
    }

    private int mbForY(float y) {
        float pad = 26f;
        double t = (y - pad) / (double) (getHeight() - pad * 2);
        int mb = (int) Math.round(maxMb - t * (maxMb - minMb));
        if (mb < minMb) mb = minMb;
        if (mb > maxMb) mb = maxMb;
        return mb;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // grid
        int[] gl = {maxMb, maxMb / 2, 0, minMb / 2, minMb};
        for (int g : gl) {
            float y = yFor(g);
            canvas.drawLine(10, y, w - 10, y, gridPaint);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(Locale.US, "+%d", maxMb / 100),
                12, yFor(maxMb) + 24, textPaint);
        canvas.drawText("0", 12, yFor(0) - 6, textPaint);
        canvas.drawText(String.format(Locale.US, "%d", minMb / 100),
                12, yFor(minMb) - 6, textPaint);

        if (centers.length == 0) return;

        // smooth path through the band points (midpoint quadratic)
        Path path = new Path();
        float x0 = xFor(centers[0]), y0 = yFor(levels[0]);
        path.moveTo(x0, y0);
        for (int i = 1; i < centers.length; i++) {
            float x = xFor(centers[i]), y = yFor(levels[i]);
            float px = xFor(centers[i - 1]), py = yFor(levels[i - 1]);
            path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f);
            if (i == centers.length - 1) path.lineTo(x, y);
        }
        // fill under curve
        Path fill = new Path(path);
        fill.lineTo(xFor(centers[centers.length - 1]), h - 6);
        fill.lineTo(x0, h - 6);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(path, linePaint);

        // points + frequency labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < centers.length; i++) {
            float x = xFor(centers[i]), y = yFor(levels[i]);
            pointPaint.setColor(i == selected ? 0xFFFFFFFF : 0xFFE3E6FA);
            canvas.drawCircle(x, y, i == selected ? 13f : 9f, pointPaint);
            textPaint.setColor(0xFF9D9DB4);
            canvas.drawText(shortHz(centers[i]), x, h - 8, textPaint);
        }
    }

    private String shortHz(int hz) {
        if (hz < 1000) return String.valueOf(hz);
        double k = hz / 1000.0;
        return (k == Math.floor(k))
                ? ((int) k) + "k"
                : String.format(Locale.US, "%.1fk", k);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (centers.length == 0) return super.onTouchEvent(e);
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                dragBand = nearestBand(e.getX());
                if (dragBand >= 0) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    applyDrag(e.getY());
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragBand >= 0) {
                    applyDrag(e.getY());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragBand = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int nearestBand(float x) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < centers.length; i++) {
            float d = Math.abs(xFor(centers[i]) - x);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private void applyDrag(float y) {
        int mb = mbForY(y);
        levels[dragBand] = mb;
        selected = dragBand;
        invalidate();
        if (listener != null) listener.onBandChanged(dragBand, mb);
    }
}
