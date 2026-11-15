#!/usr/bin/env python3
"""Distinguish V5.9.1 system candidate from V5.9 (after V5.9 system validation)."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
gradle=root/'launcher/app/build.gradle'
source=gradle.read_text('utf8')
assert 'versionCode 59' in source and "versionName '5.9-system-candidate'" in source
source=source.replace('versionCode 59','versionCode 591',1).replace("versionName '5.9-system-candidate'","versionName '5.9.1-system-fonts'",1)
gradle.write_text(source,'utf8')
labels=root/'launcher/app/src/main/res/values/strings.xml'
label_source=labels.read_text('utf8')
assert 'Apple Home V5.9 系统候选' in label_source
labels.write_text(label_source.replace('Apple Home V5.9 系统候选','Apple Home V5.9.1 系统候选',1),'utf8')
print('Candidate version updated to 591 / 5.9.1-system-fonts; label V5.9.1')
