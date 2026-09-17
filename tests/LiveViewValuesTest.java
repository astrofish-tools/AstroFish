package com.github.ma1co.pmcademo.app;
import java.util.Arrays;
import java.util.List;
public class LiveViewValuesTest {
    static void expect(int actual, int expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        List values = Arrays.asList(6400, 0, 100, -1, 1600, 12800, 3200);
        expect(LiveViewValues.nearestIso(values, 400, 10000), 12800);
        expect(LiveViewValues.nearestIso(values, 400, 100000), 12800);
        expect(LiveViewValues.nearestIso(values, 400, 1), 100);
        expect(LiveViewValues.stepIso(values, 3200, 1), 6400);
        expect(LiveViewValues.stepIso(values, 3200, -1), 1600);
        expect(LiveViewValues.stepIso(values, 100, -1), 100);
        expect(LiveViewValues.stepIso(values, 12800, 1), 12800);
        expect(LiveViewValues.stepIso(values, 0, 1), 100);
        expect(LiveViewValues.stepIso(null, 1600, 1), 1600);
        expect(LiveViewValues.nearestIso(Arrays.asList("bad", null, 800), 1600, 12800), 800);
        System.out.println("PASS: 10 capability-list edge cases");
    }
}
