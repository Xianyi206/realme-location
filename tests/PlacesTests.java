package local.position.helper;

import java.nio.file.*;
import java.util.*;

public final class PlacesTests {
    private static int passed;
    private static Path root;
    interface Check { void run() throws Exception; }
    static void test(String name, Check c) throws Exception { c.run(); passed++; System.out.println("PASS " + name); }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void fails(Check c) throws Exception {
        try { c.run(); } catch (IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError("Expected failure");
    }
    static SavedPlaces store(String name) { return new SavedPlaces(root.resolve(name)); }
    static final GeoPoint POINT = new GeoPoint(31.2304, 121.4737);
    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory(Paths.get("build/core-tests"), "places-");
        test("new store is empty", () -> check(store("new").list().isEmpty()));
        test("multiple named coordinates survive restart with accuracy and time", () -> {
            SavedPlaces s = store("persist");
            SavedPlaces.Place first = s.add("公司门口", POINT, 1700000000000L, 12f);
            s.add(" 家 🏠 ", new GeoPoint(-33.86, 151.2), 0, -1f);
            List<SavedPlaces.Place> all = store("persist").list();
            check(all.size() == 2 && all.get(0).name.equals("家 🏠") && all.get(1).id.equals(first.id));
            check(all.get(1).point.latitude == POINT.latitude && all.get(1).accuracy == 12f && all.get(1).measuredAt == 1700000000000L);
        });
        test("rename preserves identity coordinates and capture metadata", () -> {
            SavedPlaces s = store("rename"); SavedPlaces.Place p = s.add("旧名称", POINT, 12L, 20f);
            s.rename(p.id, " 新名称 "); SavedPlaces.Place q = store("rename").list().get(0);
            check(q.id.equals(p.id) && q.name.equals("新名称") && q.measuredAt == 12L && q.savedAt == p.savedAt && q.point.longitude == p.point.longitude);
        });
        test("duplicate names remain separate and delete uses stable identity", () -> {
            SavedPlaces s = store("duplicates"); SavedPlaces.Place a = s.add("公司", POINT, 0, -1f);
            SavedPlaces.Place b = s.add("公司", new GeoPoint(30, 120), 0, -1f);
            s.delete(a.id); List<SavedPlaces.Place> rest = store("duplicates").list();
            check(rest.size() == 1 && rest.get(0).id.equals(b.id));
        });
        test("invalid name does not modify persisted records", () -> {
            SavedPlaces s = store("invalid"); SavedPlaces.Place p = s.add("原名", POINT, 0, -1f);
            byte[] before = Files.readAllBytes(root.resolve("invalid"));
            for (String bad : Arrays.asList("", "   ", "\n", "两\n行", String.join("", Collections.nCopies(41, "a")))) fails(() -> s.rename(p.id, bad));
            check(Arrays.equals(before, Files.readAllBytes(root.resolve("invalid"))));
        });
        test("unknown id cannot modify another saved place", () -> {
            SavedPlaces s = store("missing"); s.add("保留", POINT, 0, -1f);
            fails(() -> s.rename("missing", "bad")); fails(() -> s.delete("missing")); check(s.list().size() == 1);
        });
        test("corrupt storage is reported and never silently overwritten", () -> {
            Path p = root.resolve("corrupt"); byte[] data = {1, 2, 3}; Files.write(p, data);
            fails(() -> store("corrupt").list()); fails(() -> store("corrupt").add("新地点", POINT, 0, -1));
            check(Arrays.equals(data, Files.readAllBytes(p)));
        });
        test("second store instance reloads before mutating", () -> {
            SavedPlaces a = store("two"), b = store("two"); a.list(); b.list();
            a.add("A", POINT, 0, -1); b.add("B", POINT, 0, -1); check(a.list().size() == 2);
        });
        test("invalid measured metadata is rejected", () -> {
            SavedPlaces s = store("meta"); fails(() -> s.add("bad", POINT, 123, Float.NaN));
            fails(() -> s.add("bad", POINT, 0, 10)); check(s.list().isEmpty());
        });
        test("disk write failure is reported without changing the blocking file", () -> {
            Path parent = root.resolve("blocked-parent"); Files.write(parent, new byte[] {7});
            SavedPlaces s = new SavedPlaces(parent.resolve("places.bin"));
            fails(() -> s.add("不能保存", POINT, 0, -1f)); check(Files.readAllBytes(parent)[0] == 7);
        });
        test("full editor update retains identity and original save time", () -> {
            SavedPlaces s = store("edit"); SavedPlaces.Place a = s.add("原名", POINT, 123, 8f);
            s.update(a.id, "新名", new GeoPoint(22.54, 114.06), 0, -1f);
            SavedPlaces.Place b = store("edit").list().get(0);
            check(b.id.equals(a.id) && b.name.equals("新名") && b.savedAt == a.savedAt && b.point.latitude == 22.54 && b.measuredAt == 0 && b.accuracy == -1f);
            byte[] before = Files.readAllBytes(root.resolve("edit"));
            fails(() -> s.update(a.id, "", POINT, 0, -1));
            fails(() -> s.update("missing", "valid", POINT, 0, -1));
            check(Arrays.equals(before, Files.readAllBytes(root.resolve("edit"))));
        });
        System.out.println("PASS " + passed + " saved-place cases");
    }
}
