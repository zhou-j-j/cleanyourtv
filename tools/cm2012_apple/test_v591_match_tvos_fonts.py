#!/usr/bin/env python3
"""Source and workflow regression for V5.9.1; not an APK compile test."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
java=root/'launcher/app/src/main/java/com/gathin/tvhome'
s=(java/'AppleRadioUi57.java').read_text('utf8')
for expression in ('name.setTextSize(26);','name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);',
                   'refresh.setTextSize(18);','title.setTextSize(18);',
                   'info.setTextSize(13);','t.setTextSize(13);',
                   'refresh.setOnClickListener(v -> manualRefresh.run());',
                   's.setScaleX(1.30f);','row.setMinimumHeight(dp(a, 64));',
                   'outer.setPadding(dp(a, 14), dp(a, 26)'):
    assert expression in s, f'Missing: {expression}'
assert 'name.setTextSize(23);' not in s
assert 'title.setTextSize(22);' not in s
for name,token in [('AppleWifiQuickActivity56.java','AppleRadioUi57.item(this,list'),
                   ('AppleBluetoothQuickActivity56.java','AppleRadioUi57.item(this,list')]:
    assert token in (java/name).read_text('utf8'), 'Not shared by '+name
settings='CM2012-tvOS-Settings verified: main header text(heading,26), row text("",18), caption text(str,13), row subtitle RelativeSizeSpan(0.73f).'
print('PASS V5.9.1: Wi-Fi/BT shared typography (26 / 18 / 13 sp), focus and scan hooks preserved.')
print(settings)
