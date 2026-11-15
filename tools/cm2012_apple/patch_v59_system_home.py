#!/usr/bin/env python3
"""Prepare separately installable V5.9 HOME candidate; never touch old /system app.
Run after V5.9 patch and before Gradle build.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
manifest = root/'launcher/app/src/main/AndroidManifest.xml'
strings = root/'launcher/app/src/main/res/values/strings.xml'
gradle = root/'launcher/app/build.gradle'
xml = manifest.read_text('utf-8')
ns='{http://schemas.android.com/apk/res/android}'
r=ET.fromstring(xml)
a=next((x for x in r.findall('./application/activity') if x.attrib.get(ns+'name')=='.MainActivity'),None)
assert a is not None,'MainActivity missing'
assert any(any(c.attrib.get(ns+'name')=='android.intent.category.HOME' for c in x.findall('./category')) for x in a.findall('./intent-filter')),'HOME intent-filter missing'
assert any(any(c.attrib.get(ns+'name')=='android.intent.category.LEANBACK_LAUNCHER' for c in x.findall('./category')) for x in a.findall('./intent-filter')),'TV app launch entry missing'
assert 'sharedUserId' not in r.attrib and 'android:sharedUserId' not in xml,'Must not claim system UID'
assert '.v59test' not in xml,'Test package reference must not be baked into manifest'
labels=strings.read_text('utf-8')
assert re.search(r'<string name="app_name">[^<]*</string>',labels), 'app label missing'
labels=re.sub(r'<string name="app_name">[^<]*</string>', '<string name="app_name">Apple Home V5.9 系统候选</string>',labels,count=1)
strings.write_text(labels,'utf-8')
build=gradle.read_text('utf-8')
assert "versionCode 11" in build and "versionName '3.2'" in build, 'unexpected version; examine manually'
build=build.replace('versionCode 11','versionCode 59',1).replace("versionName '3.2'","versionName '5.9-system-candidate'",1)
gradle.write_text(build,'utf-8')
print('SYSTEM CANDIDATE: HOME and LEANBACK retained; distinct applicationId must be passed in Gradle')
