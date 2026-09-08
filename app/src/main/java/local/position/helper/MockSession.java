package local.position.helper;

import java.util.*;

/** Android-independent ownership and rollback for one explicit mock session. */
public final class MockSession {
    public interface Provider {
        void add(String name) throws Exception;
        void publish(String name, GeoPoint point) throws Exception;
        void remove(String name) throws Exception;
    }
    private final Provider provider;
    private final List<String> names;
    private final List<String> owned = new ArrayList<>();
    private GeoPoint point;
    public MockSession(Provider provider, List<String> names) {
        this.provider = provider;
        this.names = new ArrayList<>(names);
    }
    public void start(GeoPoint next) throws Exception {
        if (next == null) throw new IllegalArgumentException("Missing point");
        try {
            if (point == null) {
                stop(); // Retry any earlier incomplete cleanup before claiming providers again.
                for (String name : names) {
                    provider.add(name);
                    owned.add(name);
                }
            }
            for (String name : owned) provider.publish(name, next);
            point = next;
        } catch (Exception error) {
            rollback(error);
            throw error;
        }
    }
    public void tick() throws Exception {
        if (point == null) return;
        try {
            for (String name : owned) provider.publish(name, point);
        } catch (Exception error) {
            rollback(error);
            throw error;
        }
    }
    public void stop() throws Exception {
        point = null;
        Exception failure = null;
        for (String name : new ArrayList<>(owned)) {
            try {
                provider.remove(name);
                owned.remove(name);
            } catch (Exception error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
    private void rollback(Exception original) {
        try { stop(); } catch (Exception cleanup) { original.addSuppressed(cleanup); }
    }
    public boolean isRunning() { return point != null; }
}
