# CM201-2 Apple Home V5.9.1 | 系统 HOME 字体统一候选版

## 适用条件

适合已上传 V5.9 源码的 `zhou-j-j/cleanyourtv` GitHub 工程。**此覆盖包已包含 V5.9 系统 HOME 候选版补丁**，不必再次上传上一个 `SYSTEM HOME` 覆盖包。如果工程还没有 V5.9 基础源码，优先使用本次完整源码包，或先上传 V5.9 的完整源码/覆盖包。

## 字体来源及改动

直接参照随对话上传的 `CM2012-tvOS-Settings-main.zip` 的 `app/src/main/java/com/cm2012/tvsettings/MainActivity.java`：
- `header(...)`: `text(heading, 26, WHITE, true)` -> Wi-Fi/蓝牙顶栏名称为 **26sp、加粗**
- `row(...)`: `text("", 18, WHITE, false)` -> 设备/热点名称 **18sp**；“刷新” **18sp**
- `caption(...)`: `text(str, 13, MUTED, false)` -> 设备状态、提示 **13sp**
- Settings 的副标题还使用 `RelativeSizeSpan(0.73f)` 作用于 18sp 基准 (≈13.14sp)，本次统一取 13sp。

原 V5.9 开关倍率、刷新尺寸、列表行高、焦点描边、网络状态和扫描/配对逻辑均不更改。Android 使用 sp 单位，实际像素随系统字体缩放和密度而变化。

## GitHub 编译

1. 解压该 ZIP 到工程根目录，提交包含 `.github/workflows` 和 `tools/cm2012_apple` 的新文件。
2. Actions -> **CM2012 Apple V5.9.1 - SYSTEM HOME FONT MATCH** -> Run workflow。
3. 下载 artifact `CM2012-Apple-V5.9.1-SYSTEM-HOME-FONT-MATCH`，解压 APK。
4. 先用 `adb install -r app-release.apk` 在 `/data/app` 中测试，确认正常后再讨论移植到系统分区。

## 身份与安全

- 候选版包名: `com.cm2012.applehome.v59system`（不是 `v59test`、也不是原系统包名 `com.cm2012.applehome`）
- 候选版的 `MainActivity` 保留 `MAIN` / `HOME` / `LEANBACK_LAUNCHER` 注册；**不是平台签名**。
- 原系统桌面：`/system/app/AppleHome/AppleHome.apk`，不得在新桌面经多次重启验证前删除。
- 旧桌面用户数据与 HOME 默认关联不会随不同包名迁移。
- 已在本地跑通补丁链和源码检查；正式 SDK 编译及 `/system` 实机验证由 GitHub Actions/盒子完成。
