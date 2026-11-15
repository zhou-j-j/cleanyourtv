#!/usr/bin/env python3
"""V5.9 layout + safety regression; does not substitute Android SDK compilation."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'launcher/app/src/main/java/com/gathin/tvhome'

def read(file): return (JAVA/file).read_text(encoding='utf8')
class TelevisionReadability(unittest.TestCase):
    def test_both_pages_use_shared_large_layout(self):
        w=read('AppleWifiQuickActivity56.java')
        b=read('AppleBluetoothQuickActivity56.java')
        self.assertIn('AppleRadioUi57.page(this)',w)
        self.assertIn('AppleRadioUi57.page(this)',b)
        self.assertIn('AppleRadioUi57.switchRow(this, page, "无线网络", this::manualRefresh)',w)
        self.assertIn('AppleRadioUi57.switchRow(this,page,"蓝牙",this::manualRefresh)',b)
        self.assertIn('AppleRadioUi57.item(this,list',w)
        self.assertIn('AppleRadioUi57.item(this,list',b)
    def test_content_width(self):
        s=read('AppleRadioUi57.java')
        self.assertIn('screenWidth * 88 / 100',s)
        self.assertIn('Math.min(dp(a, 1250)',s)
    def test_header_and_switch_sizing(self):
        s=read('AppleRadioUi57.java')
        self.assertIn('dp(a, 72)',s)
        self.assertIn('dp(a, 68)',s)
        self.assertIn('name.setTextSize(23)',s)
        self.assertIn('s.setScaleX(1.30f)',s)
        self.assertIn('s.setScaleY(1.30f)',s)
    def test_refresh_big_focusable_and_separate(self):
        s=read('AppleRadioUi57.java')
        for token in ['refresh.setText("刷新")','refresh.setTextSize(20)','refresh.setFocusable(true)','refreshLp.leftMargin','dp(a, 104)','refreshBackground(a, f)','refresh.setOnClickListener(v -> manualRefresh.run())']:
            self.assertIn(token,s)
    def test_rows_readable(self):
        s=read('AppleRadioUi57.java')
        for token in ['row.setMinimumHeight(dp(a, 64))','title.setTextSize(22)','info.setTextSize(18)','t.setTextSize(17)','signal.setBounds(0, 0, dp(a, 32), dp(a, 32))']:
            self.assertIn(token,s)
    def test_no_wifi_or_bluetooth_behavior_change(self):
        w=read('AppleWifiQuickActivity56.java')
        b=read('AppleBluetoothQuickActivity56.java')
        self.assertIn('wifi.startScan()',w)
        self.assertIn('handler.postDelayed(poll,45000L)',w)
        self.assertIn('adapter.startDiscovery()',b)
        self.assertIn('adapter.getBondedDevices()',b)
        self.assertIn('关闭蓝牙？',b)
        self.assertIn('adapter.cancelDiscovery()',b)
    def test_still_independent_of_system_home(self):
        self.assertIn('AppleLinkStatus57.read(activity)',read('AppleTopStatus53.java'))
        self.assertIn('CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION',read('AppLibraryDialog.java'))
        self.assertIn('tile.setScaleX(1f)',read('AppleControlCenterActivity.java'))
if __name__=='__main__': unittest.main(verbosity=2)
