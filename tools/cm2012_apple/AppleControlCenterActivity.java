package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.graphics.drawable.Drawable;
import android.content.DialogInterface;
import java.util.HashSet;
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
        item("应用库 · 首页增删", "在桌面底部打开全部应用库，确定键添加；长按卡片可管理首页", () -> {
            toast("返回桌面后使用底部的「全部应用库」，或长按首页应用卡片");
            startHome();
        });
        item("桌面设置 · 布局与排序", "返回首页后长按右上角设置键，可调整 Dock 和应用顺序", () -> {
            toast("返回首页，长按设置键：桌面设置 / 应用排序 / Dock");
            startHome();
        });
        item("系统应用管理", "优先打开烽火应用管理；不支持时进入内置应用列表", this::openVendorAppManager);

        section("网络与连接");
        item("当前网络", ethernetDetails(), () -> new AlertDialog.Builder(this)
                .setTitle("网络信息").setMessage(ethernetDetails())
                .setPositiveButton("确定", null).show());
        item("Wi-Fi 无线网络", "已在 CM201-2 实测可用：直接打开烽火无线网络设置", () -> openVendorOr("fiberhome.intent.action.WIRELESS", this::showWifiPanel));
        item("无线网络备用入口", "备用 WIFI 设置入口，失败时使用内置热点管理", () -> openVendorOr("fiberhome.intent.action.WIFI", this::showWifiPanel));
        item("内置 Wi-Fi 管理", "直接使用 Android 9 WifiManager 扫描、连接、切换", this::showWifiPanel);
        item("有线 / 综合网络设置", "烽火原厂 Ethernet / Network 设置页", () -> openVendorOr("fiberhome.intent.action.ETHERNET", () -> openVendorOr("fiberhome.intent.action.NETWORK", this::openVendorSettings)));
        item("蓝牙设备", "查看配对设备及遥控器", () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS));

        section("天气与屏保");
        item("中文天气", "城市、温度、预报与空气质量；需要合并天气页面源码", () -> openOptional("com.gathin.tvhome.AppleWeatherActivity", "中文天气模块仍需合入主 APK"));
        item("航拍视频屏保", "屏保来源与设置；系统屏保管理后备入口", () -> openOptionalOrSetting("com.gathin.tvhome.AppleAerialSettingsActivity", Settings.ACTION_DREAM_SETTINGS));
        item("时间与日期", "日期、时区和显示格式", () -> openSetting(Settings.ACTION_DATE_SETTINGS));

        section("声音与画面");
        item("音量 +", "提高机顶盒媒体音量", () -> adjustVolume(true));
        item("音量 −", "降低机顶盒媒体音量", () -> adjustVolume(false));
        item("音频设置", "音频输出与系统声音选项", () -> openSetting(Settings.ACTION_SOUND_SETTINGS));
        item("显示设置", "烽火 DISPLAY 页面；HDMI 背光通常不可由盒子调节", () -> openVendorOr("fiberhome.intent.action.DISPLAY", () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS)));
        item("分辨率与 HDMI 输出", "烽火 RESOLUTION 页面，修改之前确认电视支持", () -> openVendorOr("fiberhome.intent.action.RESOLUTION", () -> toast("本固件没有可用的分辨率设置页")));

        section("系统与高级设置");
        item("设备存储", "烽火 SPACEMANAGER 页面", () -> openVendorOr("fiberhome.intent.action.SPACEMANAGER", () -> openSetting(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        item("输入法与语言", "检查输入法、语言和中文输入", () -> openSetting(Settings.ACTION_INPUT_METHOD_SETTINGS));
        item("权限与无障碍", "系统无障碍设置", () -> openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        item("安全与隐私", "设备安全、应用权限入口", () -> openSetting(Settings.ACTION_SECURITY_SETTINGS));
        item("开发者选项", "烽火 ADVANCED 页面，或标准开发者选项", () -> openVendorOr("fiberhome.intent.action.ADVANCED", () -> openSetting(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        item("关于本机", "烽火 INFORMATION 页面或 Android 设备信息", () -> openVendorOr("fiberhome.intent.action.INFORMATION", () -> openSetting(Settings.ACTION_DEVICE_INFO_SETTINGS)));
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
            if (!openVendorSettingsInternal()) {
                toast("此标准设置页不存在，固件未开放对应入口");
            }
        }
    }


    /**
     * These actions are registered by the CM201-2's actual FiberHomeSettings APK
     * (package dump supplied by the device owner); do not invent activity names.
     */
    private void openVendorOr(String action, Runnable fallback) {
        if (!startFiberhome(action)) fallback.run();
    }

    private boolean startFiberhome(String action) {
        try {
            Intent i = new Intent(action);
            i.setClassName("com.fiberhome.settings", "com.fiberhome.settings.MainActivity");
            startActivity(i);
            return true;
        } catch (Exception ex) {
            android.util.Log.w("CM2012Control", "FiberHome " + action + " unavailable", ex);
            return false;
        }
    }

    private void openVendorAppManager() {
        // The vendor app-manager fragment is internal, not a registered exported Activity.
        // Open the settings landing page first; if it isn't reachable, show native list.
        if (!openVendorSettingsInternal()) showInstalledApps();
        else toast("已打开烽火设置；若没有应用管理入口，可返回控制中心使用「全部应用」管理和卸载");
    }

    // Some FiberHome ROMs do not export standard android.settings.* activities.
    // First try their public launcher, without guessing private activity names.
    private void openVendorSettings() {
        if (!openVendorSettingsInternal()) {
            toast("烽火设置未提供外部启动入口，可继续使用本控制中心内置功能");
        }
    }

    private boolean openVendorSettingsInternal() {
        // Installed FiberHome APK declares android.settings.SETTINGS / FHSETTINGS
        // on AuthActivity; getLaunchIntentForPackage() alone may be null.
        try {
            Intent intent = new Intent("fiberhome.intent.action.FHSETTINGS");
            intent.setClassName("com.fiberhome.settings", "com.fiberhome.settings.AuthActivity");
            startActivity(intent);
            return true;
        } catch (Exception ex) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.fiberhome.settings");
                if (launch != null) { startActivity(launch); return true; }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private WifiManager wifiManager;
    private static final int WIFI_LOCATION_REQUEST = 2318;

    private void showWifiPanel() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            new AlertDialog.Builder(this).setTitle("无线网络不可用")
                    .setMessage("系统未提供 Wi-Fi 服务。此功能无法由桌面自行创建。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_LOCATION_REQUEST);
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(d(12), d(6), d(12), d(6));
        TextView status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.WHITE);
        status.setPadding(d(6), d(6), d(6), d(12));
        panel.addView(status);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("无线网络 · Apple TV")
                .setView(scroll)
                .setPositiveButton("刷新热点", null)
                .setNeutralButton("Wi-Fi 开关", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v ->
                    refreshWifiList(status, rows, true));
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    boolean result = wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
                    if (!result) toast("此固件禁止桌面操作 Wi-Fi 开关，请使用原厂设置");
                    refreshWifiList(status, rows, false);
                } catch (Exception e) { toast("切换 Wi-Fi 失败：" + e.getClass().getSimpleName()); }
            });
            refreshWifiList(status, rows, true);
        });
        dialog.show();
        // Fixed dimensions prevent the hotspot list from covering the TV screen.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(d(720), d(550));
        }
    }

    private void refreshWifiList(TextView status, LinearLayout rows, boolean forceScan) {
        rows.removeAllViews();
        boolean enabled = false;
        try { enabled = wifiManager.isWifiEnabled(); } catch (Exception ignored) { }
        if (!enabled) {
            status.setText("Wi-Fi 目前关闭。按下方「Wi-Fi 开关」开启；有线网络仍保持连接。");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 19 && Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.LOCATION_MODE, 0) == 0) {
                status.setText("Android 9 扫描热点需要系统定位服务开启。固件如隐藏定位设置，" +
                        "可通过 ADB 开启后再刷新。本页面不会擅自修改定位设置。");
                return;
            }
        } catch (Exception ignored) { }
        try {
            boolean scanRequested = !forceScan || wifiManager.startScan();
            List<ScanResult> found = wifiManager.getScanResults();
            if (found == null) found = new ArrayList<>();
            Collections.sort(found, (a,b) -> Integer.compare(b.level, a.level));
            HashSet<String> seen = new HashSet<>();
            int total = 0;
            for (ScanResult sr : found) {
                if (sr.SSID == null || sr.SSID.trim().isEmpty() || !seen.add(sr.SSID)) continue;
                total++;
                final ScanResult network = sr;
                final String security = sr.capabilities.contains("EAP") ? "企业网络" :
                        sr.capabilities.contains("WEP") ? "WEP" :
                        sr.capabilities.contains("PSK") || sr.capabilities.contains("SAE") ? "需要密码" : "开放网络";
                TextView tile = new TextView(this);
                tile.setText(sr.SSID + "    " + strengthName(sr.level) + "  ·  " + security);
                tile.setTextColor(Color.WHITE);
                tile.setTextSize(18);
                tile.setPadding(d(18), d(13), d(16), d(13));
                tile.setFocusable(true);
                tile.setClickable(true);
                tile.setBackground(bg(0xFF233652, 0xFF3B5276, 12));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = d(6);
                rows.addView(tile, lp);
                tile.setOnClickListener(v -> confirmWifiConnection(network));
                tile.setOnFocusChangeListener((v, focused) ->
                        tile.setBackground(bg(focused ? 0xFF326BB0 : 0xFF233652,
                                focused ? 0xFFB3E6FF : 0xFF3B5276, 12)));
            }
            status.setText("已发现 " + total + " 个热点" +
                    (scanRequested ? "（可选中连接）" : "（刷新受限，显示缓存结果）") +
                    "。如果列表为空，可过几秒刷新；扫描限频属于 Android 9 正常行为。");
        } catch (SecurityException e) {
            status.setText("没有获得热点扫描权限。请确认已允许此应用使用定位权限，且系统定位已开启。");
        } catch (Exception e) {
            status.setText("读取热点失败：" + e.getClass().getSimpleName() + "。可尝试烽火原厂设置。");
        }
    }

    private String strengthName(int level) {
        if (level > -55) return "信号极好";
        if (level > -67) return "信号较好";
        if (level > -80) return "信号一般";
        return "信号较弱";
    }

    private void confirmWifiConnection(ScanResult sr) {
        if (sr.capabilities.contains("EAP") || sr.capabilities.contains("WEP")) {
            new AlertDialog.Builder(this).setTitle(sr.SSID)
                    .setMessage("该网络使用企业认证或旧式 WEP。请从厂商网络设置中配置认证方式。")
                    .setPositiveButton("确定", null).show();
            return;
        }
        boolean protectedNetwork = sr.capabilities.contains("PSK") || sr.capabilities.contains("SAE");
        if (sr.capabilities.contains("SAE") && !sr.capabilities.contains("PSK")) {
            toast("此网络仅支持 WPA3，Android 9 盒子可能无法连接");
            return;
        }
        if (!protectedNetwork) {
            new AlertDialog.Builder(this).setTitle("连接 " + sr.SSID + "？")
                    .setMessage("此热点没有密码保护。")
                    .setPositiveButton("连接", (d, w) -> connectWifi(sr.SSID, null))
                    .setNegativeButton("取消", null).show();
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入 Wi-Fi 密码");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(d(25), d(5), d(25), d(8));
        frame.addView(input);
        new AlertDialog.Builder(this).setTitle("连接 " + sr.SSID)
                .setView(frame)
                .setPositiveButton("连接", (d, w) -> {
                    String pass = input.getText().toString();
                    if (pass.length() < 8 || pass.length() > 63) {
                        toast("WPA2 密码通常为 8–63 个字符，请检查密码后重新选择热点");
                    } else connectWifi(sr.SSID, pass);
                }).setNegativeButton("取消", null).show();
    }

    @SuppressWarnings("deprecation")
    private void connectWifi(String ssid, String password) {
        try {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ssid.replace("\"", "\\\"") + "\"";
            if (password == null) {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                conf.preSharedKey = "\"" + password.replace("\"", "\\\"") + "\"";
            }
            int id = wifiManager.addNetwork(conf);
            if (id < 0) {
                List<WifiConfiguration> saved = wifiManager.getConfiguredNetworks();
                if (saved != null) {
                    for (WifiConfiguration item : saved) {
                        if (conf.SSID.equals(item.SSID)) { id = item.networkId; break; }
                    }
                }
            }
            if (id < 0 || !wifiManager.enableNetwork(id, true)) {
                toast("系统拒绝保存/连接此热点。可能需要使用原厂网络设置。");
                return;
            }
            wifiManager.reconnect();
            toast("已向系统提交连接请求，请稍后检查 Wi-Fi 状态；有线网络不受影响");
        } catch (Exception e) { toast("连接失败：" + e.getClass().getSimpleName()); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WIFI_LOCATION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                showWifiPanel();
            else toast("需要定位权限才能在 Android 9 上扫描附近 Wi-Fi。请在应用权限中允许。");
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
                ? new String[]{"打开应用", "应用信息", "返回应用列表"}
                : new String[]{"打开应用", "应用信息", "卸载应用（需要系统确认）", "返回应用列表"};
        new AlertDialog.Builder(this).setTitle(pkg).setItems(options, (dlg, which) -> {
            if (which == 0) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) { toast("此应用没有公开的启动页面"); return; }
                try { startActivity(launch); } catch (Exception e) { toast("打开应用失败：" + e.getClass().getSimpleName()); }
            } else if (which == 1) {
                openApplicationDetails(pkg);
            } else if (which == 2 && !system) {
                if (pkg.equals(getPackageName())) {
                    toast("不能从正在使用的桌面卸载自身，请先切换默认桌面");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("卸载第三方应用")
                        .setMessage("准备卸载 " + pkg + "\n此操作可能同时删除应用数据。下一步由 Android 系统再次确认。")
                        .setPositiveButton("进入系统卸载页面", (x, y) -> requestSafeUninstall(pkg))
                        .setNegativeButton("取消", null).show();
            } else showInstalledApps();
        }).setNegativeButton("关闭", null).show();
    }

    private void openApplicationDetails(String pkg) {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + pkg));
        try { startActivity(details); }
        catch (Exception e) {
            toast("原厂未公开标准应用信息页面；改用内置应用列表");
            showInstalledApps();
        }
    }

    private void requestSafeUninstall(String pkg) {
        Uri data = Uri.parse("package:" + pkg);
        // Explicit activity discovered from the actual Android 9 PackageInstaller APK.
        // Always uses the SYSTEM CONFIRMATION UI. No privileged silent removal.
        try {
            Intent uninstaller = new Intent(Intent.ACTION_DELETE, data);
            uninstaller.setClassName("com.android.packageinstaller",
                    "com.android.packageinstaller.UninstallerActivity");
            startActivity(uninstaller);
            return;
        } catch (Exception first) {
            android.util.Log.w("CM2012Control", "Explicit uninstaller failed", first);
        }
        for (String action : new String[]{Intent.ACTION_UNINSTALL_PACKAGE, Intent.ACTION_DELETE}) {
            try {
                Intent i = new Intent(action, data);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                startActivity(i);
                return;
            } catch (Exception ex) {
                android.util.Log.w("CM2012Control", "Uninstaller action failed " + action, ex);
            }
        }
        new AlertDialog.Builder(this).setTitle("卸载页面未能启动")
                .setMessage("已尝试当前固件的 PackageInstaller 和标准卸载指令。\n" +
                        "请检查软件包权限和系统日志，勿使用静默卸载系统组件。")
                .setPositiveButton("知道了", null).show();
    }

}
