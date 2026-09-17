package com.github.ma1co.pmcademo.app;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.util.Pair;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.KeyEvent;
import com.sony.scalar.sysutil.ScalarInput;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.sony.scalar.hardware.CameraEx;
import java.util.List;

/** Full-screen camera-owned preview. Enhancement parameters never become capture parameters. */
public class LiveViewActivity extends BaseActivity implements SurfaceHolder.Callback {
    private final Handler handler = new Handler();
    private CameraEx camera;
    private SurfaceHolder surface;
    private TextView settings, sequenceSettings, message, help;
    private LinearLayout bottomPanel;
    private HistogramView histogram;
    private SharedPreferences preferences;
    private final CapturePlan plan = new CapturePlan();
    private int shotCount = 1, delaySeconds = 2, intervalSeconds;
    private int magnificationLevel, magnificationFactor, magX, magY, magLimit;
    private List magnificationLevels;
    private boolean consumeDelete;
    private final Runnable sequenceTick = new Runnable() { public void run() { tickSequence(); }};
    private SurfaceView previewView;
    private FocusMapView focusMap;
    private float previewAspect = 1.5f;
    private boolean active, preview, busy, enhanced, magnified, slowSupported, adjusting, captureFault;
    private boolean cleanPreview;
    private boolean slowApplied, isoApplied, previewModeApplied, restorePending;
    private int selected, photoIso, boostIso, generation;
    private String originalSlow, originalPreviewMode;
    private List isoValues;
    private String notice = "Initializing camera...";
    private int previewWidth, previewHeight;
    private long lastHistogramAt;
    private volatile boolean histogramReceived;
    private boolean nativeHistogramSupported;
    private final Camera.PreviewCallback histogramCallback = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera source) {
            if (!active || data == null || histogram == null || previewWidth <= 0 || previewHeight <= 0) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastHistogramAt < 300) return;
            lastHistogramAt = now;
            double exposureScale = enhanced && isoApplied && boostIso > 0
                    ? (double) photoIso / boostIso : 1.0;
            String histogramMode = enhanced
                    ? (isoApplied ? "EXP EST" : "VIEW") : "LIVE";
            histogramReceived = true;
            histogram.update(data, previewWidth, previewHeight, exposureScale, histogramMode);
        }
    };
    private final CameraEx.PreviewAnalizeListener nativeHistogramListener = new CameraEx.PreviewAnalizeListener() {
        public void onAnalizedData(CameraEx.AnalizedData data, CameraEx source) {
            if (!active || source != camera || data == null || data.hist == null) return;
            short[] nativeBins = usableHistogram(data.hist.Y) ? data.hist.Y
                    : usableHistogram(data.hist.G) ? data.hist.G
                    : usableHistogram(data.hist.R) ? data.hist.R : data.hist.B;
            if (!usableHistogram(nativeBins)) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastHistogramAt < 300) return;
            lastHistogramAt = now;
            double exposureScale = enhanced && isoApplied && boostIso > 0
                    ? (double) photoIso / boostIso : 1.0;
            String histogramMode = enhanced
                    ? (isoApplied ? "EXP EST" : "VIEW") : "LIVE";
            if (!histogramReceived) Logger.info("Native histogram received: " + nativeBins.length + " bins");
            histogramReceived = true;
            histogram.update(nativeBins, exposureScale, histogramMode);
        }
    };

    private static boolean usableHistogram(short[] bins) {
        if (bins == null || bins.length == 0) return false;
        for (int i = 0; i < bins.length; i++) if ((bins[i] & 0xffff) != 0) return true;
        return false;
    }

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        preferences = getSharedPreferences("liveview", MODE_PRIVATE);
        shotCount = clamp(preferences.getInt("shots", 1), 1, 999);
        delaySeconds = clamp(preferences.getInt("delay", 2), 0, 3600);
        intervalSeconds = clamp(preferences.getInt("interval", 0), 0, 3600);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView view = new SurfaceView(this) {
            @Override protected void onMeasure(int ws, int hs) {
                int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
                if (w > h * previewAspect) w = Math.round(h * previewAspect);
                else h = Math.round(w / previewAspect);
                setMeasuredDimension(w, h);
            }
        };
        previewView = view;
        view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        view.getHolder().addCallback(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        focusMap = new FocusMapView(this);
        FrameLayout.LayoutParams mapLayout = new FrameLayout.LayoutParams(112,80,Gravity.TOP | Gravity.RIGHT);
        mapLayout.topMargin=8; mapLayout.rightMargin=8; root.addView(focusMap,mapLayout);
        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(0, 0, 168, 0);
        settings = line("", 20); sequenceSettings = line("", 16); message = line(notice, 15);
        help = line("C1 STAR/CLEAN  |  C2/AEL MAG  |  FN SELECT  |  LEFT/RIGHT CHANGE\nSHUTTER START/STOP  |  MENU BULB  |  PLAY REVIEW  |  TRASH EXIT", 13);
        bottomPanel.addView(settings); bottomPanel.addView(sequenceSettings); bottomPanel.addView(message); bottomPanel.addView(help);
        root.addView(bottomPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        histogram = new HistogramView(this);
        FrameLayout.LayoutParams histogramLayout = new FrameLayout.LayoutParams(156, 92, Gravity.RIGHT | Gravity.BOTTOM);
        histogramLayout.rightMargin = 6; histogramLayout.bottomMargin = 6;
        root.addView(histogram, histogramLayout);
        setContentView(root);
    }

    private TextView line(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setTextColor(Color.rgb(230, 115, 85)); v.setBackgroundColor(0xB0000000);
        v.setPadding(10, 3, 10, 3); return v;
    }

    @Override protected void setColorDepth(boolean high) { super.setColorDepth(false); }

    @Override protected void onResume() {
        super.onResume(); active = true; final int token = ++generation;
        setAutoPowerOffMode(false);
        new Thread(new Runnable() { public void run() {
            CameraEx opened = null;
            try {
                opened = CameraEx.open(0, null);
                final CameraEx ready = opened;
                handler.post(new Runnable() { public void run() {
                    if (!active || token != generation) { ready.release(); return; }
                    camera = ready;
                    try {
                        if (preferences.getBoolean("pending_restore", false)) {
                            photoIso = preferences.getInt("restore_iso", 1600);
                            originalSlow = preferences.getString("restore_slow", null);
                            originalPreviewMode = preferences.getString("restore_preview", null);
                            isoApplied = preferences.getBoolean("restore_iso_applied", false);
                            slowApplied = preferences.getBoolean("restore_slow_applied", false);
                            previewModeApplied = preferences.getBoolean("restore_preview_applied", false);
                            restorePending = true;
                            if (!restoreEnhancement()) {
                                releaseCamera(); message.setText("Restoration failed - exit and reopen app"); return;
                            }
                        }
                        Camera.Parameters p = camera.getNormalCamera().getParameters();
                        CameraEx.ParametersModifier m = camera.createParametersModifier(p);
                        Camera.Size size = p.getPreviewSize();
                        if (size != null && size.height > 0) {
                            previewWidth = size.width; previewHeight = size.height;
                            previewAspect = (float)size.width / size.height;
                            previewView.requestLayout();
                        }
                        photoIso = m.getISOSensitivity(); isoValues = m.getSupportedISOSensitivities();
                        boostIso = nearestIso(Math.max(12800, photoIso));
                        p.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
                        p.setSceneMode(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE);
                        m.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE);
                        camera.getNormalCamera().setParameters(p);
                        slowSupported = false;
                        try {
                            slowSupported = m.isSupportedSlowShutterLiveviewMode();
                            originalSlow = m.getSlowShutterLiveviewMode();
                            if (originalSlow == null) slowSupported = false;
                        } catch (Throwable unavailable) { Logger.info("Slow live view unavailable: " + unavailable); }
                        try { originalPreviewMode = m.getShootingPreviewMode(); }
                        catch (Throwable unavailable) { originalPreviewMode = null; }
                        try {
                            nativeHistogramSupported = m.isSupportedPreviewAnalize();
                            if (nativeHistogramSupported) camera.setPreviewAnalizeListener(nativeHistogramListener);
                            Logger.info("Native preview histogram supported: " + nativeHistogramSupported);
                        } catch (Throwable unavailable) {
                            nativeHistogramSupported = false;
                            Logger.info("Native preview histogram unavailable: " + unavailable);
                        }
                        setupMagnification(m);
                        camera.setShutterListener(new CameraEx.ShutterListener() {
                            public void onShutter(final int result, final CameraEx source) {
                                handler.post(new Runnable() { public void run() {
                                    if (!active || source != camera || !busy) return;
                                    Logger.info("Live capture callback: " + result + " completed=" + plan.completed());
                                    if (result == CameraEx.ShutterListener.STATUS_OK) completeCapture(true);
                                    else if (result == CameraEx.ShutterListener.STATUS_ERROR) completeCapture(false);
                                }});
                            }
                        });
                        Logger.info("Live view ready; slowSupported=" + slowSupported);
                        startPreview(); render("Ready - manual focus; M mode recommended");
                    } catch (Throwable error) { fail("Camera setup", error); releaseCamera(); }
                }});
            } catch (final Throwable error) {
                if (opened != null) try { opened.release(); } catch (Throwable ignored) {}
                handler.post(new Runnable() { public void run() {
                    if (active && token == generation) fail("Camera open", error);
                }});
            }
        }}, "LiveViewOpen").start();
    }

    @Override protected void onPause() {
        active = false; ++generation; handler.removeCallbacksAndMessages(null);
        plan.fail(); releaseCamera(); setAutoPowerOffMode(true); super.onPause();
    }

    private void releaseCamera() {
        if (camera != null) {
            if (busy || captureFault) try { camera.cancelTakePicture(); } catch (Throwable e) { Logger.error("Cancel: " + e); }
            restoreEnhancement();
            try { camera.stopPreviewMagnification(); } catch (Throwable ignored) {}
            try { camera.setPreviewAnalizeListener(null); } catch (Throwable ignored) {}
            try { camera.getNormalCamera().setPreviewCallback(null); } catch (Throwable ignored) {}
            try { camera.getNormalCamera().stopPreview(); } catch (Throwable ignored) {}
            try { camera.release(); } catch (Throwable e) { Logger.error("Release: " + e); }
        }
        camera = null; busy = preview = enhanced = magnified = adjusting = captureFault = false;
        magnificationLevel = magnificationFactor = 0;
        focusMap.update(0,0,0);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { surface = holder; startPreview(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surface = null; preview = false;
        if (camera != null && !busy) try { camera.getNormalCamera().stopPreview(); } catch (Throwable ignored) {}
    }
    private void startPreview() {
        if (camera == null || surface == null || preview || busy) return;
        try {
            camera.getNormalCamera().setPreviewDisplay(surface);
            histogramReceived = false;
            histogram.waiting();
            if (!nativeHistogramSupported) {
                try { camera.getNormalCamera().setPreviewCallback(histogramCallback); }
                catch (Throwable error) { Logger.info("Histogram preview callback unavailable: " + error); }
            }
            camera.getNormalCamera().startPreview(); preview = true;
            handler.postDelayed(new Runnable() { public void run() {
                if (!active || !preview || histogramReceived || camera == null) return;
                try {
                    camera.getNormalCamera().setPreviewCallback(histogramCallback);
                    Logger.info("Native histogram timed out; Android preview fallback enabled");
                    histogram.waiting();
                } catch (Throwable error) {
                    Logger.info("Histogram fallback unavailable: " + error);
                }
                handler.postDelayed(new Runnable() { public void run() {
                    if (active && preview && !histogramReceived) histogram.noData();
                }}, 2500);
            }}, 2500);
        } catch (Throwable error) { fail("Preview", error); }
    }
    private boolean idle() { return camera != null && preview && !busy && !plan.running() && !restorePending && !adjusting && !captureFault; }

    private void enableEnhancement() {
        if (!idle()) return;
        try {
            Camera.Parameters p = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier m = camera.createParametersModifier(p);
            photoIso = m.getISOSensitivity();
            // Try the sensor's slow live-view mode first; this is optional on old firmware.
            if (slowSupported) {
                try {
                    m.setSlowShutterLiveviewMode(CameraEx.ParametersModifier.SLOW_SHUTTER_LIVEVIEW_MODE_ON);
                    slowApplied = true;
                    checkpointEnhancement();
                    camera.getNormalCamera().setParameters(p);
                    Camera.Parameters check = camera.getNormalCamera().getParameters();
                    if (!"on".equals(camera.createParametersModifier(check).getSlowShutterLiveviewMode()))
                        throw new IllegalStateException("Slow live view was not accepted");
                } catch (Throwable error) {
                    Logger.info("Slow view rejected: " + error);
                    if (!restoreEnhancement()) return;
                    slowSupported = false;
                }
            }
            p = camera.getNormalCamera().getParameters(); m = camera.createParametersModifier(p);
            // High ISO is a fallback only. Capture always restores photoIso first.
            if (!slowSupported) {
                m.setISOSensitivity(boostIso);
                isoApplied = true;
            }
            List modes = null;
            try { modes = m.getSupportedShootingPreviewModes(); } catch (Throwable unavailable) {}
            String mode = slowSupported ? "off" : "iris_ss_iso";
            if (originalPreviewMode != null && modes != null && modes.contains(mode)) {
                m.setShootingPreviewMode(mode); previewModeApplied = true;
            }
            checkpointEnhancement();
            camera.getNormalCamera().setParameters(p);
            enhanced = true;
            Logger.info("Star enabled: " + (slowSupported ? "slow" : "ISO="+boostIso) + "; photoISO="+photoIso);
            render("STAR VIEW");
        } catch (Throwable error) { restoreEnhancement(); fail("Star view", error); }
    }

    private void checkpointEnhancement() {
        if (!preferences.edit().putBoolean("pending_restore", true)
                .putInt("restore_iso", photoIso).putString("restore_slow", originalSlow)
                .putString("restore_preview", originalPreviewMode)
                .putBoolean("restore_iso_applied", isoApplied)
                .putBoolean("restore_slow_applied", slowApplied)
                .putBoolean("restore_preview_applied", previewModeApplied).commit())
            throw new IllegalStateException("Cannot save restoration checkpoint");
    }

    private boolean restoreEnhancement() {
        if (camera == null) return !restorePending;
        try {
            if (isoApplied || slowApplied || previewModeApplied) {
                Camera.Parameters p = camera.getNormalCamera().getParameters();
                CameraEx.ParametersModifier m = camera.createParametersModifier(p);
                if (isoApplied) m.setISOSensitivity(photoIso);
                if (slowApplied) m.setSlowShutterLiveviewMode(originalSlow);
                if (previewModeApplied) m.setShootingPreviewMode(originalPreviewMode);
                camera.getNormalCamera().setParameters(p);
                CameraEx.ParametersModifier check = camera.createParametersModifier(camera.getNormalCamera().getParameters());
                if (isoApplied && check.getISOSensitivity() != photoIso)
                    throw new IllegalStateException("ISO restoration was not accepted");
                if (slowApplied && !originalSlow.equals(check.getSlowShutterLiveviewMode()))
                    throw new IllegalStateException("Slow live view restoration was not accepted");
                if (previewModeApplied && !originalPreviewMode.equals(check.getShootingPreviewMode()))
                    throw new IllegalStateException("Preview restoration was not accepted");
            }
            if ((isoApplied || slowApplied || previewModeApplied || restorePending)
                    && !preferences.edit().putBoolean("pending_restore", false).commit())
                throw new IllegalStateException("Cannot clear restoration checkpoint");
            isoApplied = slowApplied = previewModeApplied = restorePending = enhanced = false;
            return true;
        } catch (Throwable error) {
            restorePending = true; fail("Restore failed; C1 retries, capture blocked", error); return false;
        }
    }

    private void adjust(int direction) {
        if (!idle() || magnified) return;
        if (selected >= 3) {
            if (selected == 3) shotCount = clamp(shotCount + direction, 1, 999);
            if (selected == 4) delaySeconds = clamp(delaySeconds + direction, 0, 3600);
            if (selected == 5) intervalSeconds = clamp(intervalSeconds + direction, 0, 3600);
            preferences.edit().putInt("shots", shotCount).putInt("delay", delaySeconds)
                    .putInt("interval", intervalSeconds).commit();
            render("Sequence settings updated"); return;
        }
        boolean resumeStar = enhanced;
        if (!restoreEnhancement()) return;
        try {
            if (selected == 0) {
                if (direction > 0) camera.incrementShutterSpeed(); else camera.decrementShutterSpeed();
            } else if (selected == 1) {
                if (direction > 0) camera.incrementAperture(); else camera.decrementAperture();
            } else {
                Camera.Parameters p = camera.getNormalCamera().getParameters();
                CameraEx.ParametersModifier m = camera.createParametersModifier(p);
                int next = LiveViewValues.stepIso(isoValues, m.getISOSensitivity(), direction);
                m.setISOSensitivity(next); camera.getNormalCamera().setParameters(p); photoIso = next;
            }
            // Dial updates can settle asynchronously in the Sony camera service.
            adjusting = true;
            final boolean restoreStar = resumeStar;
            handler.postDelayed(new Runnable() { public void run() {
                adjusting = false;
                if (!active || camera == null) return;
                if (restoreStar) enableEnhancement();
                if (!restorePending) render("Photo settings updated");
            }}, 180);
        } catch (Throwable error) {
            fail("Parameter", error);
            if (resumeStar) enableEnhancement();
        }
    }

    private int nearestIso(int target) {
        return LiveViewValues.nearestIso(isoValues, photoIso, target);
    }

    private void shoot() {
        if (plan.running()) { stopSequence(); return; }
        if (!idle()) return;
        final boolean resumeStar = enhanced;
        if (!restoreEnhancement()) return;
        try {
            stopMagnification();
            Camera.Parameters p = camera.getNormalCamera().getParameters();
            Pair speed = camera.createParametersModifier(p).getShutterSpeed();
            if (speed == null || ShutterDisplay.isBulb(((Integer)speed.first).intValue(),
                    ((Integer)speed.second).intValue())) {
                if (resumeStar) enableEnhancement();
                render("BULB: MENU opens timed exposure / sequence"); return;
            }
            resumeAfterCapture = resumeStar;
            Logger.info("Live sequence start: shots=" + shotCount + " delay=" + delaySeconds
                    + " interval=" + intervalSeconds + " shutter=" + speed + " ISO=" + photoIso);
            plan.start(SystemClock.elapsedRealtime(), shotCount, delaySeconds, intervalSeconds);
            tickSequence();
        } catch (Throwable error) {
            plan.fail(); busy = false; resumeAfterCapture = false; fail("Capture", error);
            if (resumeStar) enableEnhancement();
        }
    }
    private void tickSequence() {
        if (!active || camera == null || !plan.running()) return;
        if (plan.takeDue(SystemClock.elapsedRealtime())) {
            try {
                busy = true;
                render("Exposing / saving " + (plan.completed()+1) + "/" + plan.target());
                camera.burstableTakePicture();
            } catch (Throwable error) {
                plan.fail(); busy = false; captureFault = true;
                try { camera.cancelTakePicture(); } catch (Throwable cancelError) { Logger.error("Capture cancel: " + cancelError); }
                finishPlan("Capture failed - exit/reopen: " + error.getClass().getSimpleName());
                Logger.error("Capture: " + error);
            }
        } else if (!busy) {
            render((plan.completed() == 0 ? "Starting in " : "Next photo in ")
                    + ((plan.remainingMillis(SystemClock.elapsedRealtime())+999)/1000) + "s"
                    + "  " + plan.completed() + "/" + plan.target());
            handler.postDelayed(sequenceTick, 200);
        }
    }
    private void stopSequence() {
        Logger.info("Live sequence stop requested; inFlight=" + plan.inFlight());
        plan.stop(); handler.removeCallbacks(sequenceTick);
        if (busy) render("Stopping after current photo is saved...");
        else finishPlan("Stopped: " + plan.completed() + " photos");
    }
    private void finishPlan(String text) {
        Logger.info("Live sequence finished: " + text);
        handler.removeCallbacks(sequenceTick);
        boolean resume = resumeAfterCapture; resumeAfterCapture = false;
        if (resume) enableEnhancement();
        if (!restorePending) render(text);
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        int scan = event.getScanCode();
        if (scan == ScalarInput.ISV_KEY_CUSTOM2) {
            if (event.getRepeatCount() == 0) magnify(); return true;
        }
        // Keep this manual-focus workflow from dispatching an AF/MF toggle to the base app.
        if (scan == ScalarInput.ISV_KEY_AFMF || scan == ScalarInput.ISV_KEY_AEL_AFMF) {
            if (event.getRepeatCount() == 0) magnify(); return true;
        }
        if (event.getRepeatCount() > 0 && (scan == ScalarInput.ISV_KEY_CUSTOM1
                || scan == ScalarInput.ISV_KEY_AEL || scan == ScalarInput.ISV_KEY_ENTER
                || scan == ScalarInput.ISV_KEY_S2 || scan == ScalarInput.ISV_KEY_MENU
                || scan == ScalarInput.ISV_KEY_FN)) return true;
        return super.onKeyDown(code, event);
    }
    private boolean resumeAfterCapture;
    private void completeCapture(boolean success) {
        if (!plan.inFlight()) return;
        busy = false;
        // Release the burstable request before accepting another physical shutter press.
        try { camera.cancelTakePicture(); } catch (Throwable error) {
            plan.fail(); captureFault = true;
            fail("Release capture failed - exit and reopen", error); return;
        }
        if (success) plan.complete(SystemClock.elapsedRealtime()); else plan.fail();
        preview = false; startPreview();
        if (!preview) { plan.fail(); finishPlan("Preview unavailable - reopen app"); return; }
        if (plan.running()) handler.postDelayed(sequenceTick, 250);
        else finishPlan(success ? "Complete: " + plan.completed() + " photos" : "Capture error - check card");
    }
    private void stopMagnification() {
        if (magnified) { camera.stopPreviewMagnification(); magnified = false; magnificationLevel = 0; focusMap.update(0,0,0); }
    }
    private void setupMagnification(CameraEx.ParametersModifier m) {
        try { magnificationLevels = m.getSupportedPreviewMagnification(); }
        catch (Throwable unavailable) { magnificationLevels = null; Logger.info("Magnification levels unavailable: " + unavailable); }
        try {
            camera.setPreviewMagnificationListener(new CameraEx.PreviewMagnificationListener() {
                public void onChanged(final boolean enabled, final int factor, final int level,
                        final Pair coords, final CameraEx source) {
                    handler.post(new Runnable() { public void run() {
                        if (!active || source != camera) return;
                        magnified = enabled; magnificationLevel = enabled ? level : 0;
                        magnificationFactor = enabled ? factor : 0;
                        magLimit = enabled && factor > 100 ? 1000 - 100000 / factor : 0;
                        if (coords != null) { magX = ((Integer)coords.first).intValue(); magY = ((Integer)coords.second).intValue(); }
                        focusMap.update(magnificationFactor,magX,magY);
                        render(enabled ? "Focus " + factor/100.0 + "x - arrows move; AEL cycles; half shutter returns" : "Full frame");
                    }});
                }
                public void onInfoUpdated(boolean enabled, Pair coords, CameraEx source) {}
            });
        } catch (Throwable unavailable) { Logger.info("Magnification capability: " + unavailable); }
    }
    private void magnify() {
        if (!idle()) return;
        try {
            int next = 0;
            if (magnificationLevels != null) for (Object entry : magnificationLevels) {
                if (!(entry instanceof Integer)) continue;
                int level = ((Integer)entry).intValue();
                if (level > magnificationLevel && (next == 0 || level < next)) next = level;
            }
            if (next > 0) camera.setPreviewMagnification(next, new Pair<Integer,Integer>(magX, magY));
            else if (magnified) stopMagnification();
            else camera.setAutoPreviewMagnification();
        } catch (Throwable error) { fail("Magnifier unavailable", error); }
    }
    private void moveMagnification(int x, int y) {
        if (!idle() || !magnified) return;
        int step = Math.max(1, 50000 / Math.max(100, magnificationFactor));
        int nextX = clamp(magX + x * step, -magLimit, magLimit);
        int nextY = clamp(magY + y * step, -magLimit, magLimit);
        try {
            camera.setPreviewMagnification(magnificationLevel, new Pair<Integer,Integer>(nextX, nextY));
            magX = nextX; magY = nextY; focusMap.update(magnificationFactor,magX,magY);
        } catch (Throwable error) { fail("Move focus area", error); }
    }
    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private void render(String text) {
        notice = text; message.setText(text);
        sequenceSettings.setText((selected == 3 ? "[SHOTS " + shotCount + "]" : "SHOTS " + shotCount)
                + "    " + (selected == 4 ? "[DELAY " + delaySeconds + "s]" : "DELAY " + delaySeconds + "s")
                + "    " + (selected == 5 ? "[INTERVAL " + intervalSeconds + "s]" : "INTERVAL " + intervalSeconds + "s"));
        updateOverlayVisibility();
        if (camera == null || busy) return;
        try {
            Camera.Parameters p = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier m = camera.createParametersModifier(p);
            Pair s = m.getShutterSpeed();
            String speed = s == null ? "--" : ShutterDisplay.format(
                    ((Integer)s.first).intValue(), ((Integer)s.second).intValue());
            int aperture = m.getAperture();
            String[] fields = {"SS " + speed, aperture > 0 ? "F " + aperture/100.0 : "F --",
                    "ISO " + (enhanced && isoApplied ? photoIso : m.getISOSensitivity())};
            settings.setText((enhanced ? "STAR VIEW    " : "")
                    + (selected == 0 ? "["+fields[0]+"]" : fields[0]) + "    "
                    + (selected == 1 ? "["+fields[1]+"]" : fields[1]) + "    "
                    + (selected == 2 ? "["+fields[2]+"]" : fields[2]));
        } catch (Throwable error) { Logger.error("Read display: " + error); }
    }
    private void updateOverlayVisibility() {
        int visibility = cleanPreview ? View.GONE : View.VISIBLE;
        bottomPanel.setVisibility(visibility);
        histogram.setVisibility(visibility);
        if (cleanPreview) focusMap.setVisibility(View.GONE);
        else if (magnified && magnificationFactor > 100) focusMap.setVisibility(View.VISIBLE);
    }
    private void fail(String operation, Throwable error) {
        Logger.error(operation + ": " + error); message.setText(operation + ": " + error.getClass().getSimpleName());
    }
    @Override protected boolean onC1KeyDown() {
        if (busy || plan.running() || adjusting || captureFault) return true;
        if (restorePending) {
            cleanPreview = false;
            if (restoreEnhancement()) render("Normal preview");
        } else if (!enhanced && !cleanPreview) {
            cleanPreview = true;
            render("Normal clean preview - C1 opens STAR VIEW");
        } else if (!enhanced) {
            cleanPreview = false;
            enableEnhancement();
        } else if (!cleanPreview) {
            cleanPreview = true;
            render("STAR clean preview - C1 returns to normal");
        } else {
            cleanPreview = false;
            if (restoreEnhancement()) render("Normal preview");
        }
        return true;
    }
    @Override protected boolean onAelKeyDown() { magnify(); return true; }
    @Override protected boolean onFnKeyDown() { if (idle()) { selected=(selected+1)%6; render(notice); } return true; }
    @Override protected boolean onUpKeyDown() { if (magnified) { moveMagnification(0,-1); return true; } return onFnKeyDown(); }
    @Override protected boolean onDownKeyDown() { if (magnified) { moveMagnification(0,1); return true; } if (idle()) { selected=(selected+5)%6; render(notice); } return true; }
    @Override protected boolean onLeftKeyDown() { if (magnified) moveMagnification(-1,0); else adjust(-1); return true; }
    @Override protected boolean onRightKeyDown() { if (magnified) moveMagnification(1,0); else adjust(1); return true; }
    @Override protected boolean onUpperDialChanged(int value) { if (value != 0) adjust(value > 0 ? 1 : -1); return true; }
    @Override protected boolean onLowerDialChanged(int value) { return onUpperDialChanged(value); }
    @Override protected boolean onEnterKeyDown() {
        if (magnified && idle()) {
            try { camera.setPreviewMagnification(magnificationLevel, new Pair<Integer,Integer>(0,0)); magX=magY=0; focusMap.update(magnificationFactor,0,0); }
            catch (Throwable error) { fail("Center focus area",error); }
        } else shoot(); return true;
    }
    @Override protected boolean onShutterKeyDown() { shoot(); return true; }
    @Override protected boolean onShutterKeyUp() { return true; }
    @Override protected boolean onFocusKeyDown() {
        if (idle()) try { stopMagnification(); render(notice); } catch (Throwable e) { fail("Magnifier",e); }
        return true;
    }
    @Override protected boolean onFocusKeyUp() { return true; }
    @Override protected boolean onMenuKeyDown() {
        if (idle() && restoreEnhancement()) startActivity(new Intent(this, MainActivity.class)); return true;
    }
    @Override protected boolean onPlayKeyDown() {
        if (!idle()) { message.setText("Wait for current operation before playback"); return true; }
        if (restoreEnhancement()) startActivity(new Intent(this, PlaybackActivity.class));
        return true;
    }
    @Override protected boolean onPlayKeyUp() { return true; }
    @Override protected boolean onDeleteKeyDown() {
        if (plan.running()) { consumeDelete = true; stopSequence(); } return true;
    }
    @Override protected boolean onDeleteKeyUp() {
        if (consumeDelete) { consumeDelete = false; return true; }
        onBackPressed(); return true;
    }
    @Override public void onBackPressed() {
        if (plan.running()) { stopSequence(); return; }
        if (busy) { message.setText("Exposing / saving - wait before exiting"); return; }
        if (camera == null || restoreEnhancement()) super.onBackPressed();
    }
}
