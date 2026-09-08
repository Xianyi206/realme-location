package local.position.helper;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.*;

public final class MockLocationService extends Service {
    public static final String STOP = "local.position.helper.STOP";
    private static final String CHANNEL = "mock_location";
    private static final int NOTIFICATION = 100;
    public static final class State {
        public final boolean running;
        public final String message;
        public final GeoPoint point;
        State(boolean running, String message, GeoPoint point) {
            this.running = running; this.message = message; this.point = point;
        }
    }
    private static volatile State state = new State(false, "尚未开始模拟", null);
    public static State state() { return state; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MockSession session;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                session.tick();
                handler.postDelayed(this, 1000);
            } catch (Exception error) { fail(error); }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "模拟定位运行状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("在切换到浏览器后维持模拟定位，并提供停止按钮");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        final LocationManager manager = getSystemService(LocationManager.class);
        List<String> providers = new ArrayList<>(Arrays.asList("gps", "network"));
        if (manager.getAllProviders().contains("fused")) providers.add("fused");
        session = new MockSession(new MockSession.Provider() {
            @Override @SuppressWarnings("deprecation") public void add(String name) {
                // Registration and enablement are separate so rollback always knows ownership.
                manager.addTestProvider(name, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            }
            @Override public void publish(String name, GeoPoint p) {
                manager.setTestProviderEnabled(name, true);
                Location location = new Location(name);
                location.setLatitude(p.latitude); location.setLongitude(p.longitude);
                location.setAccuracy(5f); location.setAltitude(0); location.setSpeed(0); location.setBearing(0);
                location.setVerticalAccuracyMeters(5f); location.setSpeedAccuracyMetersPerSecond(0);
                location.setBearingAccuracyDegrees(0);
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                manager.setTestProviderLocation(name, location);
            }
            @Override public void remove(String name) { manager.removeTestProvider(name); }
        }, providers);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            finish("已停止模拟；浏览器可能需要刷新定位");
            return START_NOT_STICKY;
        }
        try {
            GeoPoint next = new GeoPoint(intent.getDoubleExtra("latitude", Double.NaN), intent.getDoubleExtra("longitude", Double.NaN));
            startForeground(NOTIFICATION, notification(next));
            handler.removeCallbacks(tick);
            session.start(next);
            state = new State(true, "正在持续发送模拟位置", next);
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("latitude", Double.toString(next.latitude))
                .putString("longitude", Double.toString(next.longitude)).apply();
            handler.postDelayed(tick, 1000);
        } catch (Exception error) { fail(error); }
        return START_NOT_STICKY;
    }

    private Notification notification(GeoPoint p) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MockLocationService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("定点助手 · 模拟定位中")
            .setContentText(String.format(Locale.US, "纬度 %.6f · 经度 %.6f", p.latitude, p.longitude))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "停止模拟", stop).build()).build();
    }

    private void fail(Exception error) {
        String detail = error instanceof SecurityException
            ? "无法模拟：请在开发者选项中选择“定点助手”，并允许定位权限"
            : "模拟已停止：" + error.getClass().getSimpleName() + " / " + String.valueOf(error.getMessage());
        if (error.getSuppressed().length > 0) detail += "。清理未完成，可重新选择模拟位置应用或重启手机";
        finish(detail);
    }

    private void finish(String message) {
        handler.removeCallbacks(tick);
        try { if (session != null) session.stop(); }
        catch (Exception cleanup) { message += "。未能清除全部模拟源，请重新选择模拟位置应用或重启手机"; }
        state = new State(false, message, null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        String message = state.running ? "模拟服务已结束；浏览器可能需要刷新定位" : state.message;
        try { if (session != null) session.stop(); }
        catch (Exception cleanup) { message = "服务已结束，但模拟源清理失败。请重新选择模拟位置应用或重启手机"; }
        state = new State(false, message, null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
