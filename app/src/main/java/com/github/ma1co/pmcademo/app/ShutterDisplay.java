package com.github.ma1co.pmcademo.app;

/** Formats Sony shutter-speed rational values like a camera display. */
final class ShutterDisplay {
    static boolean isBulb(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) return true;
        // CameraEx firmware variants use a large integer sentinel for BULB.
        if (numerator >= 0x7fff) return true;
        return (double) numerator / denominator > 60.0;
    }

    static String format(int numerator, int denominator) {
        if (isBulb(numerator, denominator)) return "BULB";
        if (numerator >= denominator) {
            if (numerator % denominator == 0) return (numerator / denominator) + "s";
            double seconds = (double) numerator / denominator;
            long tenths = Math.round(seconds * 10.0);
            if (Math.abs(seconds - tenths / 10.0) < 0.01)
                return (tenths / 10) + "." + Math.abs(tenths % 10) + "s";
            long hundredths = Math.round(seconds * 100.0);
            return (hundredths / 100) + "." + twoDigits((int)Math.abs(hundredths % 100)) + "s";
        }
        int divisor = gcd(numerator, denominator);
        return (numerator / divisor) + "/" + (denominator / divisor);
    }

    private static int gcd(int a, int b) {
        a = Math.abs(a); b = Math.abs(b);
        while (b != 0) { int next = a % b; a = b; b = next; }
        return a == 0 ? 1 : a;
    }

    private static String twoDigits(int value) { return value < 10 ? "0" + value : String.valueOf(value); }
    private ShutterDisplay() {}
}
