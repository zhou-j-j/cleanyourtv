package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One Bluetooth switch + paired and discovered device list for TV remote. */
public final class AppleBluetoothQuickActivity56 extends Activity {
    private static final int PERMISSION_REQUEST = 5671;
    private static final int REQUEST_ENABLE = 5672;
    private BluetoothAdapter adapter;
    private AppleQuickUi56.Row toggle;
    private TextView status;
    private LinearLayout rows;
    private boolean receiverRegistered;
    private boolean scanStarted;
    private boolean permissionRequested;
    private final Map<String, BluetoothDevice> nearby = new HashMap<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                scanStarted = false;
                if (adapter != null && adapter.isEnabled()) nearby.clear();
                update();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && allowedConnect()) {
                    try { nearby.put(device.getAddress(), device); renderDevices(); }
                    catch (SecurityException ignored) { }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (adapter != null && adapter.isEnabled()) {
                    status.setText("搜索完成。长按蓝牙开关可重新搜索附近设备。");
                    renderDevices();
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                renderDevices();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = BluetoothAdapter.getDefaultAdapter();
        LinearLayout page = AppleQuickUi56.page(this, "蓝牙 · Bluetooth");
        toggle = AppleQuickUi56.row(this, page, "蓝牙", "正在读取开关状态…");
        toggle.onClick(this::toggleBluetooth);
        toggle.onLongClick(() -> { scanStarted = false; scan(); });
        status = AppleQuickUi56.caption(this, page, "打开蓝牙后自动搜索周围设备");
        AppleQuickUi56.section(this, page, "已配对和附近的设备");
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(rows);
        toggle.root.requestFocus();
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        receiverRegistered = true;
        update();
    }
    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        try { if (adapter != null && allowedScan() && adapter.isDiscovering()) adapter.cancelDiscovery(); }
        catch (SecurityException ignored) { }
        scanStarted = false;
        super.onStop();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(requestCode, permissions, result);
        if (requestCode == PERMISSION_REQUEST) {
            if (allowedScan() && allowedConnect()) { permissionRequested = false; update(); }
            else status.setText("需要附近设备及位置权限才能搜索；可在系统应用权限中授予后重新打开。");
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_ENABLE) { scanStarted = false; update(); }
    }
    private boolean allowedConnect() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }
    private boolean allowedScan() {
        if (Build.VERSION.SDK_INT >= 31)
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean ensurePermissions() {
        if (allowedConnect() && allowedScan()) return true;
        if (!permissionRequested) {
            permissionRequested = true;
            if (Build.VERSION.SDK_INT >= 31)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST);
        }
        return false;
    }
    private void toggleBluetooth() {
        if (adapter == null) return;
        // On Android 9 the radio switch does not need location; ask only when
        // scanning. On Android 12+ the BLUETOOTH_CONNECT permission is required.
        if (Build.VERSION.SDK_INT >= 31 && !allowedConnect() && !ensurePermissions()) return;
        try {
            if (adapter.isEnabled()) {
                // Never disconnect a Bluetooth remote without an explicit warning.
                new AlertDialog.Builder(this).setTitle("关闭蓝牙？")
                        .setMessage("关闭后蓝牙遥控器、耳机等设备会断开。请先准备红外遥控器或 USB 键盘。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("关闭蓝牙", (d, w) -> changeBluetooth(false)).show();
            } else changeBluetooth(true);
        } catch (SecurityException ex) { openSystemBluetooth(); }
    }
    private void changeBluetooth(boolean turnOn) {
        try {
            if (!turnOn) {
                if (Build.VERSION.SDK_INT >= 33 || !adapter.disable()) openSystemBluetooth();
                else { nearby.clear(); status.setText("正在关闭蓝牙…"); update(); }
            } else {
                if (Build.VERSION.SDK_INT >= 33 || !adapter.enable()) {
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE);
                } else {
                    status.setText("正在开启蓝牙…"); scanStarted = false; update();
                }
            }
        } catch (SecurityException ex) { openSystemBluetooth(); }
        catch (Exception ex) { status.setText("蓝牙开关不可用：" + ex.getMessage()); }
    }
    private void update() {
        if (adapter == null) { toggle.description.setText("此设备没有蓝牙适配器"); status.setText("蓝牙不可用"); return; }
        if (Build.VERSION.SDK_INT >= 31 && !allowedConnect() && !ensurePermissions()) {
            status.setText("需要附近设备权限才能显示蓝牙状态…"); return;
        }
        boolean on;
        try { on = adapter.isEnabled(); }
        catch (SecurityException ex) { status.setText("无法读取蓝牙状态"); return; }
        toggle.title.setText(on ? "蓝牙  ·  已开启" : "蓝牙  ·  已关闭");
        toggle.description.setText(on ? "按确定关闭 · 长按确定重新搜索" : "按确定开启并自动搜索");
        if (!on) {
            nearby.clear();
            rows.removeAllViews();
            scanStarted = false;
            status.setText("蓝牙已关闭。开启后自动列出附近设备。");
            return;
        }
        if (!ensurePermissions()) {
            status.setText("需要搜索权限才能列出附近蓝牙设备…");
            return;
        }
        renderDevices();
        scan();
    }
    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= 31) return true; // Nearby Devices permission replaces location.
        try { return Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 0) != 0; }
        catch (Exception ignored) { return true; }
    }
    private void scan() {
        if (adapter == null || !allowedScan() || !allowedConnect()) return;
        if (!isLocationEnabled()) {
            status.setText("Android 9 搜索蓝牙设备需要启用系统定位服务；已配对设备仍显示在下方。");
            return;
        }
        try {
            if (!adapter.isEnabled() || scanStarted) return;
            scanStarted = true;
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            if (adapter.startDiscovery()) status.setText("正在搜索附近蓝牙设备…");
            else status.setText("搜索未能启动，长按蓝牙开关重试。已配对设备仍显示在下方。");
        } catch (SecurityException ex) { status.setText("系统拒绝蓝牙搜索权限"); }
    }
    private void renderDevices() {
        if (rows == null || adapter == null || !allowedConnect()) return;
        rows.removeAllViews();
        List<BluetoothDevice> paired = new ArrayList<>();
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) paired.addAll(bonded);
        } catch (SecurityException ex) { status.setText("无法读取已配对设备"); }
        Collections.sort(paired, (a, b) -> safeName(a).compareToIgnoreCase(safeName(b)));
        for (BluetoothDevice device : paired) addDevice(device, true);
        List<BluetoothDevice> discovered = new ArrayList<>(nearby.values());
        Collections.sort(discovered, (a, b) -> safeName(a).compareToIgnoreCase(safeName(b)));
        for (BluetoothDevice device : discovered) {
            boolean bonded = false;
            try { bonded = device.getBondState() == BluetoothDevice.BOND_BONDED; }
            catch (SecurityException ignored) { }
            if (!bonded) addDevice(device, false);
        }
        if (paired.isEmpty() && discovered.isEmpty())
            AppleQuickUi56.caption(this, rows, "尚未发现设备。请让耳机、音箱或遥控器进入配对模式。");
    }
    private String safeName(BluetoothDevice device) {
        try { String n = device.getName(); if (n != null && !n.trim().isEmpty()) return n; }
        catch (SecurityException ignored) { }
        return "未命名设备";
    }
    private void addDevice(BluetoothDevice device, boolean paired) {
        String addr;
        try { addr = device.getAddress(); }
        catch (SecurityException ex) { addr = "未知地址"; }
        AppleQuickUi56.Row row = AppleQuickUi56.row(this, rows, safeName(device),
                (paired ? "已配对" : "附近设备 · 按确定配对") + " · " + addr);
        row.onClick(() -> {
            if (paired) {
                new AlertDialog.Builder(this).setTitle(safeName(device))
                        .setMessage("已配对。音频设备或遥控器的实际连接由系统蓝牙服务管理。")
                        .setNegativeButton("返回", null)
                        .setPositiveButton("系统蓝牙设置", (d, w) -> openSystemBluetooth()).show();
            } else {
                new AlertDialog.Builder(this).setTitle("配对 " + safeName(device) + "？")
                        .setMessage("请先让设备处于配对模式；可能还需在设备或电视上确认配对码。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("配对", (d, w) -> pair(device)).show();
            }
        });
    }
    private void pair(BluetoothDevice device) {
        try {
            if (!allowedConnect() || !allowedScan()) return;
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            if (device.createBond()) status.setText("已发起配对：" + safeName(device) + "，请完成系统配对确认。");
            else status.setText("配对请求未被系统接受，请确认设备仍处于配对模式。");
        } catch (SecurityException ex) { status.setText("系统拒绝蓝牙配对请求"); }
    }
    private void openSystemBluetooth() {
        try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, "系统未开放蓝牙设置", Toast.LENGTH_LONG).show(); }
    }
}
