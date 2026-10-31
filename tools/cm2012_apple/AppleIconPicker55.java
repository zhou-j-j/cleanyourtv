package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Landscape D-pad icon picker for one package; independent of Home/Library windows. */
public final class AppleIconPicker55 extends Activity {
    private static final int CHOOSE_PICTURE = 45501;
    private String pkg;
    public static void open(Activity from, String pkg) {
        if (from == null || pkg == null || pkg.isEmpty()) return;
        Intent intent = new Intent(from, AppleIconPicker55.class);
        intent.putExtra("package", pkg);
        from.startActivity(intent);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        pkg = getIntent().getStringExtra("package");
        if (pkg == null || pkg.isEmpty()) { finish(); return; }
        build();
    }
    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(17)); d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView txt(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); return t;
    }
    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff111a2b);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(25), dp(12), dp(25), dp(15));
        scroll.addView(page);
        String label = pkg;
        try { label = String.valueOf(getPackageManager().getApplicationLabel(
            getPackageManager().getApplicationInfo(pkg, 0))); }
        catch (Exception ignored) {}
        TextView title = txt("更改图标 · " + label, 24, Color.WHITE);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView sub = txt("仅改变苹果桌面显示，应用名称和 APP 本身不变", 15, 0xffb8c9df);
        page.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setUseDefaultMargins(true);
        page.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < AppleIconManager55.NAMES.length; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setPadding(dp(6), dp(8), dp(6), dp(5));
            cell.setBackground(background(0xff253754, 0x668faacb));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(AppleIconManager55.preview(i));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(67), dp(67)));
            TextView name = txt(AppleIconManager55.NAMES[i], 13, Color.WHITE);
            cell.addView(name, new LinearLayout.LayoutParams(dp(86), dp(28)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(103); lp.height = dp(119);
            lp.setMargins(dp(5), dp(4), dp(5), dp(4));
            grid.addView(cell, lp);
            cell.setOnClickListener(v -> {
                AppleIconManager55.useBuiltin(this, pkg, index);
                Toast.makeText(this, "图标已更改", Toast.LENGTH_SHORT).show();
                finish();
            });
            cell.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(background(focused ? 0xff3f649c : 0xff253754,
                        focused ? 0xffaed9ff : 0x668faacb));
                v.animate().scaleX(focused ? 1.05f : 1f)
                        .scaleY(focused ? 1.05f : 1f).setDuration(140).start();
            });
            if (i == 0) cell.requestFocus();
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(55)));
        Button choose = new Button(this);
        choose.setText("从本地选择 PNG / JPG");
        actions.addView(choose, new LinearLayout.LayoutParams(0, -1, 1));
        choose.setOnClickListener(v -> browse());
        Button reset = new Button(this);
        reset.setText("恢复原图标");
        actions.addView(reset, new LinearLayout.LayoutParams(0, -1, 1));
        reset.setOnClickListener(v -> {
            AppleIconManager55.reset(this, pkg);
            Toast.makeText(this, "已恢复原图标", Toast.LENGTH_SHORT).show();
            finish();
        });
        Button cancel = new Button(this);
        cancel.setText("取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1));
        cancel.setOnClickListener(v -> finish());
        setContentView(scroll);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }
    private void browse() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg", "image/webp"});
        try { startActivityForResult(i, CHOOSE_PICTURE); }
        catch (Exception e) {
            Toast.makeText(this, "盒子缺少可用的文件选择器", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != CHOOSE_PICTURE || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (AppleIconManager55.saveFromUri(this, pkg, uri)) {
            Toast.makeText(this, "自定义图标已保存", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "无法读取所选图片，请换一张 PNG 或 JPG", Toast.LENGTH_LONG).show();
        }
    }
}
