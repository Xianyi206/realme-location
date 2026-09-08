package local.position.helper;

import java.util.*;

public final class SessionTests {
    static final GeoPoint A = new GeoPoint(31.2, 121.4), B = new GeoPoint(30.1, 120.2);
    static int passed;
    interface Check { void run() throws Exception; }
    static void test(String name, Check body) throws Exception { body.run(); passed++; System.out.println("PASS " + name); }
    static void require(boolean result) { if (!result) throw new AssertionError(); }
    static final class Fake implements MockSession.Provider {
        final Set<String> active = new HashSet<>();
        final Map<String, GeoPoint> delivered = new HashMap<>();
        final List<String> removeAttempts = new ArrayList<>();
        String failAdd, failPublish, failRemove;
        int additions;
        public void add(String n) throws Exception {
            if (n.equals(failAdd)) throw new Exception("add denied");
            require(active.add(n)); additions++;
        }
        public void publish(String n, GeoPoint p) throws Exception {
            if (n.equals(failPublish)) throw new Exception("publish denied");
            require(active.contains(n)); delivered.put(n, p);
        }
        public void remove(String n) throws Exception {
            removeAttempts.add(n);
            if (n.equals(failRemove)) throw new Exception("remove denied");
            active.remove(n);
        }
    }
    static MockSession session(Fake f) { return new MockSession(f, Arrays.asList("gps", "network", "fused")); }
    static void fails(Check body) throws Exception {
        try { body.run(); } catch (UnsupportedOperationException e) { throw e; } catch (Exception expected) { return; }
        throw new AssertionError("Expected an error");
    }
    public static void main(String[] args) throws Exception {
        test("publish all providers before reporting running", () -> {
            Fake f = new Fake(); MockSession s = session(f); s.start(A);
            require(s.isRunning() && f.delivered.size() == 3);
            s.start(B); require(f.additions == 3 && f.delivered.get("gps") == B);
            s.stop(); require(!s.isRunning() && f.active.isEmpty());
            s.stop(); require(f.removeAttempts.size() == 3);
        });
        test("provider creation failure rolls back prior providers", () -> {
            Fake f = new Fake(); f.failAdd = "network"; MockSession s = session(f);
            fails(() -> s.start(A)); require(!s.isRunning() && f.active.isEmpty());
        });
        test("initial publish failure rolls back every provider", () -> {
            Fake f = new Fake(); f.failPublish = "network"; MockSession s = session(f);
            fails(() -> s.start(A)); require(!s.isRunning() && f.active.isEmpty());
        });
        test("permission loss during tick stops and cleans up", () -> {
            Fake f = new Fake(); MockSession s = session(f); s.start(A); f.failPublish = "gps";
            fails(s::tick); require(!s.isRunning() && f.active.isEmpty());
        });
        test("one cleanup failure does not skip other providers; retry works", () -> {
            Fake f = new Fake(); MockSession s = session(f); s.start(A); f.failRemove = "network";
            fails(s::stop); require(!s.isRunning() && f.removeAttempts.size() == 3);
            require(f.active.equals(Collections.singleton("network")));
            f.failRemove = null; s.stop(); require(f.active.isEmpty());
        });
        test("session can be restarted after clean stop", () -> {
            Fake f = new Fake(); MockSession s = session(f); s.start(A); s.stop(); s.start(B);
            require(s.isRunning() && f.delivered.get("network") == B);
        });
        System.out.println("PASS " + passed + " session cases");
    }
}
