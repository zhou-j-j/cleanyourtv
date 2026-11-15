#!/usr/bin/env python3
"""Use the actual CM2012-tvOS-Settings typography for V5.9.1 radio pages.
Run AFTER patch_v59_tv_readability and its regression check.
Both quick Activities rely on AppleRadioUi57, so they change together.
"""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[2]
assets=Path(__file__).resolve().parent/'AppleRadioUi591.src'
target=root/'launcher/app/src/main/java/com/gathin/tvhome/AppleRadioUi57.java'
assert assets.is_file() and target.is_file(), 'Apply V5.9 scripts first'
s=target.read_text('utf8')
assert 'name.setTextSize(23)' in s and 'title.setTextSize(22)' in s, 'Unexpected source: do not silently overwrite'
shutil.copyfile(assets,target)
print('V5.9.1 typography: 26sp bold header, 18sp refresh and item title, 13sp status/details. No network or Bluetooth behavior changes.')
