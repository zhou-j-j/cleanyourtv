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
