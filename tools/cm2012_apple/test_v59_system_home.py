#!/usr/bin/env python3
"""Reject a system-candidate workflow without a HOME entry or with the old package ID."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
manifest=(root/'launcher/app/src/main/AndroidManifest.xml').read_text('utf-8')
ns='{http://schemas.android.com/apk/res/android}'
r=ET.fromstring(manifest)
activity=next(a for a in r.findall('./application/activity') if a.get(ns+'name')=='.MainActivity')
filters=[[c.get(ns+'name') for c in f.findall('./category')] for f in activity.findall('./intent-filter')]
assert sum('android.intent.category.HOME' in f for f in filters)==1, 'Require exactly one HOME filter'
assert any('android.intent.category.LEANBACK_LAUNCHER' in f for f in filters), 'Require TV app icon'
assert 'sharedUserId' not in manifest,'No system UID impersonation'
gradle=(root/'launcher/app/build.gradle').read_text('utf-8')
assert 'versionCode 59' in gradle and "versionName '5.9-system-candidate'" in gradle
assert 'Apple Home V5.9 系统候选' in (root/'launcher/app/src/main/res/values/strings.xml').read_text('utf-8')
wf=(root/'.github/workflows/cm2012-apple-v591-system-home-fonts.yml').read_text('utf-8')
assert '-PlauncherApplicationId=com.cm2012.applehome.v59system' in wf
assert 'patch_v59_isolated_test.py' not in wf
assert 'patch_v59_system_home.py' in wf
print('PASS: distinct package, HOME entry present, independent label, release version, no system UID')
