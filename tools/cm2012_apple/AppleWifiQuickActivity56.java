package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** One Wi-Fi switch, automatic nearby hotspot list. Works on CM201-2 Android 9. */
public final class AppleWifiQuickActivity56 extends Activity {
    private static final int LOCATION_REQUEST = 5661;
    private WifiManager wifi;
    private AppleQuickUi56.Row toggle;
    private TextView status;
    private LinearLayout results;
    private boolean receiverRegistered;
    private boolean scanStarted;
    private boolean locationPermissionAsked;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                scanStarted = false;
                update();
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                drawResults();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        LinearLayout page = AppleQuickUi56.page(this, "无线网络 · Wi-Fi");
        toggle = AppleQuickUi56.row(this, page, "Wi-Fi", "正在读取开关状态…");
        toggle.onClick(this::toggleWifi);
        toggle.onLongClick(() -> { scanStarted = false; update(); });
        status = AppleQuickUi56.caption(this, page, "打开 Wi-Fi 后自动搜索附近热点");
        AppleQuickUi56.section(this, page, "附近的无线网络");
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        toggle.root.requestFocus();
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        receiverRegistered = true;
        update();
    }
    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        super.onStop();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationPermissionAsked = false;
                update();
            } else status.setText("没有定位权限，无法读取附近热点。请在系统应用权限中允许后重开此页。");
        }
    }
    private void toggleWifi() {
        if (wifi == null) return;
        boolean wantOn;
        try { wantOn = !wifi.isWifiEnabled(); }
        catch (SecurityException ex) { status.setText("没有读取 Wi-Fi 状态的权限"); return; }
        if (Build.VERSION.SDK_INT >= 29) {
            openSystemWifi(); // Android 10+ prohibits ordinary apps changing this switch.
            return;
        }
        try {
            if (!wifi.setWifiEnabled(wantOn)) { openSystemWifi(); return; }
            scanStarted = false;
            status.setText(wantOn ? "正在开启 Wi-Fi…" : "正在关闭 Wi-Fi…");
            update();
        } catch (SecurityException ex) { openSystemWifi(); }
    }
    private void update() {
        results.removeAllViews();
        if (wifi == null) {
            toggle.description.setText("此设备没有 Wi-Fi 服务");
            status.setText("无法管理无线网络");
            return;
        }
        boolean enabled;
        try { enabled = wifi.isWifiEnabled(); }
        catch (SecurityException ex) { status.setText("系统拒绝读取无线网络"); return; }
        toggle.title.setText(enabled ? "Wi-Fi  ·  已开启" : "Wi-Fi  ·  已关闭");
        toggle.description.setText(enabled ? "按确定关闭 · 长按确定重新扫描" : "按确定开启并自动扫描");
        if (!enabled) { status.setText("Wi-Fi 已关闭。开启后会自动显示附近热点。"); scanStarted = false; return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.setText("需要定位权限才能搜索热点，请在授权提示中允许。");
            if (!locationPermissionAsked) {
                locationPermissionAsked = true;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            }
            return;
        }
        if (!isLocationEnabled()) {
            status.setText("Android 9 扫描热点需要开启系统定位服务。请先到系统定位设置开启。");
            AppleQuickUi56.Row loc = AppleQuickUi56.row(this, results, "打开系统定位设置", "只修改定位开关，不修改网络地址");
            loc.onClick(() -> { try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                catch (Exception ignored) { Toast.makeText(this, "固件未开放定位页面", Toast.LENGTH_SHORT).show(); } });
            return;
        }
        if (!scanStarted) {
            scanStarted = true;
            try {
                if (wifi.startScan()) status.setText("正在搜索附近热点…");
                else status.setText("扫描受系统限流，以下可能是上次的搜索结果；长按开关重试。");
            } catch (SecurityException ex) { status.setText("没有扫描权限：" + ex.getMessage()); }
        }
        drawResults();
    }
    private boolean isLocationEnabled() {
        try { return Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 0) != 0; }
        catch (Exception ex) { return true; }
    }
    private void drawResults() {
        if (wifi == null || !wifi.isWifiEnabled()) return;
        results.removeAllViews();
        List<ScanResult> found;
        try { found = wifi.getScanResults(); }
        catch (SecurityException ex) { status.setText("Wi-Fi 热点读取被系统拒绝"); return; }
        if (found == null) found = new ArrayList<>();
        Collections.sort(found, (a, b) -> Integer.compare(b.level, a.level));
        HashSet<String> seen = new HashSet<>();
        String active = "";
        try { WifiInfo info = wifi.getConnectionInfo(); if (info != null) active = info.getSSID().replace("\"", ""); }
        catch (Exception ignored) { }
        int count = 0;
        for (ScanResult sr : found) {
            if (sr.SSID == null || sr.SSID.trim().isEmpty() || !seen.add(sr.SSID)) continue;
            String capabilities = sr.capabilities == null ? "" : sr.capabilities;
            String lock = capabilities.contains("PSK") || capabilities.contains("WEP") ||
                    capabilities.contains("SAE") || capabilities.contains("EAP") ? "需密码" : "开放";
            boolean connected = sr.SSID.equals(active);
            AppleQuickUi56.Row row = AppleQuickUi56.row(this, results, sr.SSID + (connected ? "  ✓" : ""),
                    (connected ? "当前连接 · " : "") + WifiManager.calculateSignalLevel(sr.level, 5) + " 格信号 · " + lock);
            row.onClick(() -> connect(sr));
            count++;
        }
        if (count == 0) {
            AppleQuickUi56.caption(this, results, "还没有扫描到热点。请检查位置权限或稍后长按 Wi-Fi 开关重扫。");
        } else {
            status.setText("发现 " + count + " 个热点。方向键选择，确定键连接；长按开关刷新。");
        }
    }
    private void connect(ScanResult sr) {
        if (Build.VERSION.SDK_INT >= 29) { openSystemWifi(); return; }
        final String caps = sr.capabilities == null ? "" : sr.capabilities;
        if (caps.contains("EAP") || caps.contains("SAE")) {
            new AlertDialog.Builder(this).setTitle(sr.SSID)
                    .setMessage("该热点使用企业认证或 WPA3，请使用系统无线网络设置连接。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("打开系统设置", (d, w) -> openSystemWifi()).show();
            return;
        }
        boolean protectedNetwork = caps.contains("PSK") || caps.contains("WEP");
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Wi-Fi 密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog.Builder prompt = new AlertDialog.Builder(this).setTitle("连接 " + sr.SSID)
                .setMessage(protectedNetwork ? "请输入热点密码后连接" : "连接到此开放热点？")
                .setNegativeButton("取消", null)
                .setPositiveButton("连接", (d, which) -> connectLegacy(sr, protectedNetwork ? password.getText().toString() : ""));
        if (protectedNetwork) prompt.setView(password);
        prompt.show();
    }
    @SuppressWarnings("deprecation")
    private void connectLegacy(ScanResult sr, String password) {
        try {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = "\"" + sr.SSID.replace("\"", "\\\"") + "\"";
            String caps = sr.capabilities == null ? "" : sr.capabilities;
            if (caps.contains("WEP")) {
                c.wepKeys[0] = "\"" + password + "\"";
                c.wepTxKeyIndex = 0;
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                c.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            } else if (caps.contains("PSK")) {
                if (password.length() < 8) { Toast.makeText(this, "密码至少需要 8 位", Toast.LENGTH_LONG).show(); return; }
                c.preSharedKey = "\"" + password.replace("\"", "\\\"") + "\"";
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            } else {
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }
            int id = wifi.addNetwork(c);
            if (id < 0 || !wifi.enableNetwork(id, true)) {
                Toast.makeText(this, "固件未允许直接连接，请到系统无线设置连接", Toast.LENGTH_LONG).show(); return;
            }
            wifi.reconnect();
            status.setText("正在连接 " + sr.SSID + "…");
        } catch (Exception ex) {
            Toast.makeText(this, "连接失败，请使用原厂无线设置", Toast.LENGTH_LONG).show();
        }
    }
    private void openSystemWifi() {
        try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, "系统未开放无线设置", Toast.LENGTH_LONG).show(); }
    }
}
