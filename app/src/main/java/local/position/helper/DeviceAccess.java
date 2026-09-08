package local.position.helper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;

final class DeviceAccess {
    static boolean fine(Activity a) { return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    static boolean notifications(Activity a) { return Build.VERSION.SDK_INT<33 || a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED; }
    static boolean mock(Activity a) { return a.getSystemService(AppOpsManager.class).checkOpNoThrow("android:mock_location",android.os.Process.myUid(),a.getPackageName())==AppOpsManager.MODE_ALLOWED; }
    static boolean location(Activity a) {
        LocationManager m=a.getSystemService(LocationManager.class);
        return Build.VERSION.SDK_INT>=28 ? m.isLocationEnabled() : m.isProviderEnabled("gps") || m.isProviderEnabled("network");
    }
    static boolean ready(Activity a) { return fine(a) && mock(a) && location(a); }
    static int missing(Activity a) { return (fine(a)?0:1)+(mock(a)?0:1)+(location(a)?0:1); }
    static void requestLocation(Activity a) { a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10); }
    static void open(Activity a, String action) { try { a.startActivity(new Intent(action)); } catch(ActivityNotFoundException e) { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); } }
    static void appInfo(Activity a) { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName()))); }
}
