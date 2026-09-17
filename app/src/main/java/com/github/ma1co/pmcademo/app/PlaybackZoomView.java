package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;

/** Lightweight fit/2x/4x photo viewer controlled by camera buttons. */
final class PlaybackZoomView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private int zoomLevel;
    private float panX, panY;

    PlaybackZoomView(Context context) { super(context); setBackgroundColor(Color.BLACK); }

    void setBitmap(Bitmap value) {
        bitmap = value; zoomLevel = 0; panX = panY = 0; invalidate();
    }

    boolean isZoomed() { return zoomLevel > 0; }
    int zoomFactor() { return zoomLevel == 0 ? 1 : zoomLevel == 1 ? 2 : 4; }

    int cycleZoom() {
        zoomLevel = (zoomLevel + 1) % 3;
        panX = panY = 0;
        invalidate();
        return zoomFactor();
    }

    void move(int horizontal, int vertical) {
        if (!isZoomed() || bitmap == null) return;
        panX += horizontal * getWidth() / 6f;
        panY += vertical * getHeight() / 6f;
        clampPan(); invalidate();
    }

    private float fitScale() {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) return 1;
        return Math.min((float)getWidth() / bitmap.getWidth(), (float)getHeight() / bitmap.getHeight());
    }

    private void clampPan() {
        if (bitmap == null) return;
        float scale = fitScale() * zoomFactor();
        float limitX = Math.max(0, (bitmap.getWidth() * scale - getWidth()) / 2f);
        float limitY = Math.max(0, (bitmap.getHeight() * scale - getHeight()) / 2f);
        panX = Math.max(-limitX, Math.min(limitX, panX));
        panY = Math.max(-limitY, Math.min(limitY, panY));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        clampPan();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        clampPan();
        float scale = fitScale() * zoomFactor();
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * scale) / 2f + panX,
                (getHeight() - bitmap.getHeight() * scale) / 2f + panY);
        canvas.drawBitmap(bitmap, matrix, paint);
    }
}
