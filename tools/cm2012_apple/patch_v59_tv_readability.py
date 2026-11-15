#!/usr/bin/env python3
"""Apply V5.9 after V5.8; read-only UI layout changes, no radio API changes."""
from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parents[2]
ASSET=Path(__file__).resolve().parent/'AppleRadioUi59.src'
TARGET=ROOT/'launcher/app/src/main/java/com/gathin/tvhome/AppleRadioUi57.java'
assert ASSET.is_file() and TARGET.is_file(), 'V5.8 UI must be present before V5.9'
source=ASSET.read_text(encoding='utf8')
assert 'refreshBackground' in source and 'signal.setBounds(0, 0, dp(a, 32), dp(a, 32))' in source
assert 'manualRefresh.run()' in source
shutil.copyfile(ASSET,TARGET)
print('V5.9 TV layout: 88% wide, 72dp header, 64dp rows, large type, large remote focus, clear refresh buttons')
print('Both Wi-Fi and Bluetooth use AppleRadioUi57; radio/ethernet state logic unchanged')
