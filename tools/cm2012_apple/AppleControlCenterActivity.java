package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Self-contained TV control center. No external app, root access, or system partition edits.
 * All privileged actions are either system Settings intents or Android's confirmation dialogs.
 */
public class AppleControlCenterActivity extends Activity {
    private static final int BG = Color.rgb(9, 17, 37);
    private static final int SECONDARY = Color.rgb(167, 190, 221);
    private LinearLayout content;
    private View initialFocus;

    private int d(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }

    private GradientDrawable bg(int color, int borderColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(d(radius));
        drawable.setStroke(d(1), borderColor);
        return drawable;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(d(45), d(26), d(45), d(40));
        scroll.addView(content);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText("控制中心   ·   Apple TV 风格");
        heading.setTextSize(29);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        content.addView(heading);
        TextView clock = new TextView(this);
        clock.setText(new SimpleDateFormat("yyyy年M月d日 EEEE  HH:mm", Locale.CHINA).format(new Date()));
        clock.setTextColor(SECONDARY);
        clock.setTextSize(16);
        clock.setPadding(0, d(5), 0, d(15));
        content.addView(clock);

        section("应用与桌面");
        item("全部应用 · 管理 / 卸载", "可查看所有应用；卸载需系统确认，预装应用只进入详情", this::showInstalledApps);
        item("应用库 · 首页增删", "在桌面顶部打开应用库，确定键增删；长按卡片可移出首页", () -> {
            toast("返回桌面后使用顶部的「应用库」，或长按首页应用卡片");
            startHome();
        });
        item("桌面设置 · 布局与排序", "返回首页后长按右上角设置键，可调整 Dock 和应用顺序", () -> {
            toast("返回首页，长按设置键：桌面设置 / 应用排序 / Dock");
            startHome();
        });
        item("系统应用管理", "原厂 Android 的应用列表（如固件支持）", () -> openSetting(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));

        section("网络与连接");
        item("当前网络", ethernetDetails(), () -> new AlertDialog.Builder(this)
                .setTitle("网络信息").setMessage(ethernetDetails())
                .setPositiveButton("确定", null).show());
        item("Wi-Fi 无线网络", "查看热点、连接无线网络；失败时尝试原厂网络设置", () -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        item("无线与移动网络", "系统网络详细设置", () -> openSetting(Settings.ACTION_WIRELESS_SETTINGS));
        item("蓝牙设备", "查看配对设备及遥控器", () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS));

        section("天气与屏保");
        item("中文天气", "城市、温度、预报与空气质量；需要合并天气页面源码", () -> openOptional("com.gathin.tvhome.AppleWeatherActivity", "中文天气模块仍需合入主 APK"));
        item("航拍视频屏保", "屏保来源与设置；系统屏保管理后备入口", () -> openOptionalOrSetting("com.gathin.tvhome.AppleAerialSettingsActivity", Settings.ACTION_DREAM_SETTINGS));
        item("时间与日期", "日期、时区和显示格式", () -> openSetting(Settings.ACTION_DATE_SETTINGS));

        section("声音与画面");
        item("音量 +", "提高机顶盒媒体音量", () -> adjustVolume(true));
        item("音量 −", "降低机顶盒媒体音量", () -> adjustVolume(false));
        item("音频设置", "音频输出与系统声音选项", () -> openSetting(Settings.ACTION_SOUND_SETTINGS));
        item("显示设置", "画面与显示参数（HDMI 亮度未必可以调节）", () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS));

