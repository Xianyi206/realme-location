package local.position.helper;

import java.util.*;

public final class CoreTests {
    private static int passed;
    public static void main(String[] args) throws Exception {
        test("trim decimal coordinates", () -> {
            GeoPoint p = GeoPoint.parse(" 31.230400 ", "121.473700");
            equal(31.2304, p.latitude); equal(121.4737, p.longitude);
        });
        test("world bounds are inclusive", () -> {
            equal(-90, GeoPoint.parse("-90", "180").latitude);
            equal(-180, GeoPoint.parse("90", "-180").longitude);
        });
        for (String value : Arrays.asList("", "NaN", "Infinity", "-Infinity", "1e309", "91", "-91", "abc", "12,3")) {
            test("reject invalid latitude: " + value, () -> rejects(() -> GeoPoint.parse(value, "100")));
        }
        for (String value : Arrays.asList("", "NaN", "Infinity", "181", "-181", "abc")) {
            test("reject invalid longitude: " + value, () -> rejects(() -> GeoPoint.parse("30", value)));
        }
        test("reject invalid constructed point", () -> rejects(() -> new GeoPoint(Double.NaN, 0)));
        System.out.println("PASS " + passed + " coordinate cases");
    }
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) throws Exception {
        check.run(); passed++; System.out.println("PASS " + name);
    }
    static void equal(double want, double got) {
        if (Math.abs(want - got) > 1e-9) throw new AssertionError(want + " != " + got);
    }
    static void rejects(Check check) throws Exception {
        try { check.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid coordinates were accepted");
    }
}
