package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Luminance histogram calculated from the displayed photo preview. */
final class PhotoHistogramView extends View {
    private static final int BIN_COUNT = 64;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] bins = new int[BIN_COUNT];

    PhotoHistogramView(Context context) { super(context); setVisibility(GONE); }

    static int[] calculate(Bitmap bitmap) {
        int[] result = new int[BIN_COUNT];
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return result;
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int step = Math.max(1, (int)Math.sqrt((double)width * height / 8192.0));
        int[] row = new int[width];
        for (int y = 0; y < height; y += step) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x += step) {
                int color = row[x];
                int luminance = (77 * Color.red(color) + 150 * Color.green(color)
                        + 29 * Color.blue(color)) >> 8;
                result[Math.min(BIN_COUNT - 1, luminance * BIN_COUNT / 256)]++;
            }
        }
        return result;
    }

    void setBins(int[] value) {
        bins = value == null ? new int[BIN_COUNT] : value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xC0000000);
        canvas.drawRect(0, 0, width, height, paint);
        int max = 1;
        for (int i = 0; i < bins.length; i++) if (bins[i] > max) max = bins[i];
        float left = 6, top = 17, right = width - 6, bottom = height - 6;
        float barWidth = (right - left) / bins.length;
        for (int i = 0; i < bins.length; i++) {
            float barHeight = (bottom - top) * bins[i] / max;
            paint.setColor(i >= bins.length - 2 && bins[i] > 0
                    ? Color.rgb(255, 70, 45) : Color.LTGRAY);
            float x = left + i * barWidth;
            canvas.drawRect(x, bottom - barHeight, x + Math.max(1, barWidth), bottom, paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(Color.WHITE);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(12);
        canvas.drawText("PHOTO HIST", 6, 13, paint);
        int highlights = bins[bins.length - 1] + bins[bins.length - 2];
        if (highlights > max / 8) {
            paint.setColor(Color.rgb(255, 70, 45));
            canvas.drawText("H", width - 16, 13, paint);
        }
    }
}
