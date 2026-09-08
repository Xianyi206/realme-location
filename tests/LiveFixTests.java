package local.position.helper;

public final class LiveFixTests {
    static int passed;
    static void check(String name, boolean ok) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS " + name); }
    public static void main(String[] args) {
        GeoPoint p = new GeoPoint(30, 120);
        LiveFix f = new LiveFix(10_000_000_000L);
        check("reject mock sample", !f.offer(p, true, 11_000_000_000L, 12_000_000_000L, 123, 5) && f.best() == null);
        check("reject cached sample from before request", !f.offer(p, false, 9_000_000_000L, 12_000_000_000L, 123, 5) && f.best() == null);
        check("reject future sample", !f.offer(p, false, 13_000_000_000L, 12_000_000_000L, 123, 5));
        check("reject stale delivered sample", !f.offer(p, false, 11_000_000_000L, 20_000_000_000L, 123, 5));
        check("reject missing accuracy", !f.offer(p, false, 11_000_000_000L, 12_000_000_000L, 123, Float.NaN) && f.best() == null);
        check("retain coarse fix while waiting for precision", !f.offer(p, false, 11_000_000_000L, 12_000_000_000L, 123, 150) && f.best().accuracy == 150);
        check("prefer better accuracy", !f.offer(p, false, 12_000_000_000L, 13_000_000_000L, 124, 60) && f.best().accuracy == 60);
        check("worse fix does not replace best", !f.offer(p, false, 13_000_000_000L, 14_000_000_000L, 125, 200) && f.best().measuredAt == 124);
        check("precise fresh fix completes acquisition", f.offer(p, false, 14_000_000_000L, 15_000_000_000L, 126, 10) && f.best().measuredAt == 126);
        System.out.println("PASS " + passed + " live-fix cases");
    }
}
