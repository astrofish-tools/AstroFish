package com.github.ma1co.pmcademo.app;
import java.util.List;

/** Handles camera capability lists without assuming order or allowing AUTO as a boost. */
final class LiveViewValues {
    static int nearestIso(List values, int current, int target) {
        int best = current > 0 ? current : 1600;
        long distance = Long.MAX_VALUE;
        if (values != null) for (Object item : values) {
            if (!(item instanceof Integer)) continue;
            int value = ((Integer)item).intValue();
            long next = Math.abs((long)value - target);
            if (value > 0 && next < distance) { best = value; distance = next; }
        }
        return best;
    }
    static int stepIso(List values, int current, int direction) {
        int next = current;
        if (values != null) for (Object item : values) {
            if (!(item instanceof Integer)) continue;
            int value = ((Integer)item).intValue();
            if (value <= 0) continue;
            if (direction > 0 && value > current && (next == current || value < next)) next = value;
            if (direction < 0 && value < current && (next == current || value > next)) next = value;
        }
        return next;
    }
}
