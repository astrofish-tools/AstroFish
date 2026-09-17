package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Low-cost luminance histogram for the live preview parameter overlay. */
final class HistogramView extends View {
    private static final int BIN_COUNT = 64;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] bins = new int[BIN_COUNT];
    private String mode = "LIVE";
    private boolean hasData;

    HistogramView(Context context) { super(context); }

    void update(byte[] frame, int width, int height, double exposureScale, String mode) {
        bins = LuminanceHistogram.calculate(frame, width, height, BIN_COUNT, 4096, exposureScale);
        this.mode = mode;
        hasData = true;
        postInvalidate();
    }

    void update(short[] source, double exposureScale, String mode) {
        bins = LuminanceHistogram.calculate(source, BIN_COUNT, exposureScale);
        this.mode = mode;
        hasData = true;
        postInvalidate();
    }

    void waiting() { hasData = false; mode = "WAIT"; postInvalidate(); }
    void noData() { if (!hasData) { mode = "NO DATA"; postInvalidate(); } }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xA8000000);
        canvas.drawRect(0, 0, width, height, paint);
        if (!hasData) {
            paint.setColor(Color.rgb(220, 130, 95));
            paint.setTextSize(14);
            canvas.drawText(mode, 8, height / 2 + 5, paint);
            return;
        }
        int max = 1;
        for (int i = 0; i < bins.length; i++) if (bins[i] > max) max = bins[i];
        float chartLeft = 5, chartTop = 14, chartRight = width - 5, chartBottom = height - 5;
        float barWidth = (chartRight - chartLeft) / BIN_COUNT;
        for (int i = 0; i < bins.length; i++) {
            float barHeight = (chartBottom - chartTop) * bins[i] / max;
            paint.setColor(i >= BIN_COUNT - 2 && bins[i] > 0
                    ? Color.rgb(255, 75, 45) : Color.rgb(225, 150, 105));
            float left = chartLeft + i * barWidth;
            canvas.drawRect(left, chartBottom - barHeight, left + Math.max(1, barWidth), chartBottom, paint);
        }
        paint.setColor(Color.rgb(220, 130, 95));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(11);
        canvas.drawText(mode, 5, 11, paint);
        int shadows = bins[0] + bins[1];
        int highlights = bins[BIN_COUNT - 1] + bins[BIN_COUNT - 2];
        if (shadows > max / 8) canvas.drawText("S", width - 26, 11, paint);
        if (highlights > max / 8) {
            paint.setColor(Color.rgb(255, 75, 45));
            canvas.drawText("H", width - 14, 11, paint);
        }
    }
}
