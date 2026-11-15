#!/usr/bin/env python3
# Isolated V5.8 test: remove MAIN/HOME filter while preserving LEANBACK_LAUNCHER.
# Production system package com.cm2012.applehome remains untouched.
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
manifest = ROOT / 'launcher/app/src/main/AndroidManifest.xml'
strings = ROOT / 'launcher/app/src/main/res/values/strings.xml'
main = manifest.read_text(encoding='utf-8')
start = main.find('android:name=".MainActivity"')
if start < 0:
    raise SystemExit('SAFE TEST ERROR: MainActivity not found')
act_start = main.rfind('<activity', 0, start)
act_end = main.find('</activity>', start)
if act_start < 0 or act_end < 0:
    raise SystemExit('SAFE TEST ERROR: cannot isolate MainActivity')
act_end += len('</activity>')
block = main[act_start:act_end]
filters = list(re.finditer(r'<intent-filter>.*?</intent-filter>', block, flags=re.DOTALL))
home = [m for m in filters if 'android.intent.category.HOME' in m.group()]
if len(home) != 1:
    raise SystemExit(f'SAFE TEST ERROR: expected 1 HOME filter, found {len(home)}')
if 'android.intent.category.LEANBACK_LAUNCHER' not in block:
    raise SystemExit('SAFE TEST ERROR: main TV launch entry missing')
new_block = block[:home[0].start()] + block[home[0].end():]
main = main[:act_start] + new_block + main[act_end:]
manifest.write_text(main, encoding='utf-8')
labels = strings.read_text(encoding='utf-8')
match = re.search(r'<string name="app_name">.*?</string>', labels)
if not match:
    raise SystemExit('SAFE TEST ERROR: app_name string missing')
labels = labels[:match.start()] + '<string name="app_name">Apple V5.8 测试</string>' + labels[match.end():]
strings.write_text(labels, encoding='utf-8')
# Verify the launcher is NOT a HOME choice even while MAIN can launch explicitly.
ns = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(manifest).getroot()
main_act = next(a for a in root.findall('./application/activity') if a.get(ns + 'name') == '.MainActivity')
assert not any(c.get(ns+'name') == 'android.intent.category.HOME' for c in main_act.iter('category'))
assert any(c.get(ns+'name') == 'android.intent.category.LEANBACK_LAUNCHER' for c in main_act.iter('category'))
print('SAFE TEST: HOME filter removed, manual launcher preserved, app label changed')
