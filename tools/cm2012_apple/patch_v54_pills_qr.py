#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CM2012 V5.4 incremental patch: matched clickable network/BT capsules;
phone QR -> TV online MP4 editor using one-time home-LAN HTTP transfer.

Run AFTER patch_v53_wallpaper_bluetooth.py, BEFORE Gradle.
Requires the established V5.2.2 AppleAerialSettingsActivity online-playlist editor.
Strict source guards: refuses unfamiliar upstream rather than guessing edits.
"""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
AERIAL = JAVA / 'AppleAerialSettingsActivity.java'
STATUS = JAVA / 'AppleTopStatus53.java'
MAIN = JAVA / 'MainActivity.java'
GRADLE = ROOT / 'launcher/app/build.gradle'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
SOURCES = ('AppleTopStatus53.java', 'AppleQrMp4Bridge.java')
MARK = '// CM2012_V54_QR_INPUT'

def die(msg):
    sys.exit('ERROR CM2012 V5.4: ' + msg)

for p in (AERIAL, STATUS, MAIN, GRADLE, MANIFEST, *(HERE / x for x in SOURCES)):
    if not p.is_file(): die('missing file: ' + str(p))

main = MAIN.read_text(encoding='utf-8')
if main.count('AppleTopStatus53.attach(this') != 1:
    die('V5.3 network/Bluetooth hook missing; run V5.3 before V5.4')

original_status = STATUS.read_text(encoding='utf-8')
if 'class AppleTopStatus53' not in original_status or 'ACTION_BLUETOOTH_SETTINGS' not in original_status:
    die('unexpected V5.3 status source')

editor = AERIAL.read_text(encoding='utf-8')
if 'private void showOnlineEditor(int editIndex)' not in editor or 'savePlaylist(unique, true)' not in editor:
    die('expected V5.2.2 online MP4 editor / playlist not found')
if MARK not in editor:
    changes = [
        ('        Button save = new Button(this);\n        save.setText("确认保存");',
         '        ' + MARK + '\n'
         '        Button scan = new Button(this);\n'
         '        scan.setText("手机扫码填写");\n'
         '        Button save = new Button(this);\n        save.setText("确认保存");'),
        ('        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(65), 1));\n'
         '        actions.addView(save, new LinearLayout.LayoutParams(0, dp(65), 1));',
         '        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(65), 1));\n'
         '        actions.addView(scan, new LinearLayout.LayoutParams(0, dp(65), 1));\n'
         '        actions.addView(save, new LinearLayout.LayoutParams(0, dp(65), 1));'),
        ('        decorateFocusable(cancel, false);\n        decorateFocusable(save, false);',
         '        decorateFocusable(cancel, false);\n'
         '        decorateFocusable(scan, false);\n'
         '        decorateFocusable(save, false);'),
        ('        cancel.setOnClickListener(v -> dialog.dismiss());\n        save.setOnClickListener(v -> {',
         '        cancel.setOnClickListener(v -> dialog.dismiss());\n'
         '        scan.setOnClickListener(v -> AppleQrMp4Bridge.show(this, field, dialog));\n'
         '        save.setOnClickListener(v -> {'),
        ('        tip.setText("必须是能直接播放的 MP4 视频地址。输入完成后，用方向键选中“确认保存”。");',
         '        tip.setText("可用遥控器输入，也可点“手机扫码填写”，用微信扫一扫或手机浏览器粘贴长网址；提交后在电视按“确认保存”。仅支持 HTTPS 直链。");'),
    ]
    # Match all anchors before writing any files. Never risk partial upgrades.
    for needle, _ in changes:
        if editor.count(needle) != 1:
            die('editor changed; expected exactly one occurrence of: ' + repr(needle[:90]))
    for old, new in changes:
        editor = editor.replace(old, new, 1)
else:
    if editor.count(MARK) != 1: die('duplicate QR patch marker')

manifest = MANIFEST.read_text(encoding='utf-8')
for permission in ('android.permission.INTERNET', 'android.permission.BLUETOOTH'):
    if permission not in manifest:
        die('V5.3 manifest missing ' + permission)

gradle = GRADLE.read_text(encoding='utf-8')
if 'dependencies {' not in gradle:
    die('unsupported launcher/app/build.gradle: missing Groovy dependencies block')
if not re.search(r"com\.google\.zxing:core:[\w.\-]+", gradle):
    gradle = gradle.replace('dependencies {',
                            "dependencies {\n    implementation 'com.google.zxing:core:3.5.3' // CM2012 V5.4 local QR\n", 1)

for name in SOURCES:
    source = (HERE / name).read_text(encoding='utf-8')
    if not source.startswith('package com.gathin.tvhome;'):
        die('invalid Java package in ' + name)
if 'server.setSoTimeout(1000)' not in (HERE / 'AppleQrMp4Bridge.java').read_text(encoding='utf-8'):
    die('QR receiver must limit its lifetime')
if 'capsuleShape' not in (HERE / 'AppleTopStatus53.java').read_text(encoding='utf-8'):
    die('new unified pill class missing')

# All validations passed: install exact validated code.
AERIAL.write_text(editor, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
for name in SOURCES:
    shutil.copyfile(HERE / name, JAVA / name)
print('OK CM2012 V5.4: Network + Bluetooth use matching focusable pill backgrounds')
print('OK CM2012 V5.4: Bluetooth pill opens Android Bluetooth settings; network click preserved')
print('OK CM2012 V5.4: Online MP4 editor has WeChat/phone scan-to-fill (not auto-save)')
print('OK CM2012 V5.4: QR local HTTP only; random session token, 5-minute expiry')
print('OK CM2012 V5.4: adds ZXing QR encoder dependency; no WeChat SDK/cloud')
