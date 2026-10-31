#!/usr/bin/env python3
"""Add an integrated Control Center activity to upstream CleanYourTV.
Run from repo root. Refuses silent edits if upstream layout changed.
"""
from pathlib import Path
import shutil, sys

root = Path.cwd()
source = root / "launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java"
manifest = root / "launcher/app/src/main/AndroidManifest.xml"
if not source.exists() or not manifest.exists():
    sys.exit("ERROR: execute from the root of a CleanYourTV fork (launcher/ missing)")
patch_dir = Path(__file__).resolve().parent
activity = patch_dir / "AppleControlCenterActivity.java"
if not activity.is_file():
    sys.exit("ERROR: missing AppleControlCenterActivity.java next to patch script")

main = source.read_text(encoding="utf-8")
needle = "btnSettings.setOnLongClickListener(v -> {"
addition = """btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, AppleControlCenterActivity.class));
        });
        // Keep the original long-OK settings menu accessible.
        """
if "new Intent(this, AppleControlCenterActivity.class)" not in main:
    if needle not in main:
        sys.exit("ERROR: upstream MainActivity changed: settings button hook not found")
    source.write_text(main.replace(needle, addition + needle, 1), encoding="utf-8")
    print("Patched main Settings button: opens integrated control center")
else:
    print("MainActivity already patched")

# CM201-2 Firehome ROM: the upstream Ethernet/Wi-Fi pill does not open
# a usable system settings page. Keep its status visible, but have a short
# press open the verified integrated Control Center (which shows eth0/IP
# and whose Wireless item reaches the verified vendor WIRELESS activity).
network_old = "btnWifi.setOnClickListener(v -> openNetworkPill());"
network_new = "btnWifi.setOnClickListener(v -> startActivity(new Intent(this, AppleControlCenterActivity.class)));"
main = source.read_text(encoding="utf-8")
if network_old in main:
    main = main.replace(network_old, network_new, 1)
    source.write_text(main, encoding="utf-8")
    print("Patched top-right Ethernet network pill -> integrated Control Center")
elif network_new in main:
    print("Network pill is already patched")
else:
    sys.exit("ERROR: upstream network pill hook changed; refusing a blind edit")

dest = source.parent / activity.name
shutil.copyfile(activity, dest)
manifest_src = manifest.read_text(encoding="utf-8")
if "AppleControlCenterActivity" not in manifest_src:
    if "</application>" not in manifest_src:
        sys.exit("ERROR: manifest has no </application> tag")
    entry = """        <activity android:name=".AppleControlCenterActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@android:style/Theme.Material.NoActionBar" />
"""
    manifest_src = manifest_src.replace("</application>", entry + "    </application>", 1)
for permission in [
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.REQUEST_DELETE_PACKAGES",
]:
    if permission not in manifest_src:
        pos = manifest_src.find("<application")
        if pos == -1: sys.exit("ERROR: manifest has no <application tag")
        manifest_src = manifest_src[:pos] + '<uses-permission android:name="' + permission + '" />\n    ' + manifest_src[pos:]

if 'android.permission.MODIFY_AUDIO_SETTINGS' not in manifest_src:
    pos = manifest_src.find("<application")
    if pos == -1: sys.exit("ERROR: manifest has no <application tag")
    manifest_src = manifest_src[:pos] + '<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n    ' + manifest_src[pos:]
manifest.write_text(manifest_src, encoding="utf-8")
print("Integrated control center registered in manifest")

# V4: install the original Apple-inspired wallpaper/config from inside the APK,
# turn on upstream's real built-in App Library, and replace the header with
# three TV-remote friendly buttons + a real quick-links dropdown.
import re
main = source.read_text(encoding="utf-8")
if "AppleTheme.ensureInstalled(this);" not in main:
    marker = "Config cfg = Config.load(this);"
    if main.count(marker) != 1:
        sys.exit("ERROR: Cannot locate the unique Config.load() hook for embedded V4 theme")
    main = main.replace(marker, "AppleTheme.ensureInstalled(this);\n        " + marker, 1)

if "void openAppleAppLibrary()" not in main:
    # The official App Library is built-in; invoke it instead of duplicating its state.
    pat_create = r"@Override\s+protected void onCreate\(Bundle savedInstanceState\) \{"
    match = re.search(pat_create, main)
    if not match:
        sys.exit("ERROR: Cannot locate onCreate() to expose App Library")
    replacement = ("public void openAppleAppLibrary() { onLibraryRequested(); }\n\n    "
                   "@Override\n    protected void onCreate(Bundle savedInstanceState) {")
    main = main[:match.start()] + replacement + main[match.end():]

if "AppleTopMenu.attach(this" not in main:
    pat = r'btnSettings\.setOnLongClickListener\(v -> \{\s*showSettingsMenu\(\);\s*return true;\s*\}\);'
    mat = re.search(pat, main)
    if not mat:
        sys.exit("ERROR: Cannot locate settings long-press handler for the 3-button header")
    # Keep upstream long-OK handler alive; the new three buttons render on top of it.
    code = ("\n        AppleTopMenu.attach(this,\n"
            "                (android.widget.LinearLayout) findViewById(R.id.topButtons),\n"
            "                btnInputs, btnWifi, btnSettings);\n")
    main = main[:mat.end()] + code + main[mat.end():]
source.write_text(main, encoding="utf-8")

for filename in ("AppleTopMenu.java", "AppleTheme.java"):
    f = patch_dir / filename
    if not f.is_file():
        sys.exit("ERROR: required V4 source missing: " + filename)
    shutil.copyfile(f, source.parent / filename)

assets = root / "launcher/app/src/main/assets"
assets.mkdir(parents=True, exist_ok=True)
for filename in ("cm2012_apple_config.json", "cm2012_apple_wallpaper.jpg"):
    f = patch_dir / filename
    if not f.is_file():
        sys.exit("ERROR: required V4 asset missing: " + filename)
    shutil.copyfile(f, assets / filename)
print("CM2012 V4: glass header + Inputs popup + real App Library + embedded dark-red theme ready")
