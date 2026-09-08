package local.position.helper;

/** Select a fresh non-mock sample from the current explicit acquisition. */
public final class LiveFix {
    public static final class Sample {
        public final GeoPoint point;
        public final long measuredAt;
        public final float accuracy;
        Sample(GeoPoint point, long measuredAt, float accuracy) {
            this.point = point; this.measuredAt = measuredAt; this.accuracy = accuracy;
        }
    }
    private final long startedNanos;
    private Sample best;
    public LiveFix(long startedNanos) { this.startedNanos = startedNanos; }
    public boolean offer(GeoPoint point, boolean mock, long fixNanos, long nowNanos, long measuredAt, float accuracy) {
        if (point == null || mock || fixNanos < startedNanos || fixNanos > nowNanos
            || nowNanos - fixNanos > 5_000_000_000L || measuredAt <= 0
            || !Float.isFinite(accuracy) || accuracy <= 0) return false;
        if (best == null || accuracy <= best.accuracy) best = new Sample(point, measuredAt, accuracy);
        return best.accuracy <= 25f;
    }
    public Sample best() { return best; }
}
