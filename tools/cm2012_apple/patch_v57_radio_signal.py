#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Apply V5.7 after V5.6. Safe to repeat, refuses unrecognized top status source."""
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
JAVA=ROOT/'launcher/app/src/main/java/com/gathin/tvhome'
TOP=JAVA/'AppleTopStatus53.java'

for name, destName in (('AppleWifiQuickActivity57.src','AppleWifiQuickActivity56.java'),
             ('AppleBluetoothQuickActivity57.src','AppleBluetoothQuickActivity56.java'),
             ('AppleRadioUi57.java','AppleRadioUi57.java'),
             ('AppleLinkStatus57.java','AppleLinkStatus57.java'),
             ('NetworkStatusDrawable57.src','NetworkStatusDrawable.java')):
    source=HERE/name
    if not source.is_file(): raise SystemExit('V5.7 missing source: '+str(source))
    dest=JAVA/destName
    if not dest.parent.is_dir(): raise SystemExit('V5.7 missing Java path')
    shutil.copyfile(source,dest)

text=TOP.read_text(encoding='utf8')
mark='// CM2012_V57_LINK_AWARE_ICON'
if mark not in text:
    signature='private void drawNetwork(TextView target)'
    if text.count(signature)!=1:
        raise SystemExit('V5.7 ERROR: expected V5.6 drawNetwork method')
    p=text.index(signature)
    b=text.index('{',p)
    depth=0
    end=None
    for i in range(b,len(text)):
        if text[i]=='{': depth+=1
        elif text[i]=='}':
            depth-=1
            if depth==0: end=i+1; break
    if end is None: raise SystemExit('V5.7 ERROR: method unclosed')
    replacement='''private void drawNetwork(TextView target) {
            // CM2012_V57_LINK_AWARE_ICON
            // Plugged eth0 takes priority even if wlan0 is simultaneously connected.
            // Green means a reported link, NOT guaranteed Internet access.
            AppleLinkStatus57 state = AppleLinkStatus57.read(activity);
            target.setText("");
            target.setGravity(Gravity.CENTER);
            target.setCompoundDrawablePadding(0);
            target.setCompoundDrawableTintList((ColorStateList)null);
            target.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    new NetworkStatusDrawable(activity.getResources().getDisplayMetrics().density,
                            state.mode, state.level, state.connected), null, null, null);
            target.setContentDescription(state.description + "，按确定键打开无线网络列表");
        }'''
    text=text[:p]+replacement+text[end:]
    TOP.write_text(text,encoding='utf8')
print('OK V5.7: Ethernet link has priority, Wi-Fi RSSI signal level, green when linked and grey when not')
print('OK V5.7: Wi-Fi and Bluetooth start with a single toggle plus immediately visible device list')
