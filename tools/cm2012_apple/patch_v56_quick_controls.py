#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CM201-2 V5.6 UX patch. Run immediately after patch_v55_icons_qr_bt.py.

 * App Library uninstall: one Android PackageInstaller confirmation, no extra dialog.
 * Top Wi-Fi and Bluetooth: dedicated switch + automatic discovery + device lists.
 * Control Center: narrower focus-safe rows, no focus scaling outside screen.
 Idempotent on an already-patched build tree. Refuses unknown source anchors.
"""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
FILES = [JAVA / s for s in ('AppLibraryDialog.java', 'MainActivity.java', 'AppleTopMenu.java',
                                'AppleTopStatus53.java', 'AppleControlCenterActivity.java')]
ASSETS = ['AppleQuickUi56.java', 'AppleWifiQuickActivity56.java', 'AppleBluetoothQuickActivity56.java']
MARK = '// CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION'


def die(msg):
    sys.exit('ERROR V5.6: ' + msg)


def replace_one(src, old, new, file):
    if new in src and old not in src:
        return src
    if src.count(old) != 1:
        die(f'{file}: expected one source anchor, got {src.count(old)}: {old[:80]!r}')
    return src.replace(old, new, 1)


def replace_method(src, signature, body, file):
    at = src.find(signature)
    if at == -1:
        die(f'{file}: missing method {signature}')
    if src.count(signature) != 1:
        die(f'{file}: duplicate method {signature}')
    start = src.find('{', at + len(signature))
    if start == -1: die('missing { for ' + signature)
    depth = 0
    # The two target methods have no braces inside string literals. This
    # lightweight guard deliberately aborts if source structure changes.
    for i in range(start, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:at] + body + src[i + 1:]
    die('unterminated ' + signature)


for p in FILES + [MANIFEST] + [HERE / f for f in ASSETS]:
    if not p.is_file(): die(f'missing input: {p}')
text = {p: p.read_text(encoding='utf-8') for p in FILES + [MANIFEST]}
lib, main, top, status, control = FILES

# 1. One system dialog after choosing "卸载应用" from the existing long-OK menu.
# The system (not the launcher) performs removal and shows its own Cancel/Uninstall.
s = text[lib]
if MARK not in s:
    start = s.find('        // CM201-2: uninstall a third-party app from the existing App Library hold-OK menu.')
    end = s.find('        // CM2012_V522_APP_DETAILS:', start)
    if start < 0 or end < 0 or s.count('addAction(rows, R.string.cm2012_uninstall') != 1:
        die('AppLibraryDialog uninstall menu does not match known V5.5 source')
    segment = s[start:end]
    if 'new android.app.AlertDialog.Builder(activity)' not in segment:
        die('AppLibraryDialog expected old two-step uninstall')
    s = s[:start] + '''        // CM201-2: only user-installed apps, and never uninstall the active HOME.
        // CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION
        android.content.pm.ApplicationInfo ai = app.activityInfo.applicationInfo;
        boolean preinstalled = ai != null && (ai.flags &
                (android.content.pm.ApplicationInfo.FLAG_SYSTEM |
                 android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        if (!preinstalled && !pkg.equals(activity.getPackageName())) {
            addAction(rows, R.string.cm2012_uninstall, () -> {
                dismissActions();
                android.net.Uri target = android.net.Uri.parse("package:" + pkg);
                try {
                    // Directly opens Android's real Cancel/Uninstall dialog.
                    Intent i = new Intent(Intent.ACTION_DELETE, target);
                    i.setClassName("com.android.packageinstaller",
                            "com.android.packageinstaller.UninstallerActivity");
                    activity.startActivity(i);
                } catch (Exception first) {
                    try { activity.startActivity(new Intent(Intent.ACTION_DELETE, target)); }
                    catch (Exception second) {
                        Toast.makeText(activity, "系统卸载确认窗口不可用", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
''' + s[end:]
text[lib] = s

# 2. Both the fallback original pill and the actual Apple top pill open local Wi-Fi.
s = text[main]
s = replace_one(s,
    'btnWifi.setOnClickListener(v -> startActivity(new Intent(this, AppleControlCenterActivity.class)));',
    'btnWifi.setOnClickListener(v -> startActivity(new Intent(this, AppleWifiQuickActivity56.class)));',
    'MainActivity')
text[main] = s

wifi_method = '''private static void openWifi(Activity a) {
        a.startActivity(new Intent(a, AppleWifiQuickActivity56.class));
    }'''
s = text[top]
if wifi_method not in s:
    s = replace_method(s, 'private static void openWifi(Activity a)', wifi_method, 'AppleTopMenu')
text[top] = s

# The existing network status icon also opens the dedicated Wi-Fi page. The
# Ethernet link is still shown under Control Center > current network.
s = text[status]
s = replace_one(s, 'bluetooth.setOnClickListener(v -> AppleBluetoothSettings55.open(activity));',
    'bluetooth.setOnClickListener(v -> activity.startActivity(new Intent(activity, AppleBluetoothQuickActivity56.class)));',
    'AppleTopStatus53')
old_draw = 'private void drawNetwork(TextView target)'
new_draw = '''private void drawNetwork(TextView target) {
            // The top icon now means Wi-Fi only, even when eth0 carries the default route.
            WifiManager wifi = (WifiManager) activity.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            int type = NetworkStatusDrawable.DISCONNECTED;
            int level = 0;
            if (wifi != null) {
                try {
                    if (wifi.isWifiEnabled()) {
                        type = NetworkStatusDrawable.WIFI;
                        WifiInfo info = wifi.getConnectionInfo();
                        if (info != null && info.getRssi() > -127 && info.getRssi() < 0)
                            level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                    }
                } catch (SecurityException ignored) { }
            }
            target.setText("");
            target.setGravity(Gravity.CENTER);
            target.setCompoundDrawablePadding(0);
            target.setCompoundDrawableTintList((ColorStateList) null);
            target.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    new NetworkStatusDrawable(activity.getResources().getDisplayMetrics().density,
                            type, level, false), null, null, null);
            target.setContentDescription(type == NetworkStatusDrawable.WIFI
                    ? "无线网络已开启，按确定打开 Wi-Fi 开关和热点列表"
                    : "无线网络已关闭，按确定打开 Wi-Fi 开关和热点列表");
        }'''
if 'The top icon now means Wi-Fi only' not in s:
    s = replace_method(s, old_draw, new_draw, 'AppleTopStatus53')
text[status] = s

# 3. Keep the advanced/vendor Wi-Fi entries intact, but make regular menu links
# match the top-right shortcut. Bluetooth system settings remains a fallback.
s = text[control]
s = replace_one(s,
    'item("Wi-Fi 无线网络", "已在 CM201-2 实测可用：直接打开烽火无线网络设置", () -> openVendorOr("fiberhome.intent.action.WIRELESS", this::showWifiPanel));',
    'item("Wi-Fi 无线网络", "开关及自动搜索的无线热点列表", () -> startActivity(new Intent(this, AppleWifiQuickActivity56.class)));',
    'AppleControlCenterActivity')
s = replace_one(s,
    'item("内置 Wi-Fi 管理", "直接使用 Android 9 WifiManager 扫描、连接、切换", this::showWifiPanel);',
    'item("内置 Wi-Fi 管理", "无线开关、自动扫描和连接", () -> startActivity(new Intent(this, AppleWifiQuickActivity56.class)));',
    'AppleControlCenterActivity')
s = replace_one(s,
    'item("蓝牙设备", "查看配对设备及遥控器", () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS));',
    'item("蓝牙设备", "蓝牙开关及自动搜索的附近设备", () -> startActivity(new Intent(this, AppleBluetoothQuickActivity56.class)));',
    'AppleControlCenterActivity')
s = replace_one(s,
    'content.setPadding(d(45), d(26), d(45), d(40));',
    '''// 10% side inset prevents full-screen focus rows from clipping on TVs.
        int inset = Math.min(d(112), Math.max(d(46),
                getResources().getDisplayMetrics().widthPixels / 10));
        content.setPadding(inset, d(26), inset, d(40));''',
    'AppleControlCenterActivity')
s = replace_one(s,
    'drawable.setStroke(d(1), borderColor);',
    'drawable.setStroke(d(borderColor == 0xFF84D9FF ? 3 : 1), borderColor);',
    'AppleControlCenterActivity')
s = replace_one(s,
    '''tile.animate().scaleX(focused ? 1.015f : 1f).scaleY(focused ? 1.015f : 1f)
                    .setDuration(120).start();''',
    '''// Use a bright in-bounds stroke, not scaleX/Y which clips the focus ring.
            tile.setScaleX(1f);
            tile.setScaleY(1f);''',
    'AppleControlCenterActivity')
# The separate Control Center app-management path also omits its extra dialog.
old_extra = '''                new AlertDialog.Builder(this).setTitle("卸载第三方应用")
                        .setMessage("准备卸载 " + pkg + "\\n此操作可能同时删除应用数据。下一步由 Android 系统再次确认。")
                        .setPositiveButton("进入系统卸载页面", (x, y) -> requestSafeUninstall(pkg))
                        .setNegativeButton("取消", null).show();'''
s = replace_one(s, old_extra, '                requestSafeUninstall(pkg);', 'AppleControlCenterActivity')
text[control] = s

# 4. Register only self-contained, non-exported quick panels. Normal/dangerous
# permissions never get silently granted and no secure-settings writes are used.
m = text[MANIFEST]
if 'android:name=".AppleWifiQuickActivity56"' not in m:
    additions = '''        <activity android:name=".AppleWifiQuickActivity56"
            android:exported="false" android:screenOrientation="landscape"
            android:theme="@android:style/Theme.Material.NoActionBar" />
        <activity android:name=".AppleBluetoothQuickActivity56"
            android:exported="false" android:screenOrientation="landscape"
            android:theme="@android:style/Theme.Material.NoActionBar" />
'''
    m = replace_one(m, '</application>', additions + '    </application>', 'AndroidManifest.xml')
for permission in ('android.permission.BLUETOOTH_ADMIN', 'android.permission.BLUETOOTH_SCAN',
                   'android.permission.BLUETOOTH_CONNECT'):
    if f'android:name="{permission}"' not in m:
        declaration = f'<uses-permission android:name="{permission}" />\n    '
        m = replace_one(m, '<application', declaration + '<application', 'AndroidManifest.xml')
text[MANIFEST] = m

for path, content in text.items():
    if path.read_text(encoding='utf-8') != content:
        path.write_text(content, encoding='utf-8')
for name in ASSETS:
    shutil.copyfile(HERE / name, JAVA / name)
print('OK V5.6: uninstall opens system confirmation immediately, no custom intermediate dialog')
print('OK V5.6: top Wi-Fi/Bluetooth -> dedicated auto-scan D-pad pages')
print('OK V5.6: Control Center focus rows fit within TV screen')
