#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[2]
gradle=(root/'launcher/app/build.gradle').read_text('utf8')
assert 'versionCode 591' in gradle and "versionName '5.9.1-system-fonts'" in gradle
assert 'versionCode 59\n' not in gradle
assert 'Apple Home V5.9.1 系统候选' in (root/'launcher/app/src/main/res/values/strings.xml').read_text('utf8')
wf=(root/'.github/workflows/cm2012-apple-v591-system-home-fonts.yml').read_text('utf8')
assert '-PlauncherApplicationId=com.cm2012.applehome.v59system' in wf
assert 'patch_v59_isolated_test.py' not in wf
assert wf.index('patch_v59_tv_readability.py') < wf.index('patch_v591_match_tvos_fonts.py') < wf.index('patch_v59_system_home.py') < wf.index('patch_v591_candidate_version.py')
print('PASS: V5.9.1 HOME candidate identity, version, patch order, and independent package')
