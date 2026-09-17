package com.github.ma1co.pmcademo.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.sony.scalar.hardware.CameraEx;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/** Long-exposure intervalometer for first-generation Sony PMCA cameras. */
public class MainActivity extends BaseActivity implements SurfaceHolder.Callback {
    private static final int EXPOSURE = 0, ISO = 1, INTERVAL = 2, COUNT = 3, DELAY = 4, WAIT_SAVE = 5, FIELD_TOTAL = 6;
    private static final int IDLE = 0, DELAYING = 1, EXPOSING = 2, SAVING = 3, WAITING = 4;
    private static final int SAVE_BUFFER_MS = 0;
    private static final int SAVE_CALLBACK_TIMEOUT_MS = 6000;
    private static final int CLOSE_ATTEMPTS = 3;

    private final Handler handler = new Handler();
    private final TextView[] values = new TextView[FIELD_TOTAL];
    private final TextView[] labels = new TextView[FIELD_TOTAL];
    private TextView status;
    private TextView help;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;
    private SurfaceView previewView;
    private SurfaceHolder previewHolder;
    private CameraEx camera;
    private Camera.Parameters cameraParameters;
    private CameraEx.ParametersModifier parametersModifier;
    private List supportedIsoValues;
    private SharedPreferences preferences;
    private int selected, exposureSeconds = 180, iso = 1600, intervalSeconds = 5, shotCount = 40, delaySeconds = 10;
    private int completedShots, state = IDLE;
    private long phaseEndsAt;
    private boolean stopping;
    private boolean consumeDeleteRelease;
    private volatile boolean activityActive;
    private boolean previewReady;
    private boolean exposureIsoLink;
    private boolean nightMode = true;
    // 0: night overlay, 1: clean preview, 2: normal overlay.
    private int displayMode;
    private boolean waitForSave = true;
    private boolean sawCancelCallback;
    private boolean saveFinalized;
    private boolean fnHeld;
    private boolean authorChordUsed;
    private boolean a7s2CompatibilityMode;
    private boolean a7s2BulbArmed;
    private int bulbSteps;
    private boolean bulbSetupPending;
    private boolean starEnhanced, starSlowSupported, starSlowApplied, starIsoApplied, starPreviewModeApplied;
    private boolean resumeStarAfterSequence;
    private int resumeStarDisplayMode, starBoostIso;
    private String starOriginalSlow, starOriginalPreviewMode;

    private final Runnable saveCallbackTimeout = new Runnable() {
        public void run() {
            if (state == SAVING && !saveFinalized) {
                Logger.error("Save callback timeout; continuing with safety buffer");
                finalizeSave();
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        public void run() { tick(); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable error) {
                StringWriter output = new StringWriter();
                error.printStackTrace(new PrintWriter(output));
                Logger.error(output.toString());
                if (previousHandler != null) previousHandler.uncaughtException(thread, error);
            }
        });
        preferences = getSharedPreferences("astro", MODE_PRIVATE);
        Logger.info("AstroFish by yxy");
        try {
            String model = getDeviceInfo().getModel();
            String normalizedModel = model == null ? "" : model.toUpperCase(java.util.Locale.US);
            a7s2CompatibilityMode = normalizedModel.indexOf("ILCE-7SM2") >= 0
                    || normalizedModel.indexOf("A7S II") >= 0
                    || normalizedModel.indexOf("A7S2") >= 0;
            Logger.info("Camera model: " + model + (a7s2CompatibilityMode ? " (A7S II compatibility)" : ""));
        } catch (Throwable modelError) {
            Logger.error("Camera model detection warning: " + modelError.toString());
        }
        exposureSeconds = preferences.getInt("exposure", exposureSeconds);
        iso = preferences.getInt("iso", iso);
        intervalSeconds = preferences.getInt("interval", intervalSeconds);
        shotCount = preferences.getInt("count", shotCount);
        delaySeconds = preferences.getInt("delay", delaySeconds);
        exposureIsoLink = preferences.getBoolean("exposure_iso_link", false);
        nightMode = true;
        displayMode = 0;
        waitForSave = preferences.getBoolean("wait_for_save", true);
        buildUi();
        applyNightMode();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        activityActive = true;
        bulbSteps = 0; bulbSetupPending = false;
        setAutoPowerOffMode(false);
        status.setText("Initializing camera...");
        new Thread(new Runnable() {
            public void run() { initializeCamera(); }
        }, "CameraInit").start();
    }

