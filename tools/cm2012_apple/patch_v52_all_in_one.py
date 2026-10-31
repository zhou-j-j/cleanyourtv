#!/usr/bin/env python3
"""CM201-2 V5.2 ONE-PASS source patch for the existing V5.1 GitHub workflow.

- green/red Ethernet status using Android NET_CAPABILITY_VALIDATED
- Wi-Fi 0..4 RSSI icon (10 seconds while Home foreground)
- library: short OK opens, long OK manage Add/Remove Home, App info, Uninstall
- do not edit screensaver or network-settings click targets

Exits rather than guessing if key code anchors diverge from expected CleanYourTV layout.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java'
LIBRARY = MAIN.with_name('AppLibraryDialog.java')
DRAWABLE_FROM = Path(__file__).with_name('NetworkStatusDrawable.java')
DRAWABLE_TO = MAIN.with_name('NetworkStatusDrawable.java')
CONFIG_XML = ROOT / 'launcher/app/src/main/res/values/config.xml'

for f in (MAIN, LIBRARY, DRAWABLE_FROM, CONFIG_XML):
    if not f.is_file():
        raise SystemExit(f'ERROR: Missing {f}. V5.1 source must be prepared before V5.2 patch.')

main = MAIN.read_text(encoding='utf-8')
lib = LIBRARY.read_text(encoding='utf-8')
config_xml = CONFIG_XML.read_text(encoding='utf-8')

FIELDS = '''    // CM2012_V52_DUAL_NETWORK: poll RSSI while Home is visible.
    private int cm2012NetMode = -1;
    private int cm2012NetLevel = -1;
    private boolean cm2012EthernetOnline = false;
    private final Runnable cm2012SignalPoll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) updateWifiLabel();
            handler.postDelayed(this, 10000L);
        }
    };
'''

# Draw the active transport, not an invented RSSI value for wired Ethernet.
# If an Ethernet interface is registered but offline, keep its red RJ45 icon.
NETWORK = '''            // CM2012_V52_ETHERNET_COLOR: active route, Wi-Fi RSSI, and validated Ethernet.
            int cmMode = NetworkStatusDrawable.DISCONNECTED;
            int cmLevel = 0;
            boolean cmEthOnline = false;
            try {
                android.net.ConnectivityManager cm =
                        (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                android.net.NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
                android.net.NetworkInfo eth = cm == null ? null :
                        cm.getNetworkInfo(android.net.ConnectivityManager.TYPE_ETHERNET);
                if (active != null && active.getType() == android.net.ConnectivityManager.TYPE_WIFI
                        && active.isConnected()) {
                    cmMode = NetworkStatusDrawable.WIFI;
                    android.net.wifi.WifiManager wm =
                            (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    android.net.wifi.WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                    if (wi != null && wi.getRssi() != android.net.wifi.WifiInfo.INVALID_RSSI) {
                        cmLevel = android.net.wifi.WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                    }
                } else if ((active != null && active.getType() == android.net.ConnectivityManager.TYPE_ETHERNET)
                        || eth != null) {
                    cmMode = NetworkStatusDrawable.ETHERNET;
                    boolean linkConnected = eth != null && eth.isConnected();
                    if (!linkConnected && active != null
                            && active.getType() == android.net.ConnectivityManager.TYPE_ETHERNET) {
                        linkConnected = active.isConnected();
                    }
                    if (linkConnected && cm != null) {
                        android.net.Network network = cm.getActiveNetwork();
                        android.net.NetworkCapabilities caps = network == null ? null :
                                cm.getNetworkCapabilities(network);
                        // VALIDATED means Android has successfully checked internet reachability.
                        // Some operator firmwares never set it, then a working Ethernet may look red.
                        cmEthOnline = caps != null && caps.hasTransport(
                                android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                                && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    }
                }
            } catch (RuntimeException ignored) {
                // Never show green when connectivity cannot be verified.
            }
            if (icon != wifiIcon || cmMode != cm2012NetMode
                    || cmLevel != cm2012NetLevel || cmEthOnline != cm2012EthernetOnline) {
                wifiIcon = icon;
                cm2012NetMode = cmMode;
                cm2012NetLevel = cmLevel;
                cm2012EthernetOnline = cmEthOnline;
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        new NetworkStatusDrawable(density, cmMode, cmLevel, cmEthOnline),
                        null, null, null);
            }'''

OLD_ICON = '''            if (icon != wifiIcon) {
                wifiIcon = icon;
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            }'''

# Must override the launcher's default white compound-drawable tint;
# otherwise wired red/green becomes white on the box.
OLD_TINT = '''            if (wifiOnlineTint != null && btnWifi.getCompoundDrawableTintList() != tint) {
                btnWifi.setCompoundDrawableTintList(tint);
            }'''
NEW_TINT = '''            // CM2012_V52_NO_ICON_TINT: preserve the drawable's green/red/white pixels.
            if (btnWifi.getCompoundDrawableTintList() != null) {
                btnWifi.setCompoundDrawableTintList(null);
            }'''

# Upgrade the previous V5.2 patch if it was added before; otherwise patch clean V5.1.
if 'CM2012_V52_ETHERNET_COLOR' not in main:
    if 'CM2012_V52_DUAL_NETWORK' in main:
        old_start = '            // CM2012_V52_DUAL_NETWORK: active route decides icon, RSSI only for Wi-Fi.'
        old_end = '''                        new NetworkStatusDrawable(density, cmMode, cmLevel), null, null, null);
            }'''
        field = '    private int cm2012NetLevel = -1;'
        if main.count(old_start) != 1 or main.count(old_end) != 1 or main.count(field) != 1:
            raise SystemExit('ERROR: Existing dual-network patch differs. No files written.')
        a = main.index(old_start)
        b = main.index(old_end, a) + len(old_end)
        main = main[:a] + NETWORK + main[b:]
        main = main.replace(field, field + '\n    private boolean cm2012EthernetOnline = false;', 1)
    else:
        anchors = ('    private int wifiIcon;',
                   '        handler.postDelayed(minuteTick, MINUTE_MS);',
                   '        handler.removeCallbacks(minuteTick);')
        if main.count(OLD_ICON) != 1 or any(main.count(a) != 1 for a in anchors):
            raise SystemExit('ERROR: MainActivity layout differs. No files written.')
        main = main.replace(anchors[0], anchors[0] + '\n' + FIELDS, 1)
        main = main.replace(OLD_ICON, NETWORK, 1)
        main = main.replace(anchors[1], anchors[1] + '\n        handler.removeCallbacks(cm2012SignalPoll);\n        handler.postDelayed(cm2012SignalPoll, 10000L);', 1)
        main = main.replace(anchors[2], anchors[2] + '\n        handler.removeCallbacks(cm2012SignalPoll);', 1)
if 'CM2012_V52_NO_ICON_TINT' not in main:
    if main.count(OLD_TINT) != 1:
        raise SystemExit('ERROR: Cannot find unique drawable-tint block. No files written.')
    main = main.replace(OLD_TINT, NEW_TINT, 1)

# AppLibraryDialog in CleanYourTV already has long-press menu logic,
# but standard menu lacks an explicit Uninstall entry. Add one only for user-installed apps.
# Android's own UI still requests final uninstall confirmation.
if 'CM2012_V52_UNINSTALL_ACTION' not in lib:
    lib_anchor = '''        Intent info = SystemSettings.appInfo(activity, pkg);'''
    lib_insertion = '''        // CM2012_V52_UNINSTALL_ACTION: only ordinary user apps, via Android confirmation UI.
        try {
            android.content.pm.ApplicationInfo installed =
                    activity.getPackageManager().getApplicationInfo(pkg, 0);
            if ((installed.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                addAction(rows, "卸载", () -> {
                    Intent delete = new Intent(Intent.ACTION_DELETE,
                            android.net.Uri.parse("package:" + pkg));
                    close();
                    try { activity.startActivity(delete); }
                    catch (ActivityNotFoundException | SecurityException err) {
                        Toast.makeText(activity, "无法打开系统卸载界面", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            // App was removed or restricted; omit Uninstall.
        }
'''
    method_anchor = '''    private void addAction(LinearLayout rows, int label, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(label);'''
    method_replace = '''    private void addAction(LinearLayout rows, int label, Runnable action) {
        addAction(rows, activity.getString(label), action);
    }
    private void addAction(LinearLayout rows, String label, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(label);'''
    if lib.count(lib_anchor) != 1 or lib.count(method_anchor) != 1:
        raise SystemExit('ERROR: AppLibraryDialog menu layout differs. No files written.')
    if 'holder.itemView.setOnLongClickListener(selectionMode ? null' not in lib or 'LibraryApps.okOpens(selectionMode, okOpens' not in lib:
        raise SystemExit('ERROR: Missing standard library click/long-click hooks. No files written.')
    lib = lib.replace(lib_anchor, lib_insertion + lib_anchor, 1)
    lib = lib.replace(method_anchor, method_replace, 1)

# Short OK opens irrespective of whether the app is already on Home.
# Selection mode must be false; it intentionally disables the long-press menu upstream.
for name, value in [('app_library', 'true'),
                    ('app_library_selection', 'false'),
                    ('library_ok_opens', 'true')]:
    rx = re.compile(r'(<bool\s+name="' + name + r'"\s*>)[ \t\r\n]*(true|false)[ \t\r\n]*(</bool>)')
    if len(rx.findall(config_xml)) != 1:
        raise SystemExit(f'ERROR: Missing or duplicate config.xml boolean {name}; no files written')
    config_xml = rx.sub(lambda m: m.group(1)+value+m.group(3), config_xml)

# Stage JSON modifications before changing any files, preserving all other keys.
json_writes = {}
for rel in ('launcher/app/src/main/assets/cm2012_apple_config.json',
            'tools/cm2012_apple/cm2012_apple_config.json'):
    p = ROOT / rel
    if p.exists():
        cfg = json.loads(p.read_text(encoding='utf-8-sig'))
        for name, value in [('app_library', True),
                            ('app_library_selection', False),
                            ('library_ok_opens', True)]:
            cfg[name] = value
        json_writes[p] = json.dumps(cfg, ensure_ascii=False, indent=2) + '\n'

# All structure checks passed; write the build tree once.
MAIN.write_text(main, encoding='utf-8')
LIBRARY.write_text(lib, encoding='utf-8')
CONFIG_XML.write_text(config_xml, encoding='utf-8')
DRAWABLE_TO.write_bytes(DRAWABLE_FROM.read_bytes())
for p, value in json_writes.items():
    p.write_text(value, encoding='utf-8')

assert 'CM2012_V52_ETHERNET_COLOR' in main
assert 'CM2012_V52_NO_ICON_TINT' in main
assert 'CM2012_V52_UNINSTALL_ACTION' in lib
print('OK: V5.2 combined patch installed in one pass')
print('OK: Ethernet green(validated)/red(unvalidated), Wi-Fi RSSI 0..4, refresh 10s')
print('OK: compound drawable tint removed so Ethernet red/green remains visible')
print('OK: app library short-OK=OPEN, long-OK=ACTION MENU, Add/Remove Home')
print('OK: Uninstall action (user apps only, Android confirmation required)')
print('OK: Wi-Fi/network settings click and V5.1 aerial screensaver remain unchanged')
