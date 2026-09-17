package com.github.ma1co.pmcademo.app;

/** Pure scheduling state; a frame is counted only on its successful completion callback. */
final class CapturePlan {
    private boolean running, inFlight, stopRequested;
    private int target, completed, interval;
    private long due;
    void start(long now, int count, int delaySeconds, int intervalSeconds) {
        if (running) throw new IllegalStateException("Already running");
        if (count < 1 || count > 999 || delaySeconds < 0 || intervalSeconds < 0)
            throw new IllegalArgumentException("Invalid plan");
        running = true; inFlight = stopRequested = false; completed = 0;
        target = count; interval = intervalSeconds; due = now + delaySeconds * 1000L;
    }
    boolean takeDue(long now) {
        if (!running || inFlight || now < due) return false;
        inFlight = true; return true;
    }
    boolean complete(long now) {
        if (!running || !inFlight) return false;
        completed++; inFlight = false;
        if (stopRequested || completed >= target) running = false;
        else due = now + interval * 1000L;
        return true;
    }
    void stop() { stopRequested = true; if (!inFlight) running = false; }
    void fail() { running = inFlight = false; stopRequested = true; }
    boolean running() { return running; }
    boolean inFlight() { return inFlight; }
    boolean stopping() { return stopRequested; }
    int completed() { return completed; }
    int target() { return target; }
    long remainingMillis(long now) { return Math.max(0, due-now); }
}
