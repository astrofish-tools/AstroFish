package com.github.ma1co.pmcademo.app;

public final class LuminanceHistogramTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[] empty = LuminanceHistogram.calculate(null, 2, 2, 4, 4);
        check(empty.length == 4, "bin count");
        check(empty[0] == 0 && empty[3] == 0, "null frame");

        byte[] levels = new byte[] {0, 63, 64, 127, (byte)128, (byte)191, (byte)192, (byte)255};
        int[] bins = LuminanceHistogram.calculate(levels, 4, 2, 4, 8);
        check(bins[0] == 2, "shadows");
        check(bins[1] == 2, "lower midtones");
        check(bins[2] == 2, "upper midtones");
        check(bins[3] == 2, "highlights");

        int[] yOnly = LuminanceHistogram.calculate(new byte[] {0, 1, 2, 3, (byte)255}, 2, 2, 4, 8);
        check(yOnly[0] == 4, "ignore NV21 chroma bytes");
        check(yOnly[3] == 0, "chroma must not create highlights");
        int[] corrected = LuminanceHistogram.calculate(new byte[] {(byte)235}, 1, 1, 4, 1, 0.25);
        check(corrected[3] == 0, "ISO correction moves boosted highlights below clipping");
        check(corrected[2] == 1, "gamma-aware exposure correction");
        int[] nativeBins = LuminanceHistogram.calculate(new short[] {2, 0, 0, 3}, 4, 1.0);
        check(nativeBins[0] == 2, "native shadow bin");
        check(nativeBins[3] == 3, "native highlight bin");
        System.out.println("PASS: " + assertions + " luminance histogram assertions");
    }
}
