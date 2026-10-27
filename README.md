# Cute_Marmot (土拨鼠隐私与防线系统)

`Cute_Marmot` 是一款专为 Android 12 - 15（已在 Samsung Galaxy S21 / S22 Ultra 系统实机环境深度适配）打造的高隐秘、系统级隐私防御与隐身应用。

采用 **Device Owner (设备所有者)**、**Accessibility 实时前台拦截**、**AES-256-GCM 流式密柜** 以及 **BiometricPrompt 系统级原生认证** 构筑多维防御体系。

---

## 🌟 核心功能一览

### 1. 👻 深度应用隐身 (App Hiding & Freezing)
- **系统级隐藏**：借助 Android Device Owner (`setApplicationHidden`) 特权，彻底从手机桌面启动器、应用抽屉以及系统“设置 -> 应用程序”列表中抹除目标 App（如 YouTube、Chrome 等）。
- **冻结与隔离**：隐藏期间应用进程被完全冻结，后台网络、广播、数据更新全面静默。
- **随时唤醒恢复**：在 Cute Marmot 内部支持随时“一键恢复显示”，保留所有应用数据与状态。

### 2. 🔒 原生凭据应用锁 (Real-time App Locker)
- **毫秒级前台拦截**：基于 Android Accessibility Service（无障碍监听），在桌面点击目标应用（如 YouTube）的瞬间（<20ms）无感阻断并弹出锁屏阻击层。
- **直接复用手机系统原生凭据**：
  - 无需额外设置或记忆第三方独立密码。
  - 自动调用系统原生 `BiometricPrompt`，支持 **超声波指纹、面容解锁** 或 **手机自身的锁屏 PIN 码/手势/数字密码**。
  - 用户只要在手机系统设置里更改锁屏密码，应用锁密码实时自动同步。
- **免频繁重复验证**：内置 60 秒临时通行证机制，切换应用不卡顿。

### 3. 🎭 全方位伪装与隐形入场 (Stealth Disguise & Dynamic Launch)
- **高仿系统设置 (Undercover Disguise)**：
  - 启动后默认呈现为与原生系统 1:1 复刻的“系统设置”页面。
  - **暗门触发机制**：在系统设置界面连续点击“软件版本”5 次，无感调出真机生物认证，验证通过后方可激活“地下”隐私中枢。
- **自毁式图标隐藏**：
  - 支持“隐藏自身桌面图标”，桌面图标直接蒸发，防翻查。
  - **暗号拨号呼出**：在系统电话拨号盘输入特权暗码 `*#*#8888#*#*`，即可直接呼出地下隐私控制面板。

### 4. 🛡️ USB 数据线物理防读取 & AES-256 隐私密柜
- **USB 传输物理通道封锁**：
  - 一键开启 USB 防御：强制关闭系统级 MTP/PTP 媒体与文件传输协议。当手机连上电脑时，电脑端仅可充电，无法识别存储设备，防外接设备窥探。
- **AES-256-GCM 独立文件加密柜**：
  - 支持将私密照片、视频、私密文档一键加密归档到沙盒隔离区。
  - **数据擦除与索引清洗**：原文件自动采用安全覆写碎纸机制物理粉碎，并实时通知 `MediaScanner` 清理系统相册与文件管理器缩略图缓存，杜绝 USB 线端痕迹恢复。

---

## 🛠️ 技术栈与架构设计

- **开发语言 & 框架**：Kotlin 1.9+, Jetpack Compose, Material 3, AndroidX Biometric
- **系统特权**：Device Policy Manager (Device Owner API)
- **前台感知**：AccessibilityService (WindowStateChanged 极速感知)
- **加密核心**：Android KeyStore + AES/GCM/NoPadding 硬件级密钥隔离
- **构建环境**：Gradle 8.7, JDK 17, Android SDK 35 (Android 15 Ready)

---

## 🚀 编译与部署指南

### 1. 编译 APK
在项目根目录下执行：
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
生成的 APK 文件位于：`app/build/outputs/apk/debug/app-debug.apk`。

### 2. 安装与激活 Device Owner (设备所有者权限)

1. **安装 APK**：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **激活设备所有者特权（无需 Root）**：
   *注意：激活前手机上不要登录 Google 账号/三星账号（如果是测试机可先退出，激活后再登录）*
   ```bash
   adb shell dpm set-device-owner com.aiolos.cutemarmot/.admin.StealthAdminReceiver
   ```

3. **激活无障碍拦截服务与悬浮窗权限**：
   ```bash
   # 开启无障碍拦截服务
   adb shell settings put secure enabled_accessibility_services com.aiolos.cutemarmot/com.aiolos.cutemarmot.lock.AppLockAccessibilityService
   adb shell settings put secure accessibility_enabled 1

   # 授权悬浮窗覆盖权限
   adb shell appops set com.aiolos.cutemarmot SYSTEM_ALERT_WINDOW allow
   ```

---

## 📱 使用指南

1. **进入隐私中枢**：打开应用 ➔ 点击“关于手机” ➔ 连续点击“软件版本” 5 次 ➔ 验证手机指纹或锁屏密码。
2. **应用隐藏**：在“📱 隐藏应用”列表中找到目标 App（如 YouTube），打开左侧“隐藏”开关，应用将立即从手机桌面完全消失。
3. **应用加锁**：打开右侧“加锁”开关，任何时候从桌面点击该 App，都会立刻唤起手机指纹/锁屏密码验证。
4. **图标隐藏**：在管理顶部开启“隐藏自身图标”， Cute Marmot 图标立即从桌面隐藏，后续可在拨号盘输入 `*#*#8888#*#*` 呼出。
5. **USB 物理防御**：在“🔒 文件与 USB 防御”中一键开启 USB 封锁，阻断数据线连接提取。

---

## ⚠️ 免责声明
本项目技术方案仅供 Android 安全防御、个人隐私保护及技术研究学习使用，请遵守当地法律法规。
