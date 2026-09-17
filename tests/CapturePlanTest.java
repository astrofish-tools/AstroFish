package com.github.ma1co.pmcademo.app;
public class CapturePlanTest {
    private static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        CapturePlan p = new CapturePlan();
        p.start(1000, 3, 2, 1);
        check(!p.takeDue(2999)); check(p.takeDue(3000)); check(!p.takeDue(3001));
        check(p.completed()==0); check(p.complete(8000)); check(p.completed()==1);
        check(!p.complete(8001)); check(!p.takeDue(8999)); check(p.takeDue(9000));
        p.complete(15000); check(p.takeDue(16000)); p.complete(22000);
        check(!p.running()); check(p.completed()==3); check(!p.takeDue(50000));
        p.start(0, 999, 10, 0); p.stop(); check(!p.running()); check(!p.takeDue(10000));
        p.start(0, 2, 0, 0); check(p.takeDue(0)); p.stop();
        check(p.running()); check(p.stopping()); check(p.inFlight());
        p.complete(100); check(!p.running()); check(p.completed()==1);
        p.start(0, 2, 0, 0); p.takeDue(0); p.fail();
        check(!p.running()); check(!p.complete(10)); check(p.completed()==0);
        // Long delays use a monotonic long timeline, including a clock beyond 32 bits.
        p.start(4000000000L, 1, 3600, 0);
        check(!p.takeDue(4003599999L)); check(p.takeDue(4003600000L));
        boolean rejected=false; try { p.start(0,1,0,0); } catch (IllegalStateException e) { rejected=true; }
        check(rejected); p.fail();
        rejected=false; try { p.start(0,0,0,0); } catch (IllegalArgumentException e) { rejected=true; }
        check(rejected);
        // Zero interval still waits for completion; never permits overlapping exposures.
        p.start(0,999,0,0);
        for (int i=0;i<999;i++) {
            check(p.takeDue(i)); check(!p.takeDue(i)); check(p.complete(i));
        }
        check(!p.running()); check(p.completed()==999);
        System.out.println("PASS: " + checks + " scheduling assertions");
    }
}
