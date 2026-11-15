#!/usr/bin/env python3
"""Apply V5.8 manual refresh after patch_v57_radio_signal.py; safe to rerun."""
from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
JAVA=ROOT/'launcher/app/src/main/java/com/gathin/tvhome'
files={
    'AppleRadioUi58.src':'AppleRadioUi57.java',
    'AppleWifiQuickActivity58.src':'AppleWifiQuickActivity56.java',
    'AppleBluetoothQuickActivity58.src':'AppleBluetoothQuickActivity56.java',
}
for source,dest in files.items():
    p=HERE/source
    if not p.is_file() or not (JAVA/dest).is_file():
        raise SystemExit('V5.8 missing source/target: '+source+' / '+dest)
    code=p.read_text(encoding='utf8')
    if 'manualRefresh' not in code and 'manualRefresh' not in source:
        raise SystemExit('V5.8 missing manual action in '+source)
    shutil.copyfile(p,JAVA/dest)
print('V5.8 OK: separate focusable refresh next to Wi-Fi and Bluetooth toggles')
print('V5.8 OK: on-demand Wi-Fi scan, BT discovery restart, existing/paired entries retained until refresh starts')
