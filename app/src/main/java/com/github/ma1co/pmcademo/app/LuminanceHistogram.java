package com.github.ma1co.pmcademo.app;

/** Builds a compact histogram from the Y plane of an NV21 camera preview. */
final class LuminanceHistogram {
    static int[] calculate(short[] source, int binCount, double exposureScale) {
        if (binCount <= 0) throw new IllegalArgumentException("binCount");
        int[] bins = new int[binCount];
        if (source == null || source.length == 0) return bins;
        int[] corrected = correctionTable(exposureScale);
        for (int i = 0; i < source.length; i++) {
            int y = source.length == 1 ? 0 : i * 255 / (source.length - 1);
            if (corrected != null) y = corrected[y];
            int bin = Math.min(binCount - 1, y * binCount / 256);
            long count = (long) bins[bin] + (source[i] & 0xffff);
            bins[bin] = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        }
        return bins;
    }

    static int[] calculate(byte[] frame, int width, int height, int binCount, int targetSamples) {
        return calculate(frame, width, height, binCount, targetSamples, 1.0);
    }

    static int[] calculate(byte[] frame, int width, int height, int binCount, int targetSamples,
            double exposureScale) {
        if (binCount <= 0) throw new IllegalArgumentException("binCount");
        int[] bins = new int[binCount];
        if (frame == null || width <= 0 || height <= 0) return bins;
        long requested = (long) width * height;
        int pixels = (int) Math.min((long) frame.length, requested);
        if (pixels <= 0) return bins;
        if (exposureScale <= 0 || Double.isNaN(exposureScale) || Double.isInfinite(exposureScale))
            exposureScale = 1.0;
        int[] corrected = correctionTable(exposureScale);
        int step = Math.max(1, pixels / Math.max(1, targetSamples));
        for (int i = 0; i < pixels; i += step) {
            int y = frame[i] & 0xff;
            if (corrected != null) y = corrected[y];
            int bin = y * binCount / 256;
            if (bin >= binCount) bin = binCount - 1;
            bins[bin]++;
        }
        return bins;
    }

    private static int[] correctionTable(double exposureScale) {
        if (exposureScale <= 0 || Double.isNaN(exposureScale) || Double.isInfinite(exposureScale))
            exposureScale = 1.0;
        if (Math.abs(exposureScale - 1.0) <= 0.001) return null;
        int[] corrected = new int[256];
        for (int y = 0; y < 256; y++) {
            double encoded = Math.max(0.0, Math.min(1.0, (y - 16) / 219.0));
            double linear = Math.pow(encoded, 2.2) * exposureScale;
            corrected[y] = 16 + (int) Math.round(219 * Math.pow(Math.min(1.0, linear), 1.0 / 2.2));
        }
        return corrected;
    }

    private LuminanceHistogram() {}
}
