#!/usr/bin/env python3
"""V5 additions to the established V4 patch: native App Library uninstall, weather, DreamService.
Runs only from the CleanYourTV repo root, *after* patch_controlcenter.py.
Idempotent where applicable, rejects unexpected upstream shape.
"""
from pathlib import Path
import shutil
import sys

root = Path.cwd()
base = root / 'launcher/app/src/main/java/com/gathin/tvhome'
manifest = root / 'launcher/app/src/main/AndroidManifest.xml'
strings = root / 'launcher/app/src/main/res/values/strings.xml'
here = Path(__file__).resolve().parent
for f in (base / 'AppLibraryDialog.java', manifest, strings, base / 'AppleTopMenu.java', base / 'AppleControlCenterActivity.java'):
    if not f.exists(): sys.exit('ERROR missing file; run V4 patch first: '+str(f))

# 1. Native AppLibrary long-OK action using the known working CM201-2 PackageInstaller.
p = base / 'AppLibraryDialog.java'
s = p.read_text('utf-8')
needle = '        int menuWidth = dp(220);'
newmenu = '''        // CM201-2: uninstall a third-party app from the existing App Library hold-OK menu.
        // Never offer uninstall for stock/system apps or the current HOME launcher.
        android.content.pm.ApplicationInfo ai = app.activityInfo.applicationInfo;
        boolean preinstalled = ai != null && (ai.flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        if (!preinstalled && !pkg.equals(activity.getPackageName())) {
            addAction(rows, R.string.cm2012_uninstall, () -> {
                dismissActions();
                new android.app.AlertDialog.Builder(activity)
                    .setTitle("卸载应用 · " + label(app))
                    .setMessage("卸载应用可能同时删除它保存的数据。将由 Android 再次确认是否卸载。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("进入系统卸载", (dialog, which) -> {
                        close();
                        android.net.Uri target = android.net.Uri.parse("package:" + pkg);
                        try {
                            Intent i = new Intent(Intent.ACTION_DELETE, target);
                            i.setClassName("com.android.packageinstaller",
                                    "com.android.packageinstaller.UninstallerActivity");
                            activity.startActivity(i);
                        } catch (Exception first) {
                            try { activity.startActivity(new Intent(Intent.ACTION_DELETE, target)); }
                            catch (Exception second) {
                                Toast.makeText(activity, "系统卸载窗口无法启动，请从控制中心卸载", Toast.LENGTH_LONG).show();
                            }
                        }
                    }).show();
            });
        }
'''
if 'R.string.cm2012_uninstall' not in s:
    if s.count(needle) != 1:
        sys.exit('ERROR upstream AppLibraryDialog showActions anchor changed; refusing blind edit')
    s = s.replace(needle,newmenu+needle,1)
    p.write_text(s,'utf-8')
    print('App Library: added long-press uninstall for third-party apps')
else: print('App Library uninstall already patched')

# 2. add Chinese menu label; strings.xml may have an XML preamble and comments.
s = strings.read_text('utf-8')
if 'name="cm2012_uninstall"' not in s:
    if '</resources>' not in s: sys.exit('ERROR cannot locate strings.xml root close')
    s = s.replace('</resources>', '    <string name="cm2012_uninstall">卸载应用</string>\n</resources>',1)
    strings.write_text(s,'utf-8')

# 3. Weather and saver stay in this same applicationId, no add-on package required.
for file in ('AppleWeatherActivity.java','AppleWeatherHomeChip.java','AppleAerialDream.java','AppleAerialSettingsActivity.java'):
    shutil.copyfile(here/file,base/file)
m = manifest.read_text('utf-8')
for perm in ('android.permission.REQUEST_DELETE_PACKAGES','android.permission.INTERNET'):
    if perm not in m:
        at = m.find('<application')
        if at < 0: sys.exit('ERROR missing manifest application element')
        m = m[:at] + '<uses-permission android:name="'+perm+'" />\n    '+m[at:]
if 'android:name=".AppleWeatherActivity"' not in m:
    add = '''        <activity android:name=".AppleWeatherActivity" android:exported="false"
            android:screenOrientation="landscape" />
        <activity android:name=".AppleAerialSettingsActivity" android:exported="false"
            android:screenOrientation="landscape" />
        <service android:name=".AppleAerialDream"
            android:label="CM2012 苹果航拍屏保"
            android:exported="true" android:permission="android.permission.BIND_DREAM_SERVICE">
            <intent-filter>
                <action android:name="android.service.dreams.DreamService" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </service>
'''
    if '</application>' not in m:sys.exit('ERROR cannot find manifest app closing tag')
    m = m.replace('</application>',add+'    </application>',1)
manifest.write_text(m,'utf-8')

# 4. Replace original placeholder links with real same-APK Activities.
p = base / 'AppleControlCenterActivity.java'; s = p.read_text('utf-8')
old = 'item("中文天气", "城市、温度、预报与空气质量；需要合并天气页面源码", () -> openOptional("com.gathin.tvhome.AppleWeatherActivity", "中文天气模块仍需合入主 APK"));'
new = 'item("中文天气", "内置中文城市搜索、七天预报、AQI、湿度与风速", () -> startActivity(new Intent(this, AppleWeatherActivity.class)));'
if old in s:s=s.replace(old,new,1)
elif new not in s:sys.exit('ERROR control-center weather placeholder changed')
old = 'item("航拍视频屏保", "屏保来源与设置；系统屏保管理后备入口", () -> openOptionalOrSetting("com.gathin.tvhome.AppleAerialSettingsActivity", Settings.ACTION_DREAM_SETTINGS));'
new = 'item("航拍视频屏保", "内置屏保设置、选择自己的 MP4 或视频链接", () -> startActivity(new Intent(this, AppleAerialSettingsActivity.class)));'
if old in s:s=s.replace(old,new,1)
elif new not in s:sys.exit('ERROR control-center saver placeholder changed')
p.write_text(s,'utf-8')

# 5. Add useful routes under original 3-pill Inputs menu; keep exact 3 top buttons.
p = base / 'AppleTopMenu.java'; s = p.read_text('utf-8')
anchor = '        addRow(a, panel, pop, "⌁   无线网络",'
if '内置天气' not in s:
    if s.count(anchor)!=1: sys.exit('ERROR AppleTopMenu inputs anchor changed')
    s=s.replace(anchor, '''        addRow(a, panel, pop, "☀   内置天气", "中文城市搜索、七天预报", () ->
                a.startActivity(new Intent(a, AppleWeatherActivity.class)));
        addRow(a, panel, pop, "▣   航拍屏保", "本机视频源与屏保设置", () ->
                a.startActivity(new Intent(a, AppleAerialSettingsActivity.class)));
'''+anchor,1)
    p.write_text(s,'utf-8')
# 6. Add a compact live weather card just beneath the existing clock; no fourth top-right pill.
p = base / 'MainActivity.java'; s = p.read_text('utf-8')
anchor = '                btnInputs, btnWifi, btnSettings);'
if 'AppleWeatherHomeChip.attach(this' not in s:
    if s.count(anchor) != 1: sys.exit('ERROR cannot find V4 header attachment in MainActivity')
    s = s.replace(anchor, anchor + '\n        AppleWeatherHomeChip.attach(this, (LinearLayout) findViewById(R.id.leftBlock));',1)
    p.write_text(s,'utf-8')
print('V5: native weather + home weather card, DreamService, App Library uninstall & routes ready')
