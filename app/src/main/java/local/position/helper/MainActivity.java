package local.position.helper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.net.URI;
import java.util.*;

public final class MainActivity extends Activity {
    private Ui ui;
    private SharedPreferences prefs;
    private SavedPlaces places;
    private LinearLayout shell, list, runningStrip;
    private TextView runningText;
    private String screen = "home", query = "", selectedId = "", selectedName = "";
    private GeoPoint selected;
    private TextView stateTitle, stateDetail, setupHint;
    private Button primary, stop;
    private boolean onboarding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 750); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); ui = new Ui(this); prefs = getSharedPreferences("settings", MODE_PRIVATE);
        places = new SavedPlaces(getFilesDir().toPath().resolve("saved-places-v1.bin"));
        selectedId = prefs.getString("selected_id", ""); selectedName = prefs.getString("selected_name", "自选位置");
        try { selected = GeoPoint.parse(prefs.getString("latitude", ""), prefs.getString("longitude", "")); }
        catch (IllegalArgumentException ignored) { }
        onboarding = !prefs.getBoolean("setup_seen_v2", false);
        if (saved != null) { screen = saved.getString("screen", "home"); query = saved.getString("query", ""); onboarding = saved.getBoolean("onboarding", onboarding); }
        else if (onboarding) screen = "settings";
        render();
    }
    private void render() {
        stateTitle = null; primary = null; setupHint = null;
        shell = ui.column(); shell.setBackgroundColor(Ui.BG); Ui.insets(shell); setContentView(shell);
        if ("places".equals(screen)) renderPlaces(); else if ("settings".equals(screen)) renderSettings(); else renderHome();
        runningStrip=null;
        if(!"home".equals(screen) && !onboarding){
            runningStrip=ui.row();runningStrip.setPadding(ui.dp(20),ui.dp(4),ui.dp(12),ui.dp(4));runningStrip.setBackgroundColor(Ui.SOFT);
            runningText=ui.text("",12,Ui.ACCENT,true);runningStrip.addView(runningText,new LinearLayout.LayoutParams(0,-2,1));
            runningStrip.addView(ui.link("停止模拟",v -> stopMock()));ui.add(shell,runningStrip);
        }
        if (!onboarding) navigation();
        updateStatus();
    }
    private void navigate(String next) {
        View focus = getCurrentFocus();
        if (focus != null) getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        screen = next; onboarding = false; prefs.edit().putBoolean("setup_seen_v2", true).apply(); render();
    }
    private void navigation() {
        LinearLayout nav = ui.row(); nav.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)); nav.setBackgroundColor(Ui.WHITE);
        String[] ids = {"home", "places", "settings"}, labels = {"定位", "地点", "设置"}, icons = {"pin", "places", "settings"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i]; boolean active = screen.equals(id);
            LinearLayout item = ui.column(); item.setGravity(Gravity.CENTER); item.setMinimumHeight(ui.dp(58));
            item.setContentDescription("导航：" + labels[i]); item.setSelected(active); item.setFocusable(true);
            item.setBackground(ui.shape(active ? Ui.SOFT : Ui.WHITE, 18, false));
            item.addView(new Ui.Symbol(this, icons[i], active ? Ui.ACCENT : Ui.MUTED), new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            ui.gap(item, 3); item.addView(ui.text(labels[i], 12, active ? Ui.ACCENT : Ui.MUTED, active));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(ui.dp(3),0,ui.dp(3),0); nav.addView(item,lp);
            item.setOnClickListener(v -> navigate(id));
        }
        ui.add(shell, nav);
    }
    private void renderHome() {
        ui.toolbar(shell, "定点助手", false, null);
        LinearLayout content = ui.content(); ui.scroll(shell, content);
        ui.add(content, ui.text("选好地点，随时开始与停止",14,Ui.MUTED,false)); ui.gap(content,18);
        LinearLayout status = ui.card(content); status.setBackground(ui.shape(Ui.SOFT,24,false));
        stateTitle = ui.text("未在模拟",19,Ui.ACCENT,true); ui.add(status,stateTitle); ui.gap(status,6);
        stateDetail = ui.text("",13,Ui.MUTED,false); ui.add(status,stateDetail);
        setupHint = ui.text("",13,Ui.ACCENT,true); setupHint.setMinHeight(ui.dp(48)); setupHint.setGravity(Gravity.CENTER_VERTICAL);
        setupHint.setOnClickListener(v -> navigate("settings")); ui.add(content,setupHint);
        ui.gap(content,16);
        LinearLayout target = ui.card(content);
        LinearLayout heading = ui.row(); heading.addView(ui.text("目标地点",13,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        Button change = ui.link(selected == null ? "选择" : "更换", v -> navigate("places")); heading.addView(change); ui.add(target,heading);
        LinearLayout location = ui.row(); location.addView(new Ui.Symbol(this,"pin",Ui.ACCENT),new LinearLayout.LayoutParams(ui.dp(42),ui.dp(48)));
        LinearLayout title = ui.column(); title.setPadding(ui.dp(12),0,0,0);
        ui.add(title,ui.text(selected == null ? "还没有选择地点" : selectedName,22,Ui.INK,true)); ui.gap(title,6);
        ui.add(title,ui.text(selected == null ? "从地点簿选择，或新增一个位置" : coords(selected),13,Ui.MUTED,false));
        location.addView(title,new LinearLayout.LayoutParams(0,-2,1)); ui.add(target,location); ui.gap(target,4);
        ui.gap(content,22); ui.add(content,ui.text("新增目标",15,Ui.INK,true)); ui.gap(content,10);
        LinearLayout quick = ui.row();
        Button capture = ui.button("记录当前位置",false,v -> editor(null,"current",true));
        Button map = ui.button("地图选点",false,v -> editor(null,"map",true));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,-2,1); half.setMargins(0,0,ui.dp(8),0); quick.addView(capture,half);
        quick.addView(map,new LinearLayout.LayoutParams(0,-2,1)); ui.add(content,quick);
        ui.add(content,ui.link("手动输入经纬度",v -> editor(null,"manual",true)));
        if (!prefs.getString("url", "").isEmpty()) { ui.gap(content,8); ui.add(content,ui.link("打开常用网页  ↗",v -> openWebsite())); }
        LinearLayout dock = ui.dock(shell);
        primary = ui.button("开始模拟",true,v -> startOrChoose()); primary.setId(1101); ui.add(dock,primary);
        stop = ui.button("停止模拟",false,v -> stopMock()); stop.setId(1102); ui.gap(dock,8); ui.add(dock,stop);
    }
    private void updateStatus() {
        if ("home".equals(screen) && stateTitle != null) {
            MockLocationService.State s = MockLocationService.state();
            stateTitle.setText(s.running ? "●  正在模拟" : "○  未在模拟");
            stateDetail.setText(s.running ? "当前生效\n" + coords(s.point) : s.message);
            int missing = DeviceAccess.missing(this);
            setupHint.setText(missing == 0 ? "" : "还有 " + missing + " 项设置待完成  ›");
            setupHint.setVisibility(missing == 0 ? View.GONE : View.VISIBLE);
            boolean same = selected != null && s.running && same(selected,s.point);
            primary.setText(selected == null ? "选择目标地点" : same ? "正在使用此位置" : s.running ? "切换到目标地点" : "开始模拟");
            primary.setEnabled(!same); primary.setAlpha(same ? .55f : 1f);
            stop.setVisibility(s.running ? View.VISIBLE : View.GONE);
        } else if ("settings".equals(screen)) updateChecks();
        if(runningStrip!=null){MockLocationService.State s=MockLocationService.state();runningStrip.setVisibility(s.running?View.VISIBLE:View.GONE);
            if(s.running)runningText.setText("模拟中\n"+coords(s.point));}
    }
    private static boolean same(GeoPoint a, GeoPoint b) { return Math.abs(a.latitude-b.latitude)<0.0000005 && Math.abs(a.longitude-b.longitude)<0.0000005; }
    private void startOrChoose() {
        if (selected == null) { navigate("places"); return; }
        if (!DeviceAccess.ready(this)) { navigate("settings"); ui.message("完成标记为待开启的设置，再回到定位页开始"); return; }
        try { startForegroundService(new Intent(this,MockLocationService.class).putExtra("latitude",selected.latitude).putExtra("longitude",selected.longitude)); }
        catch (RuntimeException e) { ui.message("暂时无法开始："+e.getMessage()); }
    }
    private void stopMock() { if (MockLocationService.state().running) startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.STOP)); }
    private void pick(SavedPlaces.Place p) { selected = p.point; selectedId = p.id; selectedName = p.name; persistSelection(); navigate("home"); }
    private void persistSelection() {
        prefs.edit().putString("selected_id",selectedId).putString("selected_name",selectedName)
            .putString("latitude",selected==null ? "" : Double.toString(selected.latitude))
            .putString("longitude",selected==null ? "" : Double.toString(selected.longitude)).apply();
    }
    private void renderPlaces() {
        ui.toolbar(shell,"地点簿",false,v -> editor(null,"manual",false));
        LinearLayout top = ui.content(); top.setPadding(ui.dp(20),0,ui.dp(20),ui.dp(12));
        EditText search = ui.field("搜索地点名称",1201,InputType.TYPE_CLASS_TEXT); search.setText(query); ui.add(top,search); ui.add(shell,top);
        list = ui.content(); ui.scroll(shell,list); refreshPlaces();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString();refreshPlaces();}
        });
    }
    private void refreshPlaces() {
        if (list == null || !"places".equals(screen)) return;
        list.removeAllViews();
        try {
            List<SavedPlaces.Place> all = places.list(); int found=0;
            if (all.isEmpty()) {
                LinearLayout empty=ui.card(list); ui.add(empty,ui.text("把常用位置存下来",22,Ui.INK,true));ui.gap(empty,10);
                ui.add(empty,ui.text("到达现场时记录坐标，起个熟悉的名字。下次直接选用。",14,Ui.MUTED,false));ui.gap(empty,20);
                ui.add(empty,ui.button("记录当前位置",true,v -> editor(null,"current",false)));
                ui.add(empty,ui.link("地图选点或手动添加",v -> editor(null,"manual",false))); return;
            }
            for (SavedPlaces.Place p : all) {
                if (!p.name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))) continue;
                found++;
                LinearLayout card=ui.card(list); card.setPadding(ui.dp(16),ui.dp(12),ui.dp(12),ui.dp(14));
                LinearLayout row=ui.row(); TextView title=ui.text(p.name,18,Ui.INK,true); row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                Button more=ui.link("⋮",v -> placeMenu(v,p)); more.setTextSize(22);more.setContentDescription("管理地点："+p.name);
                row.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));ui.add(card,row);
                ui.add(card,ui.text(coords(p.point),13,Ui.MUTED,false));ui.gap(card,6);
                LinearLayout actions=ui.row(); TextView tag=ui.text(p.measuredAt>0 ? "手机实测 · 精度约 "+Math.round(p.accuracy)+" 米" : "手动 / 地图选点",12,Ui.MUTED,false);
                actions.addView(tag,new LinearLayout.LayoutParams(0,-2,1));actions.addView(ui.link("使用",v -> pick(p)));ui.add(card,actions);
                card.setOnClickListener(v -> editor(p,"manual",false));ui.gap(list,12);
            }
            if(found==0) {ui.gap(list,30);ui.add(list,ui.text("没有找到“"+query+"”",18,Ui.INK,true));ui.gap(list,8);ui.add(list,ui.text("换个名称搜索，或点右上角新增地点。",14,Ui.MUTED,false));}
        } catch(Exception e) {ui.add(list,ui.text("地点暂时无法读取",18,Ui.RED,true));ui.add(list,ui.text("原有文件已保留。"+e.getMessage(),13,Ui.MUTED,false));}
    }
    private void placeMenu(View anchor,SavedPlaces.Place p) {
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("编辑地点");menu.getMenu().add("删除地点");
        menu.setOnMenuItemClickListener(item -> {
            if(item.getTitle().equals("编辑地点")) editor(p,"manual",false);
            else new AlertDialog.Builder(this).setTitle("删除“"+p.name+"”？").setMessage("删除后无法撤销；当前模拟不会停止。")
                .setNegativeButton("取消",null).setPositiveButton("删除",(d,w) -> {
                    try{places.delete(p.id);if(selectedId.equals(p.id)){selected=null;selectedId="";selectedName="";persistSelection();}refreshPlaces();ui.message("地点已删除");}
                    catch(Exception e){ui.message("删除失败："+e.getMessage());}
                }).show();return true;
        });menu.show();
    }
    private void editor(SavedPlaces.Place p,String source,boolean useAfter) {
        Intent i=new Intent(this,PlaceEditorActivity.class).putExtra("source",source).putExtra("use_after",useAfter);
        if(p!=null)i.putExtra("id",p.id);startActivityForResult(i,20);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==20 && result==RESULT_OK && data!=null) {
            try{for(SavedPlaces.Place p:places.list())if(p.id.equals(data.getStringExtra("id"))){
                if(data.getBooleanExtra("use_after",false)){pick(p);return;}
                if(selectedId.equals(p.id)){selected=p.point;selectedName=p.name;persistSelection();}
                query="";navigate("places");ui.message("地点已保存");return;
            }}catch(Exception e){ui.message("无法读取保存结果："+e.getMessage());}
        }
    }
    private TextView fineCheck,mockCheck,gpsCheck,noticeCheck;
    private Button finishSetup;
    private void renderSettings() {
        ui.toolbar(shell,onboarding ? "先完成定位设置" : "设置",false,null);
        LinearLayout c=ui.content();ui.scroll(shell,c);
        if(onboarding){ui.add(c,ui.text("模拟前需要以下 3 项。也可以稍后设置，先整理地点。",14,Ui.MUTED,false));ui.gap(c,20);}
        ui.add(c,ui.text("定位准备",14,Ui.MUTED,true));ui.gap(c,10);
        LinearLayout checks=ui.card(c);checks.setPadding(ui.dp(16),ui.dp(6),ui.dp(16),ui.dp(6));
        mockCheck=settingRow(checks,"模拟位置应用","在开发者选项中选择定点助手",v -> DeviceAccess.open(this,Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        fineCheck=settingRow(checks,"精确位置权限","用于读取手机当前位置和运行模拟",v -> {if(DeviceAccess.fine(this))DeviceAccess.appInfo(this);else DeviceAccess.requestLocation(this);});
        gpsCheck=settingRow(checks,"手机定位开关","保持系统位置信息开启",v -> DeviceAccess.open(this,Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        ui.gap(c,22);ui.add(c,ui.text("通知与使用",14,Ui.MUTED,true));ui.gap(c,10);
        LinearLayout options=ui.card(c);options.setPadding(ui.dp(16),ui.dp(6),ui.dp(16),ui.dp(6));
        noticeCheck=settingRow(options,"运行通知","在通知栏查看状态并停止模拟",v -> {if(Build.VERSION.SDK_INT>=33 && !DeviceAccess.notifications(this))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);else DeviceAccess.appInfo(this);});
        settingRow(options,"常用网页",prefs.getString("url","").isEmpty()?"设置快捷打开的网址":prefs.getString("url",""),v -> editWebsite());
        settingRow(options,"realme 设置帮助","开发者选项、后台运行与恢复定位",v -> help());
        ui.gap(c,18);ui.add(c,ui.text("定点助手 1.2\n地点仅保存在本机；地图加载使用 OpenStreetMap。",12,Ui.MUTED,false));
        if(onboarding){LinearLayout dock=ui.dock(shell);finishSetup=ui.button("完成设置",true,v -> navigate("home"));ui.add(dock,finishSetup);ui.add(dock,ui.link("稍后设置，先看看",v -> navigate("home")));}
    }
    private TextView settingRow(LinearLayout parent,String title,String description,View.OnClickListener click) {
        LinearLayout row=ui.row();row.setPadding(0,ui.dp(14),0,ui.dp(14));row.setMinimumHeight(ui.dp(76));row.setOnClickListener(click);row.setFocusable(true);
        LinearLayout labels=ui.column();ui.add(labels,ui.text(title,16,Ui.INK,true));ui.gap(labels,5);ui.add(labels,ui.text(description,12,Ui.MUTED,false));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView detail=ui.text("›",13,Ui.ACCENT,true);detail.setPadding(ui.dp(12),0,0,0);row.addView(detail);ui.add(parent,row);
        View divider=new View(this);divider.setBackgroundColor(Ui.LINE);parent.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));return detail;
    }
    private void updateChecks() {
        if(fineCheck==null)return;
        mockCheck.setText(DeviceAccess.mock(this)?"已选择":"待选择 ›");fineCheck.setText(DeviceAccess.fine(this)?"已允许":"待允许 ›");
        gpsCheck.setText(DeviceAccess.location(this)?"已开启":"待开启 ›");noticeCheck.setText(DeviceAccess.notifications(this)?"已允许":"可开启 ›");
        if(onboarding && finishSetup!=null){finishSetup.setEnabled(DeviceAccess.ready(this));finishSetup.setAlpha(DeviceAccess.ready(this)?1f:.45f);}
    }
    private void editWebsite() {
        EditText field=ui.field("https://网页地址",1301,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);field.setText(prefs.getString("url",""));
        LinearLayout box=ui.content();ui.add(box,field);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("常用网页").setView(box).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try{String value=field.getText().toString().trim();if(!value.isEmpty())value=validUrl(value);prefs.edit().putString("url",value).apply();dialog.dismiss();render();}
            catch(Exception e){field.setError("请输入完整的 http 或 https 地址");}
        }));dialog.show();
    }
    private String validUrl(String value)throws Exception {
        if(!value.contains(":"))value="https://"+value;URI u=new URI(value);
        if(!("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();return value;
    }
    private void openWebsite(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(validUrl(prefs.getString("url","")))).addCategory(Intent.CATEGORY_BROWSABLE));}catch(Exception e){ui.message("无法打开网页，请在设置中检查地址");}}
    private void help(){new AlertDialog.Builder(this).setTitle("realme 使用帮助").setMessage("首次配置\n在“关于本机 / 版本信息”中连续点击版本号，启用开发者选项；再找到“选择模拟位置信息应用”，选择定点助手。无需开启 USB 调试。\n\n后台运行\n允许应用后台运行和通知。不同 realme 系统的省电设置可能影响持续模拟。\n\n恢复定位\n先点击停止模拟，再刷新浏览器。若仍未恢复，将系统模拟位置应用改为“无”，必要时重启手机。\n\n记录实际位置\n先停止模拟，再到地点簿记录当前位置。记录会显示采集时间和精度。\n\n应用状态仅说明模拟服务状态，不代表网页已收到该坐标或签到成功。").setPositiveButton("知道了",null).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);updateStatus();if(r==10&&!DeviceAccess.fine(this))new AlertDialog.Builder(this).setTitle("需要精确位置权限").setMessage("请允许使用期间的精确位置。若不再弹出授权，可在应用信息中修改。").setPositiveButton("应用信息",(d,w)->DeviceAccess.appInfo(this)).setNegativeButton("稍后",null).show();}
    @Override public void onBackPressed(){if(!"home".equals(screen)||onboarding)navigate("home");else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);if("places".equals(screen))refreshPlaces();}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("screen",screen);b.putString("query",query);b.putBoolean("onboarding",onboarding);super.onSaveInstanceState(b);}
    static String coords(GeoPoint p){return String.format(Locale.US,"纬度 %.6f · 经度 %.6f",p.latitude,p.longitude);}
    static void inset(View root){Ui.insets(root);}
}
