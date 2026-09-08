package local.position.helper;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class PlaceEditorActivity extends Activity {
    private Ui ui;
    private SavedPlaces store;
    private EditText name, latitude, longitude;
    private TextView info, error;
    private Button save, capture;
    private String id;
    private boolean applying, dirty, useAfter, permissionPending;
    private long measuredAt;
    private float accuracy = -1;
    private LiveFix collector;
    private LocationListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> finishCapture();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);ui=new Ui(this);store=new SavedPlaces(getFilesDir().toPath().resolve("saved-places-v1.bin"));
        id=getIntent().getStringExtra("id");useAfter=getIntent().getBooleanExtra("use_after",false);
        LinearLayout root=ui.column();root.setBackgroundColor(Ui.BG);Ui.insets(root);setContentView(root);
        ui.toolbar(root,id==null ? "新增地点" : "编辑地点",true,null);
        LinearLayout c=ui.content();ui.scroll(root,c);
        ui.add(c,ui.text("给位置起个容易找到的名字",14,Ui.MUTED,false));ui.gap(c,22);
        ui.add(c,ui.text("地点名称",14,Ui.INK,true));ui.gap(c,8);
        name=ui.field("例如：公司门口",2101,InputType.TYPE_CLASS_TEXT);ui.add(c,name);ui.gap(c,24);
        ui.add(c,ui.text("位置坐标",14,Ui.INK,true));ui.gap(c,10);
        LinearLayout sources=ui.row();capture=ui.button("读取当前位置",false,v -> record());
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMargins(0,0,ui.dp(8),0);sources.addView(capture,half);
        sources.addView(ui.button("地图选点",false,v -> map()),new LinearLayout.LayoutParams(0,-2,1));ui.add(c,sources);ui.gap(c,16);
        int numeric=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;
        ui.add(c,ui.text("纬度",13,Ui.MUTED,false));ui.gap(c,6);latitude=ui.field("-90 到 90",2102,numeric);ui.add(c,latitude);ui.gap(c,14);
        ui.add(c,ui.text("经度",13,Ui.MUTED,false));ui.gap(c,6);longitude=ui.field("-180 到 180",2103,numeric);ui.add(c,longitude);ui.gap(c,14);
        info=ui.text("可直接填写 WGS84 坐标，或使用上方两种方式选点。",13,Ui.MUTED,false);ui.add(c,info);
        error=ui.text("",13,Ui.RED,false);ui.gap(c,12);ui.add(c,error);
        ui.gap(c,10);ui.add(c,ui.text("高德、百度等地图的坐标可能存在偏移，请勿直接混填。",12,Ui.MUTED,false));
        LinearLayout dock=ui.dock(root);save=ui.button(useAfter?"保存并选用":"保存地点",true,v -> save());save.setId(2104);ui.add(dock,save);
        if(useAfter){ui.gap(dock,6);ui.add(dock,ui.text("选用后回到定位页，由你点击开始模拟。",12,Ui.MUTED,false));}
        if(saved!=null) {
            name.setText(saved.getString("name",""));latitude.setText(saved.getString("lat",""));longitude.setText(saved.getString("lon",""));
            measuredAt=saved.getLong("measuredAt");accuracy=saved.getFloat("accuracy",-1);dirty=saved.getBoolean("dirty");updateInfo();
        } else if(id!=null) {
            try{boolean found=false;for(SavedPlaces.Place p:store.list())if(id.equals(p.id)){
                name.setText(p.name);setPoint(p.point);measuredAt=p.measuredAt;accuracy=p.accuracy;found=true;updateInfo();break;
            }if(!found){ui.message("这个地点已不存在");finish();return;}}
            catch(Exception e){error.setText("无法读取地点，原文件已保留："+e.getMessage());save.setEnabled(false);}
        }
        watch(name,false);watch(latitude,true);watch(longitude,true);
        if(saved==null && id==null) {
            String source=getIntent().getStringExtra("source");
            if("map".equals(source))handler.post(() -> map());
            else if("current".equals(source))handler.post(() -> record());
        } else if(saved!=null && saved.getBoolean("capturing"))handler.post(() -> record());
    }
    private void watch(EditText field,boolean coordinate) {
        field.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int st,int b,int c){if(applying)return;dirty=true;error.setText("");
                if(coordinate){measuredAt=0;accuracy=-1;updateInfo();}
            }
        });
    }
    private void setPoint(GeoPoint p){applying=true;latitude.setText(String.format(Locale.US,"%.6f",p.latitude));longitude.setText(String.format(Locale.US,"%.6f",p.longitude));applying=false;}
    private void updateInfo(){info.setText(measuredAt>0 ? "手机实测 · "+new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(measuredAt))+"\n精度约 "+Math.round(accuracy)+" 米" : "手动输入或地图坐标 · WGS84");}
    private void map(){cancelCapture();Intent i=new Intent(this,MapActivity.class);try{GeoPoint p=point();i.putExtra("latitude",p.latitude).putExtra("longitude",p.longitude);}catch(Exception ignored){}startActivityForResult(i,30);}
    private GeoPoint point(){return GeoPoint.parse(latitude.getText().toString(),longitude.getText().toString());}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==30 && result==RESULT_OK && data!=null){try{setPoint(new GeoPoint(data.getDoubleExtra("latitude",Double.NaN),data.getDoubleExtra("longitude",Double.NaN)));measuredAt=0;accuracy=-1;dirty=true;error.setText("");updateInfo();}catch(Exception e){error.setText(e.getMessage());}}
    }
    private void save(){
        if(listener!=null)return;error.setText("");
        if(name.getText().toString().trim().isEmpty()){name.setError("请输入地点名称");name.requestFocus();return;}
        GeoPoint p;
        try{p=point();}catch(IllegalArgumentException e){error.setText(e.getMessage());if(e.getMessage().startsWith("经度"))longitude.setError(e.getMessage());else latitude.setError(e.getMessage());return;}
        try {
            if(id==null)id=store.add(name.getText().toString(),p,measuredAt,accuracy).id;
            else store.update(id,name.getText().toString(),p,measuredAt,accuracy);
            setResult(RESULT_OK,new Intent().putExtra("id",id).putExtra("use_after",useAfter));dirty=false;finish();
        } catch(Exception e){error.setText("保存失败："+e.getMessage());}
    }
    @SuppressWarnings("deprecation") private void record(){
        error.setText("");
        if(MockLocationService.state().running){new AlertDialog.Builder(this).setTitle("先停止模拟，再记录实际位置")
            .setMessage("当前正在模拟其他地点。停止后再获取手机所在位置。").setNegativeButton("取消",null)
            .setPositiveButton("停止并继续",(d,w)->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.STOP));waitForStop(0);}).show();return;}
        if(!DeviceAccess.fine(this)){permissionPending=true;DeviceAccess.requestLocation(this);return;}
        if(!DeviceAccess.location(this)){error.setText("手机定位开关未开启。开启后再次点击读取当前位置。");DeviceAccess.open(this,Settings.ACTION_LOCATION_SOURCE_SETTINGS);return;}
        cancelCapture();collector=new LiveFix(SystemClock.elapsedRealtimeNanos());capture.setEnabled(false);save.setEnabled(false);capture.setText("正在定位…");
        info.setText("正在读取新位置，最多等待 15 秒。可继续填写名称。");
        LocationManager manager=getSystemService(LocationManager.class);
        listener=new LocationListener(){
            public void onLocationChanged(Location l){if(listener!=this||collector==null)return;
                try{boolean done=collector.offer(new GeoPoint(l.getLatitude(),l.getLongitude()),l.isFromMockProvider(),l.getElapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos(),l.getTime(),l.hasAccuracy()?l.getAccuracy():Float.NaN);
                    if(done)finishCapture();else if(collector.best()!=null)info.setText("已收到位置，精度约 "+Math.round(collector.best().accuracy)+" 米，继续等待更精确的结果…");
                }catch(IllegalArgumentException ignored){}
            }
            public void onStatusChanged(String p,int s,Bundle e){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}
        };
        try{int count=0;for(String provider:Arrays.asList("gps","network"))if(manager.isProviderEnabled(provider)){manager.requestLocationUpdates(provider,1000,0,listener,Looper.getMainLooper());count++;}
            if(count==0){cancelCapture();error.setText("没有可用定位源，可尝试地图选点或手动输入。");}else handler.postDelayed(timeout,15000);
        }catch(RuntimeException e){cancelCapture();error.setText("无法读取位置："+e.getMessage());}
    }
    private void waitForStop(int attempt){if(isFinishing()||isDestroyed())return;if(!MockLocationService.state().running){record();return;}if(attempt>=20){error.setText("模拟尚未停止，请返回定位页检查。");return;}handler.postDelayed(()->waitForStop(attempt+1),200);}
    private void finishCapture(){LiveFix.Sample sample=collector==null?null:collector.best();cancelCapture();
        if(sample==null){error.setText("未取得新位置。可以重试或地图选点，原有坐标未改变。");updateInfo();return;}
        setPoint(sample.point);measuredAt=sample.measuredAt;accuracy=sample.accuracy;dirty=true;updateInfo();
        if(accuracy>100)error.setText("本次精度较低，保存前请核对目标位置。");
    }
    private void cancelCapture(){handler.removeCallbacks(timeout);if(listener!=null){LocationListener old=listener;listener=null;try{getSystemService(LocationManager.class).removeUpdates(old);}catch(RuntimeException ignored){}updateInfo();}collector=null;if(capture!=null){capture.setEnabled(true);capture.setText("读取当前位置");}if(save!=null)save.setEnabled(true);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&permissionPending){permissionPending=false;if(DeviceAccess.fine(this))record();else{error.setText("没有精确位置权限。你仍可手动输入或地图选点。");new AlertDialog.Builder(this).setTitle("未获得精确位置权限").setMessage("如系统不再弹出授权，可在应用信息中修改。").setPositiveButton("应用信息",(d,w)->DeviceAccess.appInfo(this)).setNegativeButton("继续编辑",null).show();}}}
    @Override public void onBackPressed(){if(dirty)new AlertDialog.Builder(this).setTitle("放弃尚未保存的修改？").setNegativeButton("继续编辑",null).setPositiveButton("放弃修改",(d,w)->finish()).show();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("name",name.getText().toString());b.putString("lat",latitude.getText().toString());b.putString("lon",longitude.getText().toString());b.putLong("measuredAt",measuredAt);b.putFloat("accuracy",accuracy);b.putBoolean("dirty",dirty);b.putBoolean("capturing",listener!=null);super.onSaveInstanceState(b);}
    @Override protected void onRestoreInstanceState(Bundle b){applying=true;super.onRestoreInstanceState(b);applying=false;measuredAt=b.getLong("measuredAt");accuracy=b.getFloat("accuracy",-1);dirty=b.getBoolean("dirty");updateInfo();}
    @Override protected void onPause(){cancelCapture();handler.removeCallbacksAndMessages(null);super.onPause();}
}
