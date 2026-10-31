#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CM2012 Apple V5.5 incremental patch.

Expected order V4 -> V5 -> V5.2.2 -> V5.3 -> V5.4 -> V5.5 -> Gradle.
Patches known V5.4 sources. Home long-OK hook is optional when
upstream uses a different signature; Library long-OK remains available.
"""
from pathlib import Path
import re
import shutil
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
MAIN = JAVA / 'MainActivity.java'
LIB = JAVA / 'AppLibraryDialog.java'
ADAPTER = JAVA / 'AppAdapter.java'
AERIAL = JAVA / 'AppleAerialSettingsActivity.java'
STATUS = JAVA / 'AppleTopStatus53.java'
QR = JAVA / 'AppleQrMp4Bridge.java'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
STRINGS = ROOT / 'launcher/app/src/main/res/values/strings.xml'
# The V5.3 / V5.4 steps must consume the V5.4-compatible source first.
# Stage V5.5 replacements under distinct filenames to avoid premature injection.
SOURCES = ('AppleIconManager55.java', 'AppleIconPicker55.java',
           'AppleBluetoothSettings55.java', 'AppleIconHomeMenu55.java',
           'AppleTopStatus55.java.txt', 'AppleQrMp4Bridge55.java.txt')
DEST_NAMES = {
    'AppleTopStatus55.java.txt': 'AppleTopStatus53.java',
    'AppleQrMp4Bridge55.java.txt': 'AppleQrMp4Bridge.java',
}
MARK = '// CM2012_V55_ICON_BIND'

def fail(message):
    sys.exit('ERROR V5.5 (no files modified): ' + message)


def method_braces(text, open_pos):
    """Return matching closing curly brace, ignoring comments/string literals."""
    count = 0
    state = 'code'
    pos = open_pos
    while pos < len(text):
        c = text[pos]
        n = text[pos + 1] if pos + 1 < len(text) else ''
        if state == 'code':
            if c == '/' and n == '/': state = 'line'; pos += 2; continue
            if c == '/' and n == '*': state = 'block'; pos += 2; continue
            if c == '"': state = 'str'; pos += 1; continue
            if c == "'": state = 'char'; pos += 1; continue
            if c == '{': count += 1
            elif c == '}':
                count -= 1
                if count == 0: return pos
        elif state == 'line':
            if c == '\n': state = 'code'
        elif state == 'block':
            if c == '*' and n == '/': state = 'code'; pos += 2; continue
        elif state in ('str', 'char'):
            if c == '\\': pos += 2; continue
            if (c == '"' and state == 'str') or (c == "'" and state == 'char'):
                state = 'code'
        pos += 1
    raise ValueError('unbalanced braces')


def hook_adapter(text, filepath):
    if MARK in text:
        if text.count(MARK) != 1: fail('duplicate icon binding marker: ' + filepath)
        return text
    # Standard RecyclerView bind methods; source guard prevents blind insertion.
    pattern = re.compile(
        r'\bonBindViewHolder\s*\(\s*(?:@\w+\s+)*(?:[\w$.]+)\s+(\w+)\s*,\s*'
        r'(?:@\w+\s+)*int\s+(\w+)\s*\)\s*\{')
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        fail(filepath + ' requires exactly one onBindViewHolder(holder, index), found ' + str(len(matches)))
    match = matches[0]
    close = method_braces(text, match.end() - 1)
    holder, index = match.group(1), match.group(2)
    addition = ('\n        ' + MARK + '\n'
                '        AppleIconManager55.afterBind(this, ' + holder + ', ' + index + ');\n')
    return text[:close] + addition + text[close:]

required = [MAIN, LIB, ADAPTER, AERIAL, STATUS, QR, MANIFEST, STRINGS]
required += [HERE / f for f in SOURCES]
for p in required:
    if not p.is_file(): fail('missing file: ' + str(p))

main = MAIN.read_text(encoding='utf-8')
lib = LIB.read_text(encoding='utf-8')
adapter = ADAPTER.read_text(encoding='utf-8')
aerial = AERIAL.read_text(encoding='utf-8')
manifest = MANIFEST.read_text(encoding='utf-8')
strings = STRINGS.read_text(encoding='utf-8')
status = STATUS.read_text(encoding='utf-8')
qr = QR.read_text(encoding='utf-8')
if main.count('AppleTopStatus53.attach(this') != 1:
    fail('V5.4 status hook missing. Run the previous patches first.')
if 'CM2012_V54_QR_INPUT' not in aerial or 'AppleQrMp4Bridge.show(this, field, dialog)' not in aerial:
    fail('V5.4 online MP4 editor hook missing')
if 'class AppleQrMp4Bridge' not in qr or 'server.setSoTimeout(1000)' not in qr:
    fail('V5.4 QR bridge not found')
if 'class AppleTopStatus53' not in status:
    fail('V5.4 network/Bluetooth status class not found')
if 'addAction(rows, R.string.cm2012_uninstall' not in lib:
    fail('V5 library long-OK menu hook missing')
if 'private void showOnlineEditor(int editIndex)' not in aerial:
    fail('online editor changed')
for filename in SOURCES:
    contents = (HERE / filename).read_text(encoding='utf-8')
    if not contents.startswith('package com.gathin.tvhome;'):
        fail('invalid Java source package: ' + filename)
if 'AppleBluetoothSettings55.open(activity)' not in (HERE / 'AppleTopStatus55.java.txt').read_text(encoding='utf-8'):
    fail('new status pill must call dedicated Bluetooth settings')
new_qr = (HERE / 'AppleQrMp4Bridge55.java.txt').read_text(encoding='utf-8')
if 'dp(activity, 190)' not in new_qr or 'CountDownLatch' not in new_qr:
    fail('new QR bridge must include compact code and UI transfer confirmation')

# Long-OK App Library menu: insert before the width calculation so existing
# Add-to-Home / info / uninstall menu remains in place and in its own order.
entry = 'addAction(rows, R.string.cm2012_change_icon'
if entry not in lib:
    anchor = '        int menuWidth = dp(220);'
    if lib.count(anchor) != 1: fail('AppLibraryDialog showActions menu-width anchor changed')
    extra = '''        // CM2012_V55_ICON_MENU: user-editable icon, never alters the target APK.
        addAction(rows, R.string.cm2012_change_icon, () -> {
            dismissActions();
            AppleIconPicker55.open(activity, pkg);
        });
'''
    lib = lib.replace(anchor, extra + anchor, 1)
if lib.count(entry) != 1: fail('duplicate icon menu')

# The original CM2012 MainActivity implements AppAdapter.TileListener and
# exposes onTileLongPress(View, int). Wrap the existing action; users can
# choose the new icon picker OR its original management popup. No listener
# replacement (important for reorder, remove from Home and app info).
home_menu_marker = '// CM2012_V55_HOME_LONG_MENU'
if home_menu_marker not in main:
    home_rx = re.compile(r'\bonTileLongPress\s*\(\s*(?:android\.view\.)?View\s+(\w+)\s*,\s*int\s+(\w+)\s*\)\s*\{')
    home_matches = list(home_rx.finditer(main))
    if len(home_matches) == 0:
        print('NOTE V5.5: MainActivity has no onTileLongPress(View,int); homepage long-OK integration skipped safely.')
    elif len(home_matches) > 1:
        fail('MainActivity has multiple onTileLongPress(View,int) methods; cannot choose safely')
    if len(home_matches) == 1:
        hm = home_matches[0]
        if 'private boolean appleV55ShowOriginalMenu;' not in main:
            field_anchor = 'private AppAdapter adapter;'
            if main.count(field_anchor) != 1: fail('MainActivity adapter field changed')
            main = main.replace(field_anchor,
                                field_anchor + '\n    private boolean appleV55ShowOriginalMenu;', 1)
            # Insertion above changes indices; repeat the method search below.
            hm = list(home_rx.finditer(main))[0]
        view, position = hm.group(1), hm.group(2)
        preamble = ("\n        " + home_menu_marker + "\n"
            "        if (!appleV55ShowOriginalMenu) {\n"
            "            String iconPkg = AppleIconManager55.packageAt(adapter, " + position + ");\n"
            "            if (iconPkg != null) {\n"
            "                AppleIconHomeMenu55.show(this, iconPkg, () -> {\n"
            "                    appleV55ShowOriginalMenu = true;\n"
            "                    try { onTileLongPress(" + view + ", " + position + "); }\n"
            "                    finally { appleV55ShowOriginalMenu = false; }\n"
            "                });\n"
            "                return;\n"
            "            }\n"
            "        }\n")
        insert_at = hm.end()
        main = main[:insert_at] + preamble + main[insert_at:]

# Home and Library use the exact same preference/key and drawable replacement.
# Injection is after their native bind logic. The helper discovers the model's
# package name from the adapter's already bound list, and does nothing if the
# user has not set an icon for that package.
adapter = hook_adapter(adapter, 'AppAdapter.java')
lib = hook_adapter(lib, 'AppLibraryDialog.java')

# Keep main's native focus handling and D-pad key listeners unchanged.
focus = 'AppleIconManager55.addFocusTracking(root);'
if focus not in main:
    anchor = 'root = findViewById(R.id.root);'
    if main.count(anchor) != 1: fail('MainActivity root binding changed')
    main = main.replace(anchor, anchor + '\n        ' + focus, 1)

# When returning from the icon picker, refresh both bound tile lists.
refresh = 'if (AppleIconManager55.hasAny(this)) {'
if refresh not in main:
    matches = list(re.finditer(r'protected\s+void\s+onResume\s*\(\s*\)\s*\{', main))
    if len(matches) != 1: fail('MainActivity onResume() not unique')
    opening = matches[0].end()
    closing = method_braces(main, opening - 1)
    body = main[opening:closing]
    if 'super.onResume();' not in body: fail('MainActivity onResume lacks super.onResume()')
    body = body.replace('super.onResume();',
        'super.onResume();\n        // CM2012_V55_RETURN_FROM_PICKER\n'
        '        if (AppleIconManager55.hasAny(this)) {\n'
        '            if (adapter != null) adapter.notifyDataSetChanged();\n'
        '            if (appLibrary != null) appLibrary.refresh();\n'
        '        }', 1)
    main = main[:opening] + body + main[closing:]

if 'name="cm2012_change_icon"' not in strings:
    if strings.count('</resources>') != 1: fail('strings.xml malformed')
    strings = strings.replace('</resources>',
        '    <string name="cm2012_change_icon">更改图标</string>\n</resources>', 1)
if 'android:name=".AppleIconPicker55"' not in manifest:
    if manifest.count('</application>') != 1: fail('AndroidManifest.xml missing unique application end')
    addition = '''        <activity android:name=".AppleIconPicker55"
            android:exported="false" android:screenOrientation="landscape" />
'''
    manifest = manifest.replace('</application>', addition + '    </application>', 1)

# Atomic-ish safety: every guard ran before this point; create backups before writing.
outputs = {MAIN: main, LIB: lib, ADAPTER: adapter,
           AERIAL: aerial, MANIFEST: manifest, STRINGS: strings}
backup_dir = ROOT / 'tools/cm2012_apple/.v55_backups'
backup_dir.mkdir(parents=True, exist_ok=True)
for path, contents in outputs.items():
    if contents != path.read_text(encoding='utf-8'):
        shutil.copyfile(path, backup_dir / path.name)
        path.write_text(contents, encoding='utf-8')
for name in SOURCES:
    shutil.copyfile(HERE / name, JAVA / DEST_NAMES.get(name, name))
print('OK V5.5: one Apple icon mapping for Home + Library; App Library long-OK -> change icon')
if home_menu_marker not in main:
    print('NOTE V5.5: use App Library long-OK to change an icon; Home will display that icon too')
print('OK V5.5: built-in styles and local PNG/JPG selection, restore original')
print('OK V5.5: compact QR with phone-to-TV acknowledgement and address preview')
print('OK V5.5: Bluetooth opens dedicated settings when ROM provides one')
print('OK V5.5: voice, wallpapers, screensaver and Dangbei untouched')
