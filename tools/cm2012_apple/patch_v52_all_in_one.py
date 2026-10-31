#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CM201-2 V5.2.1: visible network status + App Library config + online playlist.

Run from GitHub Actions *after* patch_controlcenter.py and patch_v5_features.py,
and before Gradle. Only one V5.2 patch command should be present.

Importantly, patch_v5_features.py ALREADY inserts the native uninstall menu.  Do
not add a second uninstall action as previous V5.2 builds accidentally did.
"""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
MAIN = JAVA / 'MainActivity.java'
LIB = JAVA / 'AppLibraryDialog.java'
CONFIG = ROOT / 'launcher/app/src/main/res/values/config.xml'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
REPLACEMENTS = (
    'AppleNetworkBadge.java',
    'NetworkStatusDrawable.java',
    'AppleAerialDream.java',
    'AppleAerialSettingsActivity.java',
)


def die(reason):
    sys.exit('ERROR V5.2.1: ' + reason)


for f in (MAIN, LIB, CONFIG, MANIFEST, *(HERE / s for s in REPLACEMENTS),
          *(JAVA / s for s in ('AppleAerialDream.java', 'AppleAerialSettingsActivity.java'))):
    if not f.is_file():
        die('missing file: ' + str(f))

main = MAIN.read_text(encoding='utf-8')
lib = LIB.read_text(encoding='utf-8')
conf = CONFIG.read_text(encoding='utf-8')
manifest = MANIFEST.read_text(encoding='utf-8')

# In the V4 patch the top-row visual implementation is AppleTopMenu.attach().
# Install a lifecycle-bound updater *after* its attachment and use the actual
# header view rather than assuming MainActivity's original Wifi drawable is visible.
HEADER = '''        AppleTopMenu.attach(this,
                (android.widget.LinearLayout) findViewById(R.id.topButtons),
                btnInputs, btnWifi, btnSettings);'''
BADGE = '''
        // CM2012_V521_VISIBLE_NETWORK_BADGE: update the actual visible pill.
        AppleNetworkBadge.attach(this,
                (android.widget.LinearLayout) findViewById(R.id.topButtons), btnWifi);'''
if main.count(HEADER) != 1:
    die('cannot find V4 AppleTopMenu.attach; MainActivity needs source-specific review')
if 'CM2012_V521_VISIBLE_NETWORK_BADGE' not in main:
    main = main.replace(HEADER, HEADER + BADGE, 1)

# The earlier V5.2 injected another uninstall before app-info, even though V5
# had already inserted cm2012_uninstall. If a fork has retained that injection,
# remove only the exact added block and keep the V5 native uninstall intact.
MARK = '        // CM2012_V52_UNINSTALL_ACTION: only ordinary user apps, via Android confirmation UI.'
END = '        Intent info = SystemSettings.appInfo(activity, pkg);'
if MARK in lib:
    if lib.count(MARK) != 1 or lib.count(END) != 1:
        die('old V5.2 uninstall layout changed; refusing risky menu edit')
    i = lib.index(MARK)
    j = lib.index(END, i)
    lib = lib[:i] + lib[j:]

# The V5 feature step must have already installed one uninstall entry.
if lib.count('addAction(rows, R.string.cm2012_uninstall') != 1:
    die('expected exactly one V5 uninstall menu action; check patch_v5_features.py')
# Preserve upstream dynamic Add/Remove Home logic (the menu uses the app's
# current Home membership); don't graft in guessed additional menu actions.
if 'LibraryApps.okOpens(selectionMode, okOpens' not in lib:
    die('AppLibraryDialog short-OK/long-OK hooks differ from expected upstream')

# Enable short OK to open, long OK to show native management menu.
for name, value in (('app_library', 'true'),
                    ('app_library_selection', 'false'),
                    ('library_ok_opens', 'true')):
    rx = re.compile(r'(<bool\s+name="' + name + r'"\s*>)[\s]*(true|false)[\s]*(</bool>)')
    if len(rx.findall(conf)) != 1:
        die('config.xml missing or duplicate: ' + name)
    conf = rx.sub(lambda m: m.group(1) + value + m.group(3), conf)

# V4 always copies its embedded JSON from tools/cm2012_apple on every run;
# keep both source JSON and generated assets aligned so this setting persists.
json_writes = {}
for rel in ('tools/cm2012_apple/cm2012_apple_config.json',
            'launcher/app/src/main/assets/cm2012_apple_config.json'):
    p = ROOT / rel
    if p.is_file():
        obj = json.loads(p.read_text(encoding='utf-8-sig'))
        obj.update(app_library=True, app_library_selection=False, library_ok_opens=True)
        json_writes[p] = json.dumps(obj, ensure_ascii=False, indent=2) + '\n'
if not json_writes:
    die('cannot find V4 embedded JSON configuration')

# Screen saver has already been registered by V5; don't rewrite the manifest
# or change the activity names, to preserve the box's selected DreamService.
for piece in ('AppleAerialDream', 'AppleAerialSettingsActivity',
              'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'):
    if piece not in manifest:
        die('V5 manifest is missing: ' + piece)
for filename in REPLACEMENTS:
    src = (HERE / filename).read_text(encoding='utf-8')
    if 'package com.gathin.tvhome;' not in src:
        die('unexpected Java package in ' + filename)
for filename in ('AppleAerialDream.java', 'AppleAerialSettingsActivity.java'):
    old = (JAVA / filename).read_text(encoding='utf-8')
    if 'cm2012_apple_aerial' not in old:
        die('unexpected existing V5 aerial source ' + filename)

# Write only after all checks succeed.
MAIN.write_text(main, encoding='utf-8')
LIB.write_text(lib, encoding='utf-8')
CONFIG.write_text(conf, encoding='utf-8')
for path, value in json_writes.items():
    path.write_text(value, encoding='utf-8')
for filename in REPLACEMENTS:
    shutil.copyfile(HERE / filename, JAVA / filename)

print('OK V5.2.1: network updater attached to visible V4 header')
print('OK V5.2.1: Ethernet green/red, Wi-Fi 0..4 signal (every 3 sec)')
print('OK V5.2.1: library short OK opens; native long OK menu; one V5 uninstall')
print('OK V5.2.1: online MP4 playlist, skip failures, manage URLs, keep local mode')
print('NOTE: Home add/remove actions remain upstream-native; verify on device')
