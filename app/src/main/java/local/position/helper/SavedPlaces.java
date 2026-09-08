package local.position.helper;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local, versioned saved locations. Read-modify-write always reloads the current file. */
public final class SavedPlaces {
    public static final class Place {
        public final String id, name;
        public final GeoPoint point;
        public final long savedAt, measuredAt;
        public final float accuracy;
        Place(String id, String name, GeoPoint point, long savedAt, long measuredAt, float accuracy) {
            this.id = id; this.name = name; this.point = point; this.savedAt = savedAt;
            this.measuredAt = measuredAt; this.accuracy = accuracy;
        }
    }
    private final Path file;
    public SavedPlaces(Path file) { this.file = file; }
    private static final int MAGIC = 0x44445031, LIMIT = 1000;
    public List<Place> list() throws IOException {
        List<Place> places = new ArrayList<>();
        if (Files.notExists(file)) return places;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("无法识别常用地点文件，原文件已保留");
            int count = in.readInt();
            if (count < 0 || count > LIMIT) throw new IOException("常用地点文件记录数量无效");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String id = in.readUTF(), name = validName(in.readUTF());
                UUID.fromString(id);
                if (!ids.add(id)) throw new IOException("常用地点记录标识重复");
                GeoPoint point = new GeoPoint(in.readDouble(), in.readDouble());
                long savedAt = in.readLong(), measuredAt = in.readLong(); float accuracy = in.readFloat();
                metadata(measuredAt, accuracy);
                if (savedAt <= 0) throw new IOException("保存时间无效");
                places.add(new Place(id, name, point, savedAt, measuredAt, accuracy));
            }
            if (in.read() != -1) throw new IOException("常用地点文件包含未知数据");
        } catch (IllegalArgumentException | EOFException error) {
            throw new IOException("常用地点文件不完整或已损坏，原文件已保留", error);
        }
        return places;
    }
    public Place add(String name, GeoPoint point, long measuredAt, float accuracy) throws IOException {
        name = validName(name); metadata(measuredAt, accuracy);
        if (point == null) throw new IllegalArgumentException("请先选择坐标");
        List<Place> places = list();
        if (places.size() >= LIMIT) throw new IllegalArgumentException("已保存 1000 个地点，请先删除不需要的记录");
        Place place = new Place(UUID.randomUUID().toString(), name, point, System.currentTimeMillis(), measuredAt, accuracy);
        places.add(0, place); write(places); return place;
    }
    public void rename(String id, String name) throws IOException {
        name = validName(name); List<Place> places = list(); int index = find(places, id);
        Place p = places.get(index);
        places.set(index, new Place(p.id, name, p.point, p.savedAt, p.measuredAt, p.accuracy)); write(places);
    }
    public void update(String id, String name, GeoPoint point, long measuredAt, float accuracy) throws IOException {
        name = validName(name); metadata(measuredAt, accuracy);
        if (point == null) throw new IllegalArgumentException("请先选择坐标");
        List<Place> places = list(); int index = find(places, id); Place old = places.get(index);
        places.set(index, new Place(old.id, name, point, old.savedAt, measuredAt, accuracy)); write(places);
    }
    public void delete(String id) throws IOException {
        List<Place> places = list(); places.remove(find(places, id)); write(places);
    }
    private int find(List<Place> places, String id) {
        for (int i = 0; i < places.size(); i++) if (places.get(i).id.equals(id)) return i;
        throw new IllegalArgumentException("该地点已不存在，请重新打开列表");
    }
    private static String validName(String name) {
        if (name == null) throw new IllegalArgumentException("请填写地点名称");
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("请填写地点名称");
        if (name.codePointCount(0, name.length()) > 40) throw new IllegalArgumentException("名称最多 40 个字");
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i)))
            throw new IllegalArgumentException("名称不能包含换行或控制字符");
        return name;
    }
    private static void metadata(long measuredAt, float accuracy) {
        if (measuredAt == 0 && accuracy == -1f) return;
        if (measuredAt <= 0 || !Float.isFinite(accuracy) || accuracy <= 0)
            throw new IllegalArgumentException("采集时间或定位精度无效");
    }
    private void write(List<Place> places) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "saved-places-", ".tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temp.toFile()); DataOutputStream out = new DataOutputStream(stream)) {
                out.writeInt(MAGIC); out.writeInt(places.size());
                for (Place p : places) {
                    out.writeUTF(p.id); out.writeUTF(p.name);
                    out.writeDouble(p.point.latitude); out.writeDouble(p.point.longitude);
                    out.writeLong(p.savedAt); out.writeLong(p.measuredAt); out.writeFloat(p.accuracy);
                }
                out.flush(); stream.getFD().sync();
            }
            // Same-directory atomic replacement: a failed write keeps the last complete file.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
