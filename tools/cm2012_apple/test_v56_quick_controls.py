#!/usr/bin/env python3
"""Structural smoke tests for the integrated CM2012 V5.6 Android sources."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'
MANIFEST = ROOT / 'launcher/app/src/main/AndroidManifest.xml'


def java(filename):
    return (JAVA / filename).read_text(encoding='utf-8')


class QuickControlsV56Test(unittest.TestCase):
    def test_manifest_well_formed(self):
        ET.parse(MANIFEST)

    def test_manifest_registered_local_activities(self):
        root = ET.parse(MANIFEST).getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        activities = root.findall('./application/activity')
        names = {a.get(android + 'name'): a.get(android + 'exported') for a in activities}
        self.assertEqual(names.get('.AppleWifiQuickActivity56'), 'false')
        self.assertEqual(names.get('.AppleBluetoothQuickActivity56'), 'false')

    def test_wifi_bluetooth_permissions(self):
        m = MANIFEST.read_text(encoding='utf-8')
        for name in ['CHANGE_WIFI_STATE','ACCESS_WIFI_STATE','ACCESS_FINE_LOCATION',
                     'BLUETOOTH','BLUETOOTH_ADMIN','BLUETOOTH_SCAN','BLUETOOTH_CONNECT']:
            with self.subTest(name=name):
                self.assertIn('android.permission.' + name, m)

    def test_system_uninstaller_is_single_confirm(self):
        lib = java('AppLibraryDialog.java')
        begin = lib.index('// CM2012_V56_ONE_SYSTEM_UNINSTALL_CONFIRMATION')
        end = lib.index('// CM2012_V522_APP_DETAILS', begin)
        sec = lib[begin:end]
        self.assertIn('Intent.ACTION_DELETE', sec)
        self.assertNotIn('AlertDialog.Builder', sec)
        self.assertEqual(lib.count('addAction(rows, R.string.cm2012_uninstall'), 1)
        self.assertIn('FLAG_SYSTEM', sec)
        self.assertIn('pkg.equals(activity.getPackageName())', sec)

    def test_top_wifi_opens_local_page(self):
        top = java('AppleTopMenu.java')
        self.assertIn('new Intent(a, AppleWifiQuickActivity56.class)', top)
        self.assertNotIn('fiberhome.intent.action.WIRELESS', top)
        self.assertIn('new Intent(this, AppleWifiQuickActivity56.class)', java('MainActivity.java'))

    def test_bluetooth_shortcut_opens_local_page(self):
        s = java('AppleTopStatus53.java')
        self.assertIn('new Intent(activity, AppleBluetoothQuickActivity56.class)', s)
        self.assertIn('The top icon now means Wi-Fi only', s)

    def test_wifi_starts_scan_and_registers_receiver(self):
        s = java('AppleWifiQuickActivity56.java')
        self.assertIn('wifi.startScan()', s)
        self.assertIn('SCAN_RESULTS_AVAILABLE_ACTION', s)
        self.assertIn('getScanResults()', s)
        self.assertIn('WIFI_STATE_CHANGED_ACTION', s)
        self.assertIn('requestPermissions', s)
        self.assertIn('isLocationEnabled()', s)
        self.assertIn('wifi.setWifiEnabled(wantOn)', s)
        self.assertIn('locationPermissionAsked', s)

    def test_bt_starts_discovery_and_lists_devices(self):
        s = java('AppleBluetoothQuickActivity56.java')
        self.assertIn('adapter.startDiscovery()', s)
        self.assertIn('BluetoothDevice.ACTION_FOUND', s)
        self.assertIn('getBondedDevices()', s)
        self.assertIn('device.createBond()', s)
        self.assertIn('BluetoothAdapter.ACTION_STATE_CHANGED', s)
        self.assertIn('adapter.cancelDiscovery()', s)
        self.assertIn('onStop()', s)
        self.assertIn('ensurePermissions()', s)
        self.assertIn('isLocationEnabled()', s)

    def test_control_center_links_quick_pages(self):
        s = java('AppleControlCenterActivity.java')
        self.assertIn('new Intent(this, AppleWifiQuickActivity56.class)', s)
        self.assertIn('new Intent(this, AppleBluetoothQuickActivity56.class)', s)
        self.assertIn('requestSafeUninstall(pkg);', s)
        self.assertNotIn('new AlertDialog.Builder(this).setTitle("卸载第三方应用")', s)

    def test_focus_rows_shrink_without_scale(self):
        s = java('AppleControlCenterActivity.java')
        self.assertIn('widthPixels / 10', s)
        self.assertNotIn('scaleX(focused ? 1.015f', s)
        self.assertIn('borderColor == 0xFF84D9FF ? 3 : 1', s)
        q = java('AppleQuickUi56.java')
        self.assertIn('setOnFocusChangeListener', q)
        self.assertNotIn('scaleX(', q)

    def test_workflow_includes_v56_patch(self):
        workflow = (ROOT / '.github/workflows/cm2012-apple-v5.yml').read_text(encoding='utf-8')
        self.assertIn('run: python3 tools/cm2012_apple/patch_v56_quick_controls.py', workflow)
        self.assertIn('-PlauncherApplicationId=com.cm2012.applehome', workflow)


if __name__ == '__main__':
    unittest.main(verbosity=2)