    @Override protected void onPause() {
        activityActive = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacksAndMessages(null);
        if (state == EXPOSING) stopExposure(false);
        state = IDLE;
        if (camera != null) {
            restoreStarEnhancement();
            try { camera.getNormalCamera().stopPreview(); } catch (Throwable ignored) {}
            try { camera.release(); } catch (Throwable ignored) {}
            camera = null;
        }
        previewReady = false;
        a7s2BulbArmed = false;
        starEnhanced = resumeStarAfterSequence = false;
        setAutoPowerOffMode(true);
        super.onPause();
    }

    private void initializeCamera() {
        CameraEx openedCamera = null;
        try {
            Logger.info("Camera open begin");
            openedCamera = CameraEx.open(0, null);
            Logger.info("Camera open complete");
            openedCamera.setShutterListener(new CameraEx.ShutterListener() {
                public void onShutter(int result, CameraEx source) {
                    Logger.info("Shutter callback: " + result);
                    final int callbackResult = result;
                    handler.post(new Runnable() {
                        public void run() { handleShutterCallback(callbackResult); }
                    });
                }
            });
            Logger.info("Camera getParameters begin");
            final Camera.Parameters openedParameters = openedCamera.getNormalCamera().getParameters();
            Logger.info("Camera getParameters complete");
            final CameraEx.ParametersModifier openedModifier = openedCamera.createParametersModifier(openedParameters);
            Logger.info("Camera ISO list begin");
            final List openedIsoValues = openedModifier.getSupportedISOSensitivities();
            Logger.info("Camera ISO list complete");
            final int cameraIso = openedModifier.getISOSensitivity();
            Logger.info("Camera ISO current: " + cameraIso);
            final boolean directShutterSupported = openedModifier.isDirectShutterSupported();
            Logger.info("Direct shutter supported: " + directShutterSupported);
            try {
                String driveMode = openedModifier.getDriveMode();
                Logger.info("Drive mode before setup: " + driveMode);
                if (!CameraEx.ParametersModifier.DRIVE_MODE_SINGLE.equals(driveMode)) {
                    openedModifier.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE);
                    openedCamera.getNormalCamera().setParameters(openedParameters);
                    Logger.info("Drive mode forced to single");
                }
            } catch (Throwable driveError) {
                Logger.error("Drive mode setup warning: " + driveError.toString());
            }
            if (!activityActive) {
                openedCamera.release();
                return;
            }
            final CameraEx readyCamera = openedCamera;
            handler.post(new Runnable() {
                public void run() {
                    if (!activityActive) {
                        try { readyCamera.release(); } catch (Throwable ignored) {}
                        return;
                    }
                    camera = readyCamera;
                    cameraParameters = openedParameters;
                    parametersModifier = openedModifier;
                    supportedIsoValues = openedIsoValues;
                    if (openedIsoValues != null && openedIsoValues.contains(Integer.valueOf(cameraIso))) iso = cameraIso;
                    setupStarCapability();
                    if (!directShutterSupported) status.setText("Direct shutter is not supported");
                    else startCameraPreview();
                    refresh();
                }
            });
        } catch (final Throwable error) {
            if (openedCamera != null) {
                try { openedCamera.release(); } catch (Throwable ignored) {}
            }
            Logger.error("Camera init: " + error.toString());
            handler.post(new Runnable() {
                public void run() {
                    if (activityActive) status.setText("Camera init failed: " + error.getClass().getSimpleName());
                }
            });
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new SurfaceView(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                int height = MeasureSpec.getSize(heightSpec);
                // Fit the 3:2 still-image preview without stretching it.
                if (width * 2 > height * 3) width = height * 3 / 2;
                else height = width * 2 / 3;
                setMeasuredDimension(width, height);
            }
        };
        previewView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        previewView.getHolder().addCallback(this);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(8, 2, 8, 3);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        addField(fields, EXPOSURE, "EXPOSURE");
        addField(fields, ISO, "ISO");
        addField(fields, INTERVAL, "INTERVAL");
        addField(fields, COUNT, "SHOTS");
        addField(fields, DELAY, "START DELAY");
        addField(fields, WAIT_SAVE, "WAIT SAVE");
        topPanel.addView(fields, rowParams(58));
        root.addView(topPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 63, Gravity.TOP));

        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(8, 2, 8, 2);
        status = text("Ready", 19, Color.rgb(100, 220, 255));
        status.setGravity(Gravity.CENTER);
        bottomPanel.addView(status, rowParams(30));
        help = text("UP/DOWN SELECT  LEFT/RIGHT CHANGE  FN LINK  C1 STAR/CLEAN  PLAY REVIEW  CENTER START/STOP", 12, Color.LTGRAY);
        help.setGravity(Gravity.CENTER);
        bottomPanel.addView(help, rowParams(22));
        root.addView(bottomPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 56, Gravity.BOTTOM));
        setContentView(root);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        previewHolder = holder;
        startCameraPreview();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        previewReady = false;
        previewHolder = null;
    }

    private void startCameraPreview() {
        if (camera == null || previewHolder == null || previewReady) return;
        try {
            camera.getNormalCamera().setPreviewDisplay(previewHolder);
            camera.getNormalCamera().startPreview();
            previewReady = true;
            if (a7s2CompatibilityMode) {
                armA7s2BulbMode();
            } else {
                armBulbMode();
            }
        } catch (IOException error) {
            Logger.error("Preview init: " + error.toString());
            status.setText("Preview failed: " + error.getClass().getSimpleName());
        } catch (Throwable error) {
            Logger.error("Preview init: " + error.toString());
            status.setText("Preview failed: " + error.getClass().getSimpleName());
        }
    }

    private void armA7s2BulbMode() {
        a7s2BulbArmed = false;
        try {
            Pair shutter = parametersModifier == null ? null : parametersModifier.getShutterSpeed();
            Logger.info("A7S II shutter before BULB: " + formatShutter(shutter));
            if (!isThirtySecondsOrLonger(shutter) || isBulb(shutter)) {
                status.setText("A7S II: restart app with shutter at 30s");
                Logger.error("A7S II BULB not armed: start the app at 30s, not BULB");
                return;
            }
            status.setText("A7S II: setting BULB...");
            camera.decrementShutterSpeed();
            // Do not read the shutter value after this point. On the A7S II the
            // BULB value query can block indefinitely even though the change worked.
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!activityActive || camera == null) return;
                    a7s2BulbArmed = true;
                    Logger.info("A7S II BULB armed from 30s boundary");
                    status.setText("Ready - A7S II BULB armed");
                }
            }, 700);
        } catch (Throwable error) {
            Logger.error("A7S II BULB setup: " + error.toString());
            status.setText("A7S II: restart app with shutter at 30s");
        }
    }

    private void armBulbMode() {
        bulbSetupPending = true;
        try {
            cameraParameters = camera.getNormalCamera().getParameters();
            parametersModifier = camera.createParametersModifier(cameraParameters);
            Pair shutter = parametersModifier.getShutterSpeed();
            Logger.info("Shutter before BULB: " + formatShutter(shutter));
            if (!isBulb(shutter) && isThirtySecondsOrLonger(shutter)) {
                status.setText("Setting BULB...");
                camera.decrementShutterSpeed();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        refreshCameraParameters();
                        bulbSetupPending = false;
                        status.setText("Ready - BULB armed");
                    }
                }, 500);
            } else if (!isBulb(shutter) && bulbSteps++ < 100) {
                status.setText("Preparing timed exposure...");
                camera.decrementShutterSpeed();
                handler.postDelayed(new Runnable() { public void run() {
                    if (activityActive && camera != null && state == IDLE) armBulbMode();
                }}, 80);
            } else {
                bulbSetupPending = false;
                status.setText(isBulb(shutter) ? "Ready - BULB armed" : "Set M mode and shutter to 30s");
            }
        } catch (Throwable error) {
            bulbSetupPending = false;
            Logger.error("BULB setup: " + error.toString());
            status.setText("Ready - BULB setup warning");
        }
    }

    private void refreshCameraParameters() {
        try {
            cameraParameters = camera.getNormalCamera().getParameters();
            parametersModifier = camera.createParametersModifier(cameraParameters);
            supportedIsoValues = parametersModifier.getSupportedISOSensitivities();
            Logger.info("Shutter after BULB: " + formatShutter(parametersModifier.getShutterSpeed()));
        } catch (Throwable error) {
            Logger.error("Parameter refresh: " + error.toString());
        }
    }

    private boolean isBulb(Pair shutter) {
        if (shutter == null || !(shutter.first instanceof Integer) || !(shutter.second instanceof Integer)) return false;
        int numerator = ((Integer) shutter.first).intValue();
        int denominator = ((Integer) shutter.second).intValue();
        return ShutterDisplay.isBulb(numerator, denominator);
    }

    private String formatShutter(Pair shutter) {
        if (shutter == null) return "null";
        if (!(shutter.first instanceof Integer) || !(shutter.second instanceof Integer)) return String.valueOf(shutter);
        return ShutterDisplay.format(((Integer)shutter.first).intValue(), ((Integer)shutter.second).intValue());
    }

    private boolean isThirtySecondsOrLonger(Pair shutter) {
        if (shutter == null || !(shutter.first instanceof Integer) || !(shutter.second instanceof Integer)) return false;
        int numerator = ((Integer) shutter.first).intValue();
        int denominator = ((Integer) shutter.second).intValue();
        return denominator > 0 && numerator >= 30 * denominator;
    }

    private void addField(LinearLayout parent, int index, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        labels[index] = text(label, 11, Color.LTGRAY);
        labels[index].setGravity(Gravity.CENTER);
        values[index] = text("", 18, Color.WHITE);
        values[index].setGravity(Gravity.CENTER);
        box.addView(labels[index], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20));
        box.addView(values[index], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34));
        parent.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams rowParams(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private void refresh() {
        values[EXPOSURE].setText(formatDuration(exposureSeconds));
        values[ISO].setText(String.valueOf(iso) + (exposureIsoLink ? " LINK" : ""));
        values[INTERVAL].setText(formatDuration(intervalSeconds));
        values[COUNT].setText(shotCount == 0 ? "INFINITE" : String.valueOf(shotCount));
        values[DELAY].setText(formatDuration(delaySeconds));
        values[WAIT_SAVE].setText(waitForSave ? "YES" : "NO");
        int normalColor = nightMode ? Color.rgb(190, 25, 20) : Color.WHITE;
        int selectedColor = nightMode ? Color.rgb(255, 90, 20) : Color.YELLOW;
        for (int i = 0; i < values.length; i++)
            values[i].setTextColor(i == selected && state == IDLE ? selectedColor : normalColor);
        help.setText((starEnhanced ? "STAR VIEW  |  " : "")
                + "UP/DOWN SELECT  LEFT/RIGHT CHANGE  FN LINK  C1 VIEW  PLAY REVIEW  CENTER START/STOP");
        updateOverlayVisibility();
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60, remaining = seconds % 60;
        if (minutes < 60) return minutes + "m " + remaining + "s";
        return (minutes / 60) + "h " + (minutes % 60) + "m " + remaining + "s";
    }

    private void adjust(int direction) {
        if (state != IDLE) return;
        if (selected == EXPOSURE) {
            int oldExposure = exposureSeconds;
            exposureSeconds = clamp(exposureSeconds + direction * step(exposureSeconds), 31, 21600);
            if (exposureIsoLink && exposureSeconds != oldExposure) {
                int linkedIso = (int) Math.max(1L, ((long) iso * oldExposure + exposureSeconds / 2) / exposureSeconds);
                iso = nearestIso(linkedIso);
                applyIso();
            }
        }
        if (selected == ISO) adjustIso(direction);
        if (selected == INTERVAL) intervalSeconds = clamp(intervalSeconds + direction * step(intervalSeconds), 0, 3600);
        if (selected == COUNT) shotCount = clamp(shotCount + direction * (shotCount >= 100 ? 10 : 1), 0, 999);
        if (selected == DELAY) delaySeconds = clamp(delaySeconds + direction * step(delaySeconds), 0, 3600);
        if (selected == WAIT_SAVE) waitForSave = direction > 0;
        preferences.edit().putInt("exposure", exposureSeconds).putInt("iso", iso).putInt("interval", intervalSeconds)
                .putInt("count", shotCount).putInt("delay", delaySeconds).putBoolean("wait_for_save", waitForSave).commit();
        refresh();
    }

    private int step(int value) { return value >= 600 ? 60 : value >= 60 ? 10 : 1; }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private void adjustIso(int direction) {
        boolean resumeStar = starEnhanced;
        int previousDisplayMode = displayMode;
        if (resumeStar && !restoreStarEnhancement()) return;
        int oldIso = iso;
        if (supportedIsoValues == null || supportedIsoValues.size() == 0) {
            iso = clamp(iso + direction * (iso >= 3200 ? 400 : iso >= 800 ? 200 : 100), 50, 25600);
        } else {
            int index = supportedIsoValues.indexOf(Integer.valueOf(iso));
            if (index < 0) index = 0;
            index = clamp(index + direction, 0, supportedIsoValues.size() - 1);
            iso = ((Integer) supportedIsoValues.get(index)).intValue();
        }
        if (exposureIsoLink && iso != oldIso)
            exposureSeconds = clamp((int) Math.max(1L, ((long) exposureSeconds * oldIso + iso / 2) / iso), 31, 21600);
        applyIso();
        if (resumeStar && enableStarEnhancement() && previousDisplayMode == 1) {
            displayMode = 1; applyNightMode(); refresh();
        }
    }

    private int nearestIso(int target) {
        if (supportedIsoValues == null || supportedIsoValues.size() == 0) return clamp(target, 50, 25600);
        int nearest = ((Integer) supportedIsoValues.get(0)).intValue();
        int distance = Math.abs(nearest - target);
        for (int i = 1; i < supportedIsoValues.size(); i++) {
            int candidate = ((Integer) supportedIsoValues.get(i)).intValue();
            int candidateDistance = Math.abs(candidate - target);
            if (candidateDistance < distance) { nearest = candidate; distance = candidateDistance; }
        }
        return nearest;
    }

    private void toggleExposureIsoLink() {
        if (state != IDLE) return;
        exposureIsoLink = !exposureIsoLink;
        preferences.edit().putBoolean("exposure_iso_link", exposureIsoLink).commit();
        status.setText(exposureIsoLink ? "Exposure-ISO link ON" : "Exposure-ISO link OFF");
        refresh();
    }

    private void setupStarCapability() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier m = camera.createParametersModifier(p);
            starBoostIso = LiveViewValues.nearestIso(m.getSupportedISOSensitivities(), iso, Math.max(12800, iso));
            starSlowSupported = false;
            try {
                starSlowSupported = m.isSupportedSlowShutterLiveviewMode();
                starOriginalSlow = m.getSlowShutterLiveviewMode();
                if (starOriginalSlow == null) starSlowSupported = false;
            } catch (Throwable unavailable) { Logger.info("Timed BULB slow view unavailable: " + unavailable); }
            try { starOriginalPreviewMode = m.getShootingPreviewMode(); }
            catch (Throwable unavailable) { starOriginalPreviewMode = null; }
            Logger.info("Timed BULB star capability: slow=" + starSlowSupported + " boostISO=" + starBoostIso);
        } catch (Throwable error) { Logger.error("Timed BULB star capability: " + error); }
    }

    private boolean enableStarEnhancement() {
        if (camera == null || state != IDLE || bulbSetupPending) return false;
        try {
            Camera.Parameters p = camera.getNormalCamera().getParameters();
            CameraEx.ParametersModifier m = camera.createParametersModifier(p);
            if (starSlowSupported) {
                try {
                    m.setSlowShutterLiveviewMode(CameraEx.ParametersModifier.SLOW_SHUTTER_LIVEVIEW_MODE_ON);
                    starSlowApplied = true;
                    camera.getNormalCamera().setParameters(p);
                    CameraEx.ParametersModifier check = camera.createParametersModifier(
                            camera.getNormalCamera().getParameters());
                    if (!"on".equals(check.getSlowShutterLiveviewMode()))
                        throw new IllegalStateException("Slow live view rejected");
                } catch (Throwable rejected) {
                    Logger.info("Timed BULB slow view rejected: " + rejected);
                    if (!restoreStarEnhancement()) return false;
                    starSlowSupported = false;
                }
            }
            p = camera.getNormalCamera().getParameters();
            m = camera.createParametersModifier(p);
            if (!starSlowSupported) {
                starBoostIso = LiveViewValues.nearestIso(m.getSupportedISOSensitivities(), iso,
                        Math.max(12800, iso));
                m.setISOSensitivity(starBoostIso);
                starIsoApplied = true;
            }
            List modes = null;
            try { modes = m.getSupportedShootingPreviewModes(); } catch (Throwable unavailable) {}
            String mode = starSlowSupported ? "off" : "iris_ss_iso";
            if (starOriginalPreviewMode != null && modes != null && modes.contains(mode)) {
                m.setShootingPreviewMode(mode); starPreviewModeApplied = true;
            }
            camera.getNormalCamera().setParameters(p);
            cameraParameters = camera.getNormalCamera().getParameters();
            parametersModifier = camera.createParametersModifier(cameraParameters);
            starEnhanced = true;
            displayMode = 0; nightMode = true;
            applyNightMode(); refresh();
            status.setText("STAR VIEW");
            Logger.info("Timed BULB star enabled: " + (starSlowSupported ? "slow" : "ISO=" + starBoostIso)
                    + "; captureISO=" + iso);
            return true;
        } catch (Throwable error) {
            Logger.error("Timed BULB star enable: " + error);
            restoreStarEnhancement();
            status.setText("Star view failed: " + error.getClass().getSimpleName());
            return false;
        }
    }

    private boolean restoreStarEnhancement() {
        if (camera == null) return !starEnhanced;
        try {
            if (starIsoApplied || starSlowApplied || starPreviewModeApplied) {
                Camera.Parameters p = camera.getNormalCamera().getParameters();
                CameraEx.ParametersModifier m = camera.createParametersModifier(p);
                if (starIsoApplied) m.setISOSensitivity(iso);
                if (starSlowApplied && starOriginalSlow != null) m.setSlowShutterLiveviewMode(starOriginalSlow);
                if (starPreviewModeApplied && starOriginalPreviewMode != null)
                    m.setShootingPreviewMode(starOriginalPreviewMode);
                camera.getNormalCamera().setParameters(p);
                Camera.Parameters checkParameters = camera.getNormalCamera().getParameters();
                CameraEx.ParametersModifier check = camera.createParametersModifier(checkParameters);
                if (starIsoApplied && check.getISOSensitivity() != iso)
                    throw new IllegalStateException("Capture ISO restoration rejected");
                if (starSlowApplied && starOriginalSlow != null
                        && !starOriginalSlow.equals(check.getSlowShutterLiveviewMode()))
                    throw new IllegalStateException("Slow live view restoration rejected");
                if (starPreviewModeApplied && starOriginalPreviewMode != null
                        && !starOriginalPreviewMode.equals(check.getShootingPreviewMode()))
                    throw new IllegalStateException("Preview mode restoration rejected");
                cameraParameters = checkParameters;
                parametersModifier = check;
            }
            starIsoApplied = starSlowApplied = starPreviewModeApplied = starEnhanced = false;
            return true;
        } catch (Throwable error) {
            Logger.error("Timed BULB star restore: " + error);
            status.setText("Star restore failed - capture blocked");
            return false;
        }
    }

    private void toggleStarDisplay() {
        if (state != IDLE || bulbSetupPending) return;
        if (!starEnhanced && displayMode != 1) {
            displayMode = 1;
            applyNightMode(); refresh();
        } else if (!starEnhanced) {
            displayMode = 0;
            enableStarEnhancement();
        } else if (displayMode != 1) {
            displayMode = 1;
            applyNightMode(); refresh();
            status.setText("STAR clean preview - C1 returns to normal");
        } else {
            displayMode = 0;
            if (restoreStarEnhancement()) {
                applyNightMode(); refresh(); status.setText("Normal preview");
            }
        }
    }

    private void applyNightMode() {
        int secondary = nightMode ? Color.rgb(130, 15, 12) : Color.LTGRAY;
        int statusColor = nightMode ? Color.rgb(230, 35, 20) : Color.rgb(100, 220, 255);
        help.setTextColor(secondary);
        status.setTextColor(statusColor);
        for (int i = 0; i < labels.length; i++) labels[i].setTextColor(secondary);
        int panelColor = displayMode == 1 ? Color.TRANSPARENT
                : (nightMode ? 0x70000000 : 0x66000000);
        topPanel.setBackgroundColor(panelColor);
        bottomPanel.setBackgroundColor(panelColor);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = nightMode ? 0.05f : -1.0f;
        getWindow().setAttributes(params);
        updateOverlayVisibility();
    }

    private void updateOverlayVisibility() {
        boolean idle = state == IDLE;
        boolean showControls = idle && displayMode != 1;
        boolean cleanPreview = idle && displayMode == 1;
        topPanel.setVisibility(showControls ? View.VISIBLE : View.GONE);
        help.setVisibility(showControls ? View.VISIBLE : View.GONE);
        status.setVisibility(View.VISIBLE);
        bottomPanel.setVisibility(cleanPreview ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) bottomPanel.getLayoutParams();
        panelParams.height = showControls ? 56 : 34;
        bottomPanel.setLayoutParams(panelParams);
        bottomPanel.setBackgroundColor(displayMode == 1 && idle ? Color.TRANSPARENT
                : (nightMode ? 0x70000000 : 0x66000000));
    }

    private void applyIso() {
        if (camera == null || parametersModifier == null || cameraParameters == null) return;
        try {
            parametersModifier.setISOSensitivity(iso);
            camera.getNormalCamera().setParameters(cameraParameters);
            status.setText("ISO set to " + iso);
        } catch (Throwable error) {
            status.setText("ISO change failed: " + error.getClass().getSimpleName());
        }
    }

    private void startSequence() {
        if (camera == null) { status.setText("Camera is unavailable"); return; }
        if (!previewReady) { status.setText("Camera preview is not ready"); return; }
        if (bulbSetupPending) { status.setText("Preparing BULB - please wait..."); return; }
        if (a7s2CompatibilityMode && !a7s2BulbArmed) {
            status.setText("Set shutter to 30s, then restart app");
            Logger.error("Sequence blocked: A7S II BULB was not armed from 30s");
            return;
        }
        resumeStarAfterSequence = starEnhanced;
        resumeStarDisplayMode = displayMode;
        if (starEnhanced && !restoreStarEnhancement()) {
            resumeStarAfterSequence = false;
            status.setText("Capture blocked: cannot restore photo settings");
            return;
        }
        displayMode = 0;
        completedShots = 0; stopping = false;
        Logger.info("Sequence start: exposure=" + exposureSeconds + "s iso=" + iso
                + " interval=" + intervalSeconds + "s count=" + shotCount + " delay=" + delaySeconds
                + "s waitSave=" + waitForSave);
        if (delaySeconds > 0) beginPhase(DELAYING, delaySeconds); else startExposure();
        refresh();
    }

    private void startExposure() {
        try {
            if (a7s2CompatibilityMode) {
                // Avoid the same blocking A7S II shutter query at sequence start.
                // The physical mode dial must be set to BULB before starting.
                Logger.info("A7S II: pre-shot BULB probe skipped");
            } else {
                refreshCameraParameters();
                Pair shutter = parametersModifier == null ? null : parametersModifier.getShutterSpeed();
                if (!isBulb(shutter) && !isThirtySecondsOrLonger(shutter)) {
                    Logger.error("Shot blocked: shutter is not BULB (" + formatShutter(shutter) + ")");
                    abort("Safety stop: BULB is not armed");
                    return;
                }
                if (!isBulb(shutter)) Logger.info("BULB boundary accepted at " + formatShutter(shutter));
            }
            applyIso();
            camera.getNormalCamera().startPreview();
            Logger.info("Opening shutter for shot " + (completedShots + 1) + ", ISO " + iso);
            camera.burstableTakePicture();
            beginPhase(EXPOSING, exposureSeconds);
        } catch (Throwable error) {
            Logger.error("Capture start: " + error.toString());
            abort("Start failed: " + error.getClass().getSimpleName());
        }
    }

    private void stopExposure(final boolean continueSequence) {
        if (camera == null || stopping) return;
        stopping = true;
        status.setText("Closing shutter...");
        try {
            if (continueSequence && state != IDLE) {
                state = SAVING;
                sawCancelCallback = false;
                saveFinalized = false;
                handler.removeCallbacks(ticker);
            }
            closeShutterWithRetry();
            Logger.info("Shutter close requested for shot " + (completedShots + 1));
            stopping = false;
            if (!continueSequence || state == IDLE) return;
            completedShots++;
            if (waitForSave) {
                status.setText("Waiting for photo save...");
                handler.removeCallbacks(saveCallbackTimeout);
                handler.postDelayed(saveCallbackTimeout, SAVE_CALLBACK_TIMEOUT_MS);
            } else {
                Logger.info("Fast mode: save callback wait skipped");
                finalizeSave();
            }
        } catch (Throwable error) {
            stopping = false;
            Logger.error("Capture stop: " + error.toString());
            state = IDLE;
            handler.removeCallbacks(ticker);
            status.setText("Stop failed: " + error.getClass().getSimpleName());
            resumeStarAfterSequence(); refresh();
        }
    }

    private void handleShutterCallback(int result) {
        if (state != SAVING || saveFinalized) return;
        if (result == CameraEx.ShutterListener.STATUS_CANCELED) {
            sawCancelCallback = true;
            Logger.info("Cancel callback received; waiting for save completion");
        } else if (result == CameraEx.ShutterListener.STATUS_OK && sawCancelCallback) {
            Logger.info("Save completion callback received");
            finalizeSave();
        } else if (result == CameraEx.ShutterListener.STATUS_ERROR) {
            Logger.error("Shutter callback reported error");
            abort("Camera reported a shutter error");
        }
    }

    private void finalizeSave() {
        if (state != SAVING || saveFinalized) return;
        saveFinalized = true;
        handler.removeCallbacks(saveCallbackTimeout);
        status.setText("Saving photo...");
        handler.postDelayed(new Runnable() {
            public void run() {
                if (state != SAVING) return;
                Logger.info("Save buffer complete for shot " + completedShots);
                if (shotCount != 0 && completedShots >= shotCount) finishSequence();
                else if (intervalSeconds > 0) beginPhase(WAITING, intervalSeconds);
                else startExposure();
            }
        }, SAVE_BUFFER_MS);
    }

    private void closeShutterWithRetry() throws Throwable {
        Throwable lastError = null;
        for (int attempt = 1; attempt <= CLOSE_ATTEMPTS; attempt++) {
            try {
                camera.cancelTakePicture();
                return;
            } catch (Throwable error) {
                lastError = error;
                Logger.error("Close attempt " + attempt + " failed: " + error.toString());
                if (attempt < CLOSE_ATTEMPTS) SystemClock.sleep(150);
            }
        }
        throw lastError;
    }

    private void beginPhase(int nextState, int seconds) {
        state = nextState;
        phaseEndsAt = SystemClock.elapsedRealtime() + seconds * 1000L;
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void tick() {
        if (state == IDLE) return;
        long millisLeft = Math.max(0, phaseEndsAt - SystemClock.elapsedRealtime());
        int secondsLeft = (int) ((millisLeft + 999) / 1000);
        String total = shotCount == 0 ? "infinite" : String.valueOf(shotCount);
        if (state == DELAYING) status.setText("Starting in " + formatDuration(secondsLeft));
        if (state == EXPOSING) status.setText("Exposing " + (completedShots + 1) + "/" + total + "   " + formatDuration(secondsLeft));
        if (state == SAVING) status.setText("Saving photo " + completedShots + "/" + total + "...");
        if (state == WAITING) status.setText("Saved " + completedShots + "/" + total + "   next in " + formatDuration(secondsLeft));
        if (millisLeft <= 0) {
            if (state == DELAYING || state == WAITING) startExposure();
            else if (state == EXPOSING) stopExposure(true);
        } else handler.postDelayed(ticker, Math.min(250, millisLeft));
    }

    private void finishSequence() {
        state = IDLE; handler.removeCallbacks(ticker);
        Logger.info("Sequence complete: " + completedShots + " shots");
        status.setText("Complete: " + completedShots + " shots");
        resumeStarAfterSequence(); refresh();
    }

    private void abort(String message) {
        handler.removeCallbacks(ticker);
        int previousState = state;
        state = IDLE;
        if (previousState == EXPOSING) stopExposure(false);
        Logger.info("Sequence stopped: " + message + ", completed=" + completedShots);
        status.setText(message); resumeStarAfterSequence(); refresh();
    }

    private void resumeStarAfterSequence() {
        if (camera == null) return;
        int mode = resumeStarDisplayMode;
        if (!resumeStarAfterSequence) {
            displayMode = mode;
            applyNightMode(); refresh();
            return;
        }
        resumeStarAfterSequence = false;
        if (enableStarEnhancement() && mode == 1) {
            displayMode = 1; applyNightMode(); refresh();
        }
    }

    @Override protected boolean onUpKeyDown() { if (state == IDLE) { selected = (selected + FIELD_TOTAL - 1) % FIELD_TOTAL; refresh(); } return true; }
    @Override protected boolean onDownKeyDown() { if (state == IDLE) { selected = (selected + 1) % FIELD_TOTAL; refresh(); } return true; }
    @Override protected boolean onLeftKeyDown() { adjust(-1); return true; }
    @Override protected boolean onRightKeyDown() { adjust(1); return true; }
    private void showAuthorCredit() {
        if (state != IDLE) return;
        final CharSequence previousStatus = status.getText();
        final String authorText = "AstroFish  |  by yxy";
        status.setText(authorText);
        Logger.info("Author credit opened: yxy");
        handler.postDelayed(new Runnable() {
            public void run() {
                if (state == IDLE && authorText.equals(status.getText().toString())) status.setText(previousStatus);
            }
        }, 3000);
    }

    @Override protected boolean onFnKeyDown() { fnHeld = true; authorChordUsed = false; return true; }
    @Override protected boolean onFnKeyUp() {
        if (fnHeld && !authorChordUsed) toggleExposureIsoLink();
        fnHeld = false;
        authorChordUsed = false;
        return true;
    }
    @Override protected boolean onC1KeyDown() {
        if (fnHeld && state == IDLE) {
            authorChordUsed = true;
            showAuthorCredit();
        } else toggleStarDisplay();
        return true;
    }
    @Override protected boolean onC1KeyUp() { return true; }
    @Override protected boolean onPlayKeyDown() {
        if (state != IDLE || bulbSetupPending) { status.setText("Wait for current operation before playback"); return true; }
        if (restoreStarEnhancement()) startActivity(new Intent(this, PlaybackActivity.class));
        return true;
    }
    @Override protected boolean onPlayKeyUp() { return true; }
    @Override protected boolean onEnterKeyDown() { if (state == IDLE) startSequence(); else abort("Stopped by user"); return true; }
    @Override protected boolean onShutterKeyDown() { if (state == IDLE) startSequence(); else abort("Stopped by user"); return true; }
    @Override protected boolean onDeleteKeyDown() {
        if (state != IDLE) {
            consumeDeleteRelease = true;
            abort("Stopped by user");
            return true;
        }
        return super.onDeleteKeyDown();
    }
    @Override protected boolean onDeleteKeyUp() {
        if (consumeDeleteRelease) {
            consumeDeleteRelease = false;
            return true;
        }
        return state != IDLE || super.onDeleteKeyUp();
    }
}
