package com.github.ma1co.pmcademo.app;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.github.ma1co.openmemories.framework.ImageInfo;
import com.github.ma1co.openmemories.framework.MediaManager;
import java.io.InputStream;

/** Full-screen card playback opened by the camera playback button. */
public class PlaybackActivity extends BaseActivity {
    private MediaManager mediaManager;
    private Cursor images;
    private PlaybackZoomView imageView;
    private PhotoHistogramView histogramView;
    private TextView info;
    private Bitmap bitmap;
    private int position, loadGeneration;
    private boolean fnHeld, displayHeld;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        imageView = new PlaybackZoomView(this);
        root.addView(imageView, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        histogramView = new PhotoHistogramView(this);
        FrameLayout.LayoutParams histogramLayout = new FrameLayout.LayoutParams(180, 104,
                Gravity.RIGHT | Gravity.BOTTOM);
        histogramLayout.rightMargin = 7; histogramLayout.bottomMargin = 34;
        root.addView(histogramView, histogramLayout);
        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        info.setPadding(8, 4, 8, 4);
        info.setBackgroundColor(0x98000000);
        root.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        try {
            mediaManager = MediaManager.create(this);
            images = mediaManager.queryNewestImages();
            if (images == null || images.getCount() == 0) info.setText("No photos on card  |  PLAY/TRASH BACK");
            else showImage(0);
        } catch (Throwable error) {
            Logger.error("Playback open: " + error);
            info.setText("Playback unavailable: " + error.getClass().getSimpleName() + "  |  PLAY/TRASH BACK");
        }
    }

    private void showImage(int requestedPosition) {
        if (images == null || images.getCount() == 0) return;
        position = Math.max(0, Math.min(images.getCount() - 1, requestedPosition));
        if (!images.moveToPosition(position)) return;
        final long imageId = mediaManager.getImageId(images);
        final int displayPosition = position;
        final int imageCount = images.getCount();
        final int token = ++loadGeneration;
        info.setText("Loading " + (displayPosition + 1) + "/" + imageCount + "...");
        new Thread(new Runnable() { public void run() {
            Bitmap loaded = null;
            int[] histogram = null;
            String details;
            try {
                ImageInfo image = mediaManager.getImageInfo(imageId);
                InputStream stream = image.getPreviewImage();
                try { loaded = BitmapFactory.decodeStream(stream); }
                finally { if (stream != null) try { stream.close(); } catch (Throwable ignored) {} }
                loaded = rotate(loaded, image.getOrientation());
                histogram = PhotoHistogramView.calculate(loaded);
                details = image.getFilename() + "  " + (displayPosition + 1) + "/" + imageCount
                        + "  ISO " + image.getIso() + "  F" + image.getAperture()
                        + "  LEFT/RIGHT BROWSE  CENTER ZOOM  FN/DISP HIST  PLAY/TRASH BACK";
            } catch (Throwable error) {
                Logger.error("Playback image: " + error);
                details = "Cannot read photo: " + error.getClass().getSimpleName() + "  |  PLAY/TRASH BACK";
            }
            final Bitmap result = loaded;
            final int[] histogramResult = histogram;
            final String text = details;
            runOnUiThread(new Runnable() { public void run() {
                if (token != loadGeneration || isFinishing()) {
                    if (result != null) result.recycle();
                    return;
                }
                Bitmap old = bitmap; bitmap = result;
                imageView.setBitmap(result);
                histogramView.setBins(histogramResult);
                if (old != null && old != result) old.recycle();
                info.setText(text);
            }});
        }}, "PhotoPlayback").start();
    }

    private Bitmap rotate(Bitmap source, int orientation) {
        if (source == null) return null;
        int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        if (degrees == 0) return source;
        Matrix matrix = new Matrix(); matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }

    private void browse(int direction) { showImage(position + direction); }
    @Override protected boolean onLeftKeyDown() {
        if (imageView.isZoomed()) imageView.move(-1, 0); else browse(-1); return true;
    }
    @Override protected boolean onRightKeyDown() {
        if (imageView.isZoomed()) imageView.move(1, 0); else browse(1); return true;
    }
    @Override protected boolean onUpKeyDown() {
        if (imageView.isZoomed()) imageView.move(0, -1); else browse(-1); return true;
    }
    @Override protected boolean onDownKeyDown() {
        if (imageView.isZoomed()) imageView.move(0, 1); else browse(1); return true;
    }
    @Override protected boolean onEnterKeyDown() {
        int factor = imageView.cycleZoom();
        info.setVisibility(View.VISIBLE);
        info.setText(factor + "x  " + (factor == 1
                ? "LEFT/RIGHT BROWSE" : "ARROWS MOVE")
                + "  CENTER ZOOM  FN/DISP HIST  PLAY/TRASH BACK");
        return true;
    }
    private boolean toggleHistogram() {
        histogramView.setVisibility(histogramView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        return true;
    }
    @Override protected boolean onFnKeyDown() {
        if (!fnHeld) { fnHeld = true; toggleHistogram(); } return true;
    }
    @Override protected boolean onFnKeyUp() { fnHeld = false; return true; }
    @Override protected boolean onDisplayKeyDown() {
        if (!displayHeld) { displayHeld = true; toggleHistogram(); } return true;
    }
    @Override protected boolean onDisplayKeyUp() { displayHeld = false; return true; }
    @Override protected boolean onPlayKeyDown() { finish(); return true; }
    @Override protected boolean onPlayKeyUp() { return true; }

    @Override protected void onDestroy() {
        ++loadGeneration;
        if (images != null) images.close();
        if (bitmap != null) { imageView.setBitmap(null); bitmap.recycle(); bitmap = null; }
        super.onDestroy();
    }
}
