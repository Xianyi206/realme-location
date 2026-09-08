package local.position.helper;

public final class GeoPoint {
    public final double latitude;
    public final double longitude;
    public GeoPoint(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("纬度应为 -90 到 90 之间的数字");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("经度应为 -180 到 180 之间的数字");
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public static GeoPoint parse(String latitude, String longitude) {
        try {
            if (latitude == null || longitude == null || latitude.trim().isEmpty() || longitude.trim().isEmpty())
                throw new IllegalArgumentException("请填写纬度和经度，或在地图上选择位置");
            return new GeoPoint(Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("请输入十进制经纬度，例如纬度 31.2304、经度 121.4737");
        }
    }
}
