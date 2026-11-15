#!/usr/bin/env python3
"""V5.8 post-patch checks; structural checks do not replace Android SDK build."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'launcher/app/src/main/java/com/gathin/tvhome'

def read(name): return (JAVA/name).read_text(encoding='utf8')

class ManualRefresh(unittest.TestCase):
    def test_separate_focusable_button(self):
        s=read('AppleRadioUi57.java')
        self.assertIn('refresh.setText("刷新")',s)
        self.assertIn('refresh.setFocusable(true)',s)
        self.assertIn('refresh.setOnClickListener(v -> manualRefresh.run())',s)
        self.assertIn('refreshLp.leftMargin',s)
        self.assertIn('header.addView(row, new LinearLayout.LayoutParams(0, dp(a, 43), 1f))',s)
    def test_wifi_manual_hook(self):
        s=read('AppleWifiQuickActivity56.java')
        self.assertIn('"无线网络", this::manualRefresh',s)
        self.assertIn('private void manualRefresh()',s)
        self.assertIn('boolean started = wifi.startScan()',s)
        self.assertIn('refreshList(); // show what is already saved',s)
        self.assertIn('if (!wifi.isWifiEnabled())',s)
        self.assertIn('WifiManager.EXTRA_RESULTS_UPDATED',s)
    def test_wifi_auto_continues(self):
        s=read('AppleWifiQuickActivity56.java')
        self.assertIn('silentScan();',s)
        self.assertIn('handler.postDelayed(poll,45000L)',s)
        self.assertIn('wifi.getScanResults()',s)
        self.assertIn('addRow(current,"已连接"',s)
    def test_bluetooth_hook(self):
        s=read('AppleBluetoothQuickActivity56.java')
        self.assertIn('"蓝牙",this::manualRefresh',s)
        self.assertIn('private void manualRefresh()',s)
        self.assertIn('adapter.cancelDiscovery()',s)
        self.assertIn('boolean started=adapter.startDiscovery()',s)
        self.assertIn('manualSearching=true;',s)
        self.assertIn('ACTION_DISCOVERY_FINISHED',s)
    def test_bluetooth_keep_paired(self):
        s=read('AppleBluetoothQuickActivity56.java')
        self.assertIn('adapter.getBondedDevices()',s)
        self.assertIn('if (started) {\n                discovered.clear();',s)
        self.assertIn('if (!isOn()) { hint.setText',s)
        self.assertIn('关闭蓝牙？',s)
    def test_uninstall_and_focus_unchanged(self):
        self.assertIn('CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION',read('AppLibraryDialog.java'))
        self.assertIn('tile.setScaleX(1f)',read('AppleControlCenterActivity.java'))
    def test_network_icon_unchanged(self):
        self.assertIn('AppleLinkStatus57.read(activity)',read('AppleTopStatus53.java'))
    def test_no_radio_toggle_on_refresh(self):
        for name in ('AppleWifiQuickActivity56.java','AppleBluetoothQuickActivity56.java'):
            s=read(name)
            start=s.index('private void manualRefresh()')
            end=s.index('\n    private ',start+1)
            self.assertNotIn('setWifiEnabled(',s[start:end])
            self.assertNotIn('adapter.disable()',s[start:end])
            self.assertNotIn('adapter.enable()',s[start:end])

if __name__=='__main__': unittest.main(verbosity=2)
