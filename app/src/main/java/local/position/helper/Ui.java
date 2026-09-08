package local.position.helper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared native spacing, typography, touch targets, and screen chrome. */
final class Ui {
    static final int BG = Color.rgb(246,248,247), INK = Color.rgb(24,42,39), MUTED = Color.rgb(98,114,109);
    static final int ACCENT = Color.rgb(10,108,90), SOFT = Color.rgb(227,241,233), LINE = Color.rgb(225,231,227);
    static final int RED = Color.rgb(173,51,48), WHITE = Color.WHITE;
    final Activity a;
    Ui(Activity activity) { a = activity; }
    int dp(int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v = new LinearLayout(a); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(a); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(a); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1);
        if (bold) v.setTypeface(null, Typeface.BOLD); return v;
    }
    GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), LINE); return d;
    }
    Button button(String value, boolean primary, View.OnClickListener click) {
        Button b = new Button(a); b.setText(value); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? WHITE : ACCENT); b.setMinHeight(dp(52)); b.setMinimumWidth(0);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000), shape(primary ? ACCENT : SOFT, 18, false), null));
        b.setOnClickListener(click); return b;
    }
    Button link(String value, View.OnClickListener click) {
        Button b = button(value, false, click); b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000), shape(Color.TRANSPARENT, 12, false), null)); return b;
    }
    EditText field(String hint, int id, int inputType) {
        EditText e = new EditText(a); e.setId(id); e.setHint(hint); e.setContentDescription(hint); e.setSingleLine(); e.setTextSize(16);
        e.setTextColor(INK); e.setHintTextColor(MUTED); e.setInputType(inputType); e.setMinHeight(dp(54));
        e.setPadding(dp(14), dp(12), dp(14), dp(12)); e.setBackground(shape(WHITE, 14, true)); return e;
    }
    void gap(LinearLayout parent, int height) { View v = new View(a); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    void add(LinearLayout parent, View v) { parent.addView(v, new LinearLayout.LayoutParams(-1, -2)); }
    LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(20), dp(20), dp(20), dp(20)); c.setBackground(shape(WHITE, 24, true)); add(parent, c); return c;
    }
    ScrollView scroll(LinearLayout parent, LinearLayout content) {
        ScrollView s = new ScrollView(a); s.setFillViewport(true); s.setClipToPadding(false); s.addView(content);
        parent.addView(s, new LinearLayout.LayoutParams(-1, 0, 1)); return s;
    }
    LinearLayout content() { LinearLayout c = column(); c.setPadding(dp(20), dp(8), dp(20), dp(20)); return c; }
    LinearLayout toolbar(LinearLayout parent, String title, boolean back, View.OnClickListener action) {
        LinearLayout bar = row(); bar.setPadding(dp(back ? 8 : 20), dp(8), dp(12), dp(8));
        if (back) { Button b = link("‹", v -> a.onBackPressed()); b.setContentDescription("返回"); b.setTextSize(30); bar.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48))); }
        TextView t = text(title, back ? 22 : 28, INK, true); bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) { Button add = link("＋", action); add.setTextSize(25); add.setContentDescription("新增地点"); bar.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48))); }
        add(parent, bar); return bar;
    }
    LinearLayout dock(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(20), dp(10), dp(20), dp(12)); c.setBackgroundColor(BG); add(parent, c); return c;
    }
    void message(String text) { Toast.makeText(a, text, Toast.LENGTH_LONG).show(); }
    static void insets(View root) {
        root.setOnApplyWindowInsetsListener((v, i) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets s = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(s.left,s.top,s.right,s.bottom); return WindowInsets.CONSUMED;
            }
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i.consumeSystemWindowInsets();
        });
    }
    static final class Symbol extends View {
        private final String kind; private final int color; private final Paint p = new Paint(3);
        Symbol(Context context, String kind, int color) { super(context); this.kind=kind; this.color=color; }
        @Override protected void onDraw(Canvas c) {
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            c.save(); c.translate((getWidth()-24*scale)/2, (getHeight()-24*scale)/2); c.scale(scale,scale);
            p.setColor(color); p.setStrokeWidth(1.8f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStyle(Paint.Style.STROKE);
            if ("places".equals(kind)) {
                c.drawRoundRect(5,3,19,21,2,2,p); c.drawLine(9,8,15,8,p); c.drawLine(9,12,15,12,p); c.drawLine(9,16,13,16,p);
            } else if ("settings".equals(kind)) {
                c.drawLine(4,6,20,6,p); c.drawLine(4,12,20,12,p); c.drawLine(4,18,20,18,p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(9,6,2.5f,p);c.drawCircle(15,12,2.5f,p);c.drawCircle(10,18,2.5f,p);
            } else {
                Path path=new Path(); path.moveTo(12,22);path.cubicTo(9,18,4,13,4,9);path.cubicTo(4,-1,20,-1,20,9);path.cubicTo(20,13,15,18,12,22);c.drawPath(path,p);c.drawCircle(12,9,3,p);
            }
            c.restore();
        }
    }
}