        section("系统与高级设置");
        item("设备存储", "剩余容量及缓存", () -> openSetting(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        item("输入法与语言", "检查输入法、语言和中文输入", () -> openSetting(Settings.ACTION_INPUT_METHOD_SETTINGS));
        item("权限与无障碍", "系统无障碍设置", () -> openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        item("安全与隐私", "设备安全、应用权限入口", () -> openSetting(Settings.ACTION_SECURITY_SETTINGS));
        item("开发者选项", "显示系统开发者设置；部分固件可能隐藏", () -> openSetting(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        item("关于本机", "Android 版本、设备信息", () -> openSetting(Settings.ACTION_DEVICE_INFO_SETTINGS));
        item("原厂系统设置", "打开 Android 原生设置或原厂设置", () -> openSetting(Settings.ACTION_SETTINGS));

        if (initialFocus != null) initialFocus.requestFocus();
    }

    private void section(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(17);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(Color.rgb(109, 205, 255));
        view.setPadding(0, d(17), 0, d(7));
        content.addView(view);
    }

    private void item(String label, String subtitle, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(d(20), d(10), d(20), d(12));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = d(7);
        tile.setLayoutParams(outer);
        tile.setBackground(bg(0xAA202C48, 0x665E7799, 14));
        tile.setFocusable(true);
        tile.setClickable(true);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(19);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        tile.addView(title);
        TextView hint = new TextView(this);
        hint.setText(subtitle);
        hint.setTextSize(13);
        hint.setTextColor(SECONDARY);
        tile.addView(hint);
        tile.setOnClickListener(v -> action.run());
        tile.setOnFocusChangeListener((v, focused) -> {
            tile.setBackground(bg(focused ? 0xC52B548B : 0xAA202C48,
                    focused ? 0xFF84D9FF : 0x665E7799, 14));
            tile.animate().scaleX(focused ? 1.015f : 1f).scaleY(focused ? 1.015f : 1f)
                    .setDuration(120).start();
        });
        content.addView(tile);
        if (initialFocus == null) initialFocus = tile;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void startHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); } catch (Exception e) { toast("无法打开桌面：" + e.getMessage()); }
        finish();
    }

    private void openSetting(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); }
        catch (Exception ex) {
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                toast("本固件未提供这个设置页面，尝试打开系统设置");
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception e) { toast("原厂固件未开放相应设置入口"); }
            } else toast("原厂固件未开放系统设置入口");
        }
    }

    private void openOptional(String className, String fallback) {
        try {
            Class<?> c = Class.forName(className);
            startActivity(new Intent(this, c));
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("功能准备中")
                    .setMessage(fallback + "。当前没有额外安装包，也不会启动不存在的应用。")
                    .setPositiveButton("确定", null).show();
        }
    }

    private void openOptionalOrSetting(String className, String setting) {
        try {
            Class<?> c = Class.forName(className);
            startActivity(new Intent(this, c));
        } catch (Exception e) {
            toast("尚未合入航拍设置页，打开系统屏保设置");
            openSetting(setting);
        }
    }

    private void adjustVolume(boolean up) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) { toast("此固件无法控制媒体音量"); return; }
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
        } catch (Exception e) { toast("媒体音量无法修改：" + e.getMessage()); }
    }

    private String ethernetDetails() {
        StringBuilder result = new StringBuilder();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return "未获取到网络接口";
            while (all.hasMoreElements()) {
                NetworkInterface nic = all.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        if (result.length() > 0) result.append("\n");
                        result.append(nic.getName()).append("  ").append(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) { return "网络信息读取受限"; }
        return result.length() == 0 ? "未检测到 IPv4 网络，进入网络设置检查" : result.toString();
    }

    private void showInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = new ArrayList<>(pm.getInstalledApplications(0));
        Collections.sort(apps, new Comparator<ApplicationInfo>() {
            @Override public int compare(ApplicationInfo a, ApplicationInfo b) {
                return String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(
                        String.valueOf(pm.getApplicationLabel(b)));
            }
        });
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); ++i) {
            ApplicationInfo a = apps.get(i);
            boolean system = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            labels[i] = String.valueOf(pm.getApplicationLabel(a)) + "  " +
                    (system ? "[系统]" : "[用户]") + (!a.enabled ? " [已停用]" : "") + "\n" + a.packageName;
        }
        new AlertDialog.Builder(this).setTitle("应用管理 · " + apps.size() + " 个")
                .setItems(labels, (dialog, which) -> showAppActions(apps.get(which)))
                .setNegativeButton("返回", null).show();
    }

    private void showAppActions(ApplicationInfo app) {
        String pkg = app.packageName;
        boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        String[] options = system
                ? new String[]{"打开应用", "应用详情 / 启用与停用", "返回应用列表"}
                : new String[]{"打开应用", "应用详情", "卸载应用（系统确认）", "返回应用列表"};
        new AlertDialog.Builder(this).setTitle(pkg).setItems(options, (dlg, which) -> {
            if (which == 0) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) { toast("该应用没有可直接启动的界面"); return; }
                try { startActivity(launch); } catch (Exception e) { toast("无法启动此应用"); }
            } else if (which == 1) {
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + pkg));
                try { startActivity(details); } catch (Exception e) { openSetting(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS); }
            } else if (which == 2 && !system) {
                if (pkg.equals(getPackageName())) {
                    toast("请先切换默认桌面，再考虑卸载当前桌面");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("确认请求卸载？")
                        .setMessage("卸载 " + pkg + " 可能删除其数据。系统仍会再要求确认。")
                        .setPositiveButton("继续", (x, y) -> {
                            try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))); }
                            catch (Exception e) { toast("此固件不支持直接卸载，请在应用详情中操作"); }
                        }).setNegativeButton("取消", null).show();
            } else showInstalledApps();
        }).setNegativeButton("关闭", null).show();
    }
}
