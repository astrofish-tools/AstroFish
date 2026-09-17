package com.github.ma1co.pmcademo.app;

public final class ShutterDisplayTest {
    private static int assertions;
    private static void equal(String expected, String actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        equal("30s", ShutterDisplay.format(30, 1));
        equal("15s", ShutterDisplay.format(150, 10));
        equal("1.3s", ShutterDisplay.format(13, 10));
        equal("1/2", ShutterDisplay.format(1, 2));
        equal("1/125", ShutterDisplay.format(2, 250));
        equal("BULB", ShutterDisplay.format(0, 1));
        equal("BULB", ShutterDisplay.format(65535, 1));
        equal("BULB", ShutterDisplay.format(65535, 65535));
        check(!ShutterDisplay.isBulb(30, 1), "30 seconds is not BULB");
        check(ShutterDisplay.isBulb(-1, 1), "negative sentinel is BULB");
        System.out.println("PASS: " + assertions + " shutter display assertions");
    }
}
