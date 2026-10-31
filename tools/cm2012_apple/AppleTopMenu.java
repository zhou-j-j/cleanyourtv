package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TV-focused, native Apple-inspired glass top bar. No HDMI-IN claim on an HDMI-OUT box.
 * Self-contained within the launcher APK. Tested structurally, not on CM201-2 hardware.
 */
public final class AppleTopMenu {
    private AppleTopMenu() { }
    private static int dp(Activity a, float x) { return (int) (a.getResources().getDisplayMetrics().density * x + .5f); }
    private static GradientDrawable shape(Activity a, int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, radius));
        g.setStroke(dp(a, 1), stroke);
        return g;
    }

    public static void attach(MainActivity a, LinearLayout bar, View oldInputs, View oldNet, View oldSettings) {
        if (bar.findViewWithTag("cm2012-apple-v4-header") != null) return;
        LinearLayout row = new LinearLayout(a);
        row.setTag("cm2012-apple-v4-header");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(row, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addPill(a, row, "▣  输入源", () -> showInputs(a, row.getChildAt(0)), null);
        addPill(a, row, "⌁  Wi-Fi", () -> openWifi(a), null);
        addPill(a, row, "⚙  设置", () -> openControl(a), () -> {
            a.startActivity(new Intent(a, LauncherSettingsActivity.class));
            return true;
        });
        oldInputs.setFocusable(false);
        oldNet.setFocusable(false);
        oldSettings.setFocusable(false);
        oldInputs.setVisibility(View.GONE);
        oldNet.setVisibility(View.GONE);
        oldSettings.setVisibility(View.GONE);
        // Some firmware callbacks make the original Inputs pill visible later.
        // Keep it suppressed without interfering with the real connection state.
        bar.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (oldInputs.getVisibility() != View.GONE) oldInputs.setVisibility(View.GONE);
            if (oldNet.getVisibility() != View.GONE) oldNet.setVisibility(View.GONE);
            if (oldSettings.getVisibility() != View.GONE) oldSettings.setVisibility(View.GONE);
        });
    }

    private interface LongAction { boolean go(); }
    private static void addPill(Activity a, LinearLayout row, String label, Runnable click, LongAction hold) {
        TextView pill = new TextView(a);
        pill.setText(label);
        pill.setTextSize(16);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setTextColor(Color.WHITE);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(a, 17), dp(a, 9), dp(a, 17), dp(a, 9));
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setBackground(shape(a, 0xB52A2830, 0x6BFFFFFF, 38));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 48));
        p.leftMargin = dp(a, 8);
        row.addView(pill, p);
        pill.setOnClickListener(v -> click.run());
        if (hold != null) pill.setOnLongClickListener(v -> hold.go());
        pill.setOnFocusChangeListener((v, focused) -> {
            pill.setBackground(shape(a, focused ? Color.WHITE : 0xB52A2830,
                    focused ? Color.WHITE : 0x6BFFFFFF, 38));
            pill.setTextColor(focused ? 0xFF101015 : Color.WHITE);
            pill.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(120).start();
        });
        pill.setOnKeyListener((v, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP);
    }

    private static void openWifi(Activity a) {
        try {
            Intent i = new Intent("fiberhome.intent.action.WIRELESS");
            i.setClassName("com.fiberhome.settings", "com.fiberhome.settings.MainActivity");
            a.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(a, "无法打开烽火 Wi-Fi，已进入控制中心", Toast.LENGTH_SHORT).show();
            openControl(a);
        }
    }

    private static void openControl(Activity a) {
        a.startActivity(new Intent(a, AppleControlCenterActivity.class));
    }

    private static void showInputs(MainActivity a, View anchor) {
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a, 13), dp(a, 14), dp(a, 13), dp(a, 14));
        panel.setBackground(shape(a, 0xF027262C, 0x66FFFFFF, 22));
        TextView heading = new TextView(a);
        heading.setText("输入源与快捷入口");
        heading.setTextColor(0xFFEFEFF3);
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(a, 15), dp(a, 5), 0, dp(a, 11));
        panel.addView(heading);
        PopupWindow pop = new PopupWindow(panel, dp(a, 330), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        pop.setOutsideTouchable(true);
        pop.setElevation(dp(a, 15));
        pop.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        addRow(a, panel, pop, "⌂   桌面首页", "返回主界面", () -> pop.dismiss());
        addRow(a, panel, pop, "▦   全部应用库", "查看全部应用、添加到首页", () -> a.openAppleAppLibrary());
        addRow(a, panel, pop, "▤   应用管理", "卸载、应用详情与权限", () -> openControl(a));
        addRow(a, panel, pop, "◉   电视直播", "从应用库选择直播应用", () -> {
            Toast.makeText(a, "请在应用库中选择已安装的电视直播应用", Toast.LENGTH_LONG).show();
            a.openAppleAppLibrary();
        });
        addRow(a, panel, pop, "⌁   无线网络", "烽火原厂 Wi-Fi 管理", () -> openWifi(a));
        addRow(a, panel, pop, "⚙   全部设置", "打开苹果风格控制中心", () -> openControl(a));
        TextView tip = new TextView(a);
        tip.setText("本机为 HDMI 输出盒子，不能切换电视 HDMI1 / HDMI2 输入。 ");
        tip.setTextColor(0xFFA6A2AD);
        tip.setTextSize(12);
        tip.setPadding(dp(a, 15), dp(a, 15), dp(a, 12), dp(a, 6));
        panel.addView(tip);
        pop.showAsDropDown(anchor, -dp(a, 6), dp(a, 10), Gravity.START);
        // First TV-remote focus after popup opens.
        for (int i = 0; i < panel.getChildCount(); i++) {
            View v = panel.getChildAt(i);
            if (v.isFocusable()) { v.requestFocus(); break; }
        }
    }

    private static void addRow(Activity a, LinearLayout panel, PopupWindow pop, String label, String subtitle, Runnable action) {
        LinearLayout item = new LinearLayout(a);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(a, 15), dp(a, 9), dp(a, 10), dp(a, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 58));
        lp.bottomMargin = dp(a, 3);
        item.setLayoutParams(lp);
        item.setBackground(shape(a, 0x0FFFFFFF, 0x00FFFFFF, 12));
        TextView title = new TextView(a);
        title.setText(label); title.setTextColor(Color.WHITE); title.setTextSize(16);
        item.addView(title);
        TextView detail = new TextView(a);
        detail.setText(subtitle); detail.setTextColor(0xFFAEAAB4); detail.setTextSize(11);
        item.addView(detail);
        item.setFocusable(true); item.setClickable(true);
        item.setOnClickListener(v -> { pop.dismiss(); action.run(); });
        item.setOnFocusChangeListener((v, selected) -> {
            item.setBackground(shape(a, selected ? 0xFFEDEDEF : 0x0FFFFFFF,
                    selected ? 0xFFFFFFFF : 0x00FFFFFF, 12));
            title.setTextColor(selected ? 0xFF191921 : Color.WHITE);
            detail.setTextColor(selected ? 0xFF4C4850 : 0xFFAEAAB4);
        });
        panel.addView(item);
    }
}
