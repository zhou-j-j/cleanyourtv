# CM201-2 Apple Home V5.6 · UI 快捷控制修改

此覆盖包适用于用户上传的 `cleanyourtv-main.zip`（2026-10-31 快照），不是 `com.cm2012.tvsettings` 设置项目。

## 使用

1. 将此 ZIP 解压，并按照**目录结构覆盖** GitHub `zhou-j-j/cleanyourtv` 工程根目录下同名文件。不要仅把 ZIP 当成一个文件上传。
2. GitHub → Actions → **CM2012 Apple V5.6 - Wi-Fi Bluetooth and UI** → Run workflow。
3. 编译完成后下载 `CM2012-Apple-V5.6-AllInOne` 工件，解压得到 `app-release.apk`（实际名称以 Actions 为准）。
4. 确认具备可恢复的 HOME 桌面后，优先尝试 ADB 覆盖安装（保留桌面数据）：

   `adb -s 192.168.2.12:5555 install -r app-release.apk`

   如果遇到签名不一致 (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)，**不要直接卸载正在使用的桌面**；应先确认原厂/备用 HOME 可以启动并备份桌面设置，再决定是否卸载重装。

## 变更

- 应用库长按后选择「卸载应用」：不再出现额外的应用内确认框，直接调用 Android 系统卸载确认（系统自己显示取消/卸载）。保留长按菜单中的打开、加入首页、换图标、信息等功能。
- 顶栏 Wi-Fi 快捷入口：单独页面、开关、开启后自动扫描及显示附近热点，点击热点可选择连接；长按开关重新扫描。
- 顶栏蓝牙快捷入口：单独页面、开关、开启后自动搜索，显示已配对和新发现设备；可发起配对；长按开关重新搜索。关闭蓝牙前警告可能断开遥控器。
- 控制中心：条目左右留边约 10%，遥控器选中使用亮色描边，不再放大到边缘以免裁切。

## 说明和限制

- Android 9 Wi-Fi 热点扫描需要 `ACCESS_FINE_LOCATION` 和系统定位服务开启。蓝牙扫描通常也需要定位权限；权限拒绝时页面会提示。
- 关闭蓝牙可能让蓝牙遥控器失联，请先准备红外遥控器或 USB 键盘。
- Bluetooth 已配对设备不等于已连接：具体连接受固件蓝牙服务控制，本版只做列出与公开配对流程，不伪称任意连接成功。
- 系统卸载确认的文案由固件 PackageInstaller 决定，应用不会静默卸载。
- 在 Android 10+，应用无法直接切换 Wi-Fi 时会打开系统设置；本版重点针对你的 Android 9。
- 本次未修改 `com.cm2012.tvsettings` 项目、以太网 DHCP/静态 IP、原厂分区或 root 权限。

## 验证

- 原始源码依次运行 V4、V5、V5.2、V5.3、V5.4、V5.5、V5.6 补丁：通过。
- `test_v56_quick_controls.py`：11 项结构测试通过；再次运行 V5.6 补丁未重复修改。
- AndroidManifest XML 与 GitHub Actions YAML：可解析。
- 本机没有 Android SDK `android.jar`，所以**未在本地完成正式 Android APK 编译，也未实机测试**；必须以 GitHub Actions 的 `assembleRelease` 结果和你盒子的实机体验为准。
