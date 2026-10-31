#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CM2012 Apple V5.3: six embedded wallpaper assets + twin network/Bluetooth indicators.

Run from repository root AFTER the V4, V5 and V5.2.2 scripts. Works with the
known CM2012 V5.2.2 patches; deliberately aborts on unknown or conflicting hooks.
"""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
JAVA_DIR = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
MAIN = JAVA_DIR / 'MainActivity.java'
CONTROL = JAVA_DIR / 'AppleControlCenterActivity.java'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
SOURCE_JSON = ROOT / 'tools/cm2012_apple/cm2012_apple_config.json'
ASSET_JSON = ROOT / 'launcher/app/src/main/assets/cm2012_apple_config.json'
ASSETS = ROOT / 'launcher/app/src/main/assets/cm2012_wallpapers'
IMG_NAMES = (
    'cm2012_apple_01_blue.jpg', 'cm2012_apple_02_purple.jpg',
    'cm2012_apple_03_gold.jpg', 'cm2012_apple_04_teal.jpg',
    'cm2012_apple_05_black.jpg', 'cm2012_apple_06_emerald.jpg',
)


def error(msg):
    sys.exit('ERROR V5.3: ' + msg)


for p in (MAIN, CONTROL, MANIFEST, SOURCE_JSON, ASSET_JSON,
          JAVA_DIR / 'NetworkStatusDrawable.java',
          JAVA_DIR / 'WallpaperPlayer.java',
          JAVA_DIR / 'LauncherSettingsActivity.java',
          *(HERE / n for n in ('AppleWallpaperPack.java', 'AppleTopStatus53.java')),
          *(HERE / n for n in IMG_NAMES),
          HERE / 'captions.txt'):
    if not p.is_file():
        error('required source/asset missing: ' + str(p))

# Guard against old forks without native wallpaper collection/picker support.
if 'wallpapers' not in (JAVA_DIR / 'WallpaperPlayer.java').read_text(encoding='utf-8'):
    error('WallpaperPlayer.java lacks collection support; upstream revision incompatible')
if 'wallpaper' not in (JAVA_DIR / 'LauncherSettingsActivity.java').read_text(encoding='utf-8').lower():
    error('LauncherSettingsActivity.java lacks wallpaper picker; upstream revision incompatible')

main = MAIN.read_text(encoding='utf-8')
control = CONTROL.read_text(encoding='utf-8')
manifest = MANIFEST.read_text(encoding='utf-8')

# Install collection only after V4's theme bootstrap, before Config.load()
# and the upstream WallpaperPlayer begin reading the theme.
THEME_CALL = 'AppleTheme.ensureInstalled(this);'
PACK_CALL = 'AppleWallpaperPack.ensureInstalled(this);'
if main.count(THEME_CALL) != 1:
    error('missing unique V4 theme bootstrap hook in MainActivity')
if PACK_CALL not in main:
    main = main.replace(THEME_CALL, THEME_CALL + '\n        ' + PACK_CALL, 1)
if main.count(PACK_CALL) != 1:
    error('duplicate wallpaper bootstrap call')

# V5.2.2 already attaches AppleNetworkBadge to the V4 top row. Swap that
# exact attachment for the combined V5.3 controller. Keep the old class file
# in place harmlessly so other legacy references are unaffected.
OLD = re.compile(r'AppleNetworkBadge\.attach\(this,\s*'
                 r'\(android\.widget\.LinearLayout\)\s*findViewById\(R\.id\.topButtons\),\s*'
                 r'btnWifi\);')
NEW = ('AppleTopStatus53.attach(this,\n'
       '                (android.widget.LinearLayout) findViewById(R.id.topButtons), btnWifi);')
if NEW not in main:
    matches = OLD.findall(main)
    if len(matches) != 1:
        error('expected exactly one AppleNetworkBadge.attach() call from V5.2.2; got ' + str(len(matches)))
    main = OLD.sub(lambda m: NEW, main, count=1)
if main.count('AppleTopStatus53.attach(this') != 1:
    error('multiple V5.3 status controllers found')

# Use CleanYourTV's real built-in Wallpaper row rather than inventing a
# second wallpaper picker. The integrated Control Center opens Launcher settings.
MENU_LINE = ('item("中文天气", "内置中文城市搜索、七天预报、AQI、湿度与风速", '
             '() -> startActivity(new Intent(this, AppleWeatherActivity.class)));')
MENU_NEW = ('item("外观与壁纸", "六款本地壁纸、遥控器切换、主题自动轮换", '
            '() -> startActivity(new Intent(this, LauncherSettingsActivity.class)));')
if MENU_NEW not in control:
    if control.count(MENU_LINE) != 1:
        error('V5 integrated control center Weather entry differs; cannot place Wallpaper settings link safely')
    control = control.replace(MENU_LINE, MENU_NEW + '\n        ' + MENU_LINE, 1)

# Older CM2012 project may lack Bluetooth permission, even on Android 9.
if not re.search(r'android:name\s*=\s*[\"\']android\.permission\.BLUETOOTH[\"\']', manifest):
    tag = '<application'
    if tag not in manifest:
        error('AndroidManifest.xml has no application tag')
    manifest = manifest.replace(tag,
         '<uses-permission android:name="android.permission.BLUETOOTH" />\n    ' + tag, 1)

# Respect existing wallpaper/theme preferences; only insert missing keys.
# 1440 minutes => change one picture per day; a fixed selection in the native
# picker disables rotation until "Follow theme" is chosen.
json_updates = {}
for path in (SOURCE_JSON, ASSET_JSON):
    try:
        obj = json.loads(path.read_text(encoding='utf-8-sig'))
    except (ValueError, UnicodeError) as exc:
        error(f'malformed theme JSON {path}: {exc}')
    if not isinstance(obj, dict):
        error('theme JSON root must be an object: ' + str(path))
    obj['wallpaper'] = True
    obj.setdefault('wallpaper_rotation_minutes', 1440)
    obj.setdefault('wallpaper_order', 'sequential')
    obj.setdefault('wallpaper_captions', False)
    json_updates[path] = json.dumps(obj, ensure_ascii=False, indent=2) + '\n'

# All checks passed; now make deterministic changes.
MAIN.write_text(main, encoding='utf-8')
CONTROL.write_text(control, encoding='utf-8')
MANIFEST.write_text(manifest, encoding='utf-8')
for path, contents in json_updates.items():
    path.write_text(contents, encoding='utf-8')
for filename in ('AppleWallpaperPack.java', 'AppleTopStatus53.java'):
    shutil.copyfile(HERE / filename, JAVA_DIR / filename)
ASSETS.mkdir(parents=True, exist_ok=True)
for filename in (*IMG_NAMES, 'captions.txt'):
    shutil.copyfile(HERE / filename, ASSETS / filename)

print('OK V5.3: six 1920x1080 wallpaper images embedded in the APK')
print('OK V5.3: uses CleanYourTV native Wallpaper row and daily sequential rotation')
print('OK V5.3: network WIFI label becomes icon; Bluetooth status icon added beside it')
print('OK V5.3: original Control Center, Xiri, Aerial Dream and App Library preserved')
