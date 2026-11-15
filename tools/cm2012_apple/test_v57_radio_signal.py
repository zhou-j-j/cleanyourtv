#!/usr/bin/env python3
"""Structural V5.7 post-patch smoke tests (not a substitute for Android SDK compile)."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'launcher/app/src/main/java/com/gathin/tvhome'

def src(name): return (JAVA/name).read_text(encoding='utf8')

class V57(unittest.TestCase):
    def test_manifest(self):
        doc=ET.parse(ROOT/'launcher/app/src/main/AndroidManifest.xml')
        names=[a.attrib.get('{http://schemas.android.com/apk/res/android}name') for a in doc.findall('./application/activity')]
        self.assertIn('.AppleWifiQuickActivity56',names)
        self.assertIn('.AppleBluetoothQuickActivity56',names)
    def test_icon_eth_preference(self):
        s=src('AppleLinkStatus57.java')
        self.assertIn('carrier == 1 || (carrier == -1 && ethConnected)',s)
        self.assertIn('wifiNetwork = null',s)
        self.assertLess(s.index('carrier == 1 ||'),s.index('if (wifiConnected || wifiOn)'))
        self.assertIn('/sys/class/net/eth0/carrier',s)
    def test_indicator_green_gray(self):
        s=src('NetworkStatusDrawable.java')
        self.assertIn('ETHERNET_OK',s)
        self.assertIn('OFFLINE_GREY',s)
        self.assertIn('ethernetOnline ? ETHERNET_OK : OFFLINE_GREY',s)
        self.assertNotIn('ETHERNET_BAD',s)
    def test_header_uses_new_link_logic(self):
        s=src('AppleTopStatus53.java')
        start=s.index('private void drawNetwork(TextView target)')
        end=s.index('private void ensureBluetoothNextTo',start)
        piece=s[start:end]
        self.assertIn('AppleLinkStatus57.read(activity)',piece)
        self.assertIn('state.mode, state.level, state.connected',piece)
        self.assertNotIn('type = NetworkStatusDrawable.WIFI;',piece)
    def test_wifi_page_is_list_with_auto_refresh(self):
        s=src('AppleWifiQuickActivity56.java')
        self.assertIn('AppleRadioUi57.switchRow(this, page, "无线网络")',s)
        self.assertIn('refreshList(); // never wait',s)
        self.assertIn('wifi.getScanResults()',s)
        self.assertIn('silentScan();',s)
        self.assertNotIn('requestPermissions(',s)
        self.assertNotIn('onLongClick(',s)
    def test_wifi_connected_even_without_scan(self):
        s=src('AppleWifiQuickActivity56.java')
        self.assertLess(s.index('if (connected) {'),s.index('wifi.getScanResults()'))
        self.assertIn('wifi.getConfiguredNetworks()',s)
    def test_bt_pair_list_first_and_discovery(self):
        s=src('AppleBluetoothQuickActivity56.java')
        self.assertIn('adapter.getBondedDevices()',s)
        self.assertIn('adapter.startDiscovery()',s)
        self.assertIn('update();\n        discover();',s)
        self.assertNotIn('requestPermissions(',s)
    def test_bt_safety_confirmation(self):
        self.assertIn('关闭蓝牙？',src('AppleBluetoothQuickActivity56.java'))
    def test_ui_remote_focus(self):
        s=src('AppleRadioUi57.java')
        self.assertIn('row.setFocusable(true)',s)
        self.assertIn('setOnFocusChangeListener',s)
        self.assertIn('new LinearLayout.LayoutParams(0, -2, 1f)',s)
    def test_uninstall_still_works(self):
        s=src('AppLibraryDialog.java')
        self.assertIn('CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION',s)
        self.assertIn('Intent.ACTION_DELETE',s)
    def test_control_center_focus_still_fixed(self):
        s=src('AppleControlCenterActivity.java')
        self.assertIn('tile.setScaleX(1f)',s)
    def test_no_missing_java_files(self):
        for name in ['AppleRadioUi57.java','AppleLinkStatus57.java','NetworkStatusDrawable.java','AppleWifiQuickActivity56.java','AppleBluetoothQuickActivity56.java']:
            self.assertTrue((JAVA/name).is_file())

if __name__=='__main__': unittest.main(verbosity=2)
