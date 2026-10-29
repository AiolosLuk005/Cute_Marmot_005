# Cute_Marmot (土拨鼠隐私与防线系统)

`Cute_Marmot` 是一款专为 Android 12 - 15（已在 **Samsung Galaxy S23 Ultra SM-S9180** 及 S21/S22 系列实机深度适配）打造的高隐秘、系统级隐私防御与隐身应用。

项目创新采用 **双引擎特权架构（Shizuku 免Root特权 / Device Owner 设备所有者）**、**Accessibility 实时前台毫秒级拦截**、**AES-256-GCM 流式密柜** 以及 **BiometricPrompt 系统级原生凭据** 构筑起立体化主动防御体系。

---

## 🌟 核心功能一览

### 1. 👻 深度应用隐身 (App Hiding & Freezing)
- **双特权后端支持**：
  - **Shizuku 模式（主力机首选）**：无需清空手机账号、无需 Root，调用特权 `pm` 指令将目标 App（如社交、金融、视频等）从当前用户空间彻底卸载隐藏，桌面与应用列表完全蒸发，保留用户数据。
  - **Device Owner 模式（极客测试首选）**：利用 `setApplicationHidden` 系统级特权，彻底冻结并隐藏应用。
- **完全静默与隔离**：隐藏期间应用进程被系统级冻结，后台网络、广播接收、消息推送全面静默。
- **一键无损复原**：在 Cute Marmot 内部随时“一键恢复”，所有数据、聊天记录、登录态完好无损。

### 2. 🔒 原生凭据应用锁 (Real-time App Locker)
- **毫秒级前台拦截**：基于 Android Accessibility Service（无障碍监听），在桌面点击目标应用的瞬间（<20ms）无感阻断并弹出锁屏阻击层。
- **直接复用手机系统原生凭据**：
  - 无需额外设置或记忆第三方独立密码。
  - 自动调用系统原生 `BiometricPrompt`，支持 **超声波指纹、面容解锁** 或 **手机自身的锁屏 PIN 码/手势/数字密码**。
  - 用户在手机系统设置里更改锁屏密码，应用锁密码实时自动同步。
- **免频繁重复验证**：内置 60 秒临时通行证机制，切换应用不卡顿。

### 3. 🎭 全方位伪装与隐形入场 (Stealth Disguise & Dynamic Launch)
- **高仿系统设置 (Undercover Disguise)**：
  - 启动后默认呈现为与原生系统 1:1 复刻的“系统设置”页面（显示 Wi-Fi、蓝牙、电池、显示等真实状态）。
  - **暗门触发机制**：在系统设置界面连续点击“软件版本” 5 次，无感调出真机生物认证，验证通过后方可激活“地下”隐私中枢。
- **自毁式图标隐藏**：
  - 支持“隐藏自身桌面图标”，桌面图标直接蒸发，防翻查。
  - **暗号拨号呼出**：在系统电话拨号盘输入特权暗码 `*#*#8888#*#*`，即可直接呼出地下隐私控制面板。

### 4. 🛡️ USB 数据线物理防读取 & AES-256 隐私密柜
- **USB 传输物理通道封锁（Device Owner 特权）**：
  - 强制关闭系统级 MTP/PTP 媒体与文件传输协议。当手机连上电脑时，电脑端仅可充电，无法识别存储设备，防外接设备窥探与取证导出。
- **AES-256-GCM 独立文件加密柜**：
  - 支持将私密照片、视频、私密文档一键加密归档到沙盒隔离区。
  - **物理覆写碎纸**：原文件自动采用安全覆写碎纸机制物理粉碎，并实时通知 `MediaScanner` 清理系统相册与缩略图缓存，杜绝数据恢复。
  - **特权原位复原**：结合 Shizuku 特权，突破 Android 11+ Scoped Storage 限制，可随时将密柜文件精准原位还原。

### 5. 🎛️ 多套防御方案快照与一键秒切 (3 Profiles & Fast Switch)
- **支持保存 3 套个性化配置方案**：
  - 默认预设：**“日常办公”**、**“借机防查”**、**“终极隐身”**。
  - **自定义重命名**：支持用户随心重命名方案（如改为“出差过关”、“家庭聚会”）。
  - **一键配置快照**：在界面随手勾选完隐藏应用、加锁应用、USB 封锁及密柜隐匿开关后，点击“保存当前配置”，瞬间覆写更新到当前方案。
  - **一键应用与全维联动**：点击“⚡ 一键应用此方案”，系统将在后台一键联动执行：批量隐藏/解冻应用、批量更新应用加锁名单、切换 USB 数据线传输权限、密柜隐匿状态及自身桌面图标隐形。
- **拨号盘暗号盲切方案**：
  - 在手机系统拨号盘直接输入：
    - `*#*#1111#*#*` ➔ 瞬间切换至方案 1
    - `*#*#2222#*#*` ➔ 瞬间切换至方案 2
    - `*#*#3333#*#*` ➔ 瞬间切换至方案 3
    - `*#*#8888#*#*` ➔ 直接唤醒隐私控制中枢

---

## 🛠️ 技术栈与架构设计

- **开发语言 & 框架**：Kotlin 1.9+, Jetpack Compose, Material 3, AndroidX Biometric
- **特权双引擎**：
  - **Shizuku API (v13+)**：免 Root 特权 Binder 通信，无痛适用于日常主力机
  - **Device Policy Manager (DPM)**：Device Owner 级别系统物理控制
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

编译产物位于：`app/build/outputs/apk/debug/app-debug.apk`。

### 2. 安装与激活应用

#### 步骤一：安装主应用与授权悬浮窗
```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 授权悬浮窗覆盖权限（应用锁全屏拦截必备）
adb shell appops set com.aiolos.cutemarmot SYSTEM_ALERT_WINDOW allow

# 开启无障碍监听服务（可选，应用内也可手动引导开启）
adb shell settings put secure enabled_accessibility_services com.aiolos.cutemarmot/com.aiolos.cutemarmot.lock.AppLockAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

### 3. 选择特权模式（二选一）

#### 推荐方案 A：Shizuku 模式（⭐ 主力机推荐，免清除账号，免 Root）
1. 安装根目录下的 `shizuku.apk`（或从官网下载）：
   ```bash
   adb install -r shizuku.apk
   ```
2. 启动 Shizuku 守护服务：
   ```bash
   # 执行 Shizuku 启动脚本（手机连接电脑 ADB）
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. 为 Cute_Marmot 授予 Shizuku API 权限：
   ```bash
   adb shell pm grant com.aiolos.cutemarmot moe.shizuku.manager.permission.API_V23
   ```
   *也可以打开手机上的 Shizuku 应用 ➔ 在“已授权应用”中开启“系统高级设置”（Cute_Marmot）。*

#### 方案 B：Device Owner 模式（适合备用机/测试机）
*注意：激活前手机上需临时移除所有 Google / 三星等个人账号。*
```bash
adb shell dpm set-device-owner com.aiolos.cutemarmot/.admin.StealthAdminReceiver
```

---

## 📱 使用指南与暗码汇总

| 触发方式 / 暗码 | 作用 |
| :--- | :--- |
| **高仿设置连续点击 5 次“软件版本”** | 调出原生指纹/锁屏密码验证，进入地下隐私控制中枢 |
| `*#*#8888#*#*` | 拨号盘特权呼出：直接唤醒地下隐私控制中枢（图标隐藏时亦有效） |
| `*#*#1111#*#*` | 拨号盘盲切：一键切换至 **方案 1** |
| `*#*#2222#*#*` | 拨号盘盲切：一键切换至 **方案 2** |
| `*#*#3333#*#*` | 拨号盘盲切：一键切换至 **方案 3** |

---

## ⚠️ 免责声明

本项目技术方案仅供 Android 安全防御、个人隐私保护及技术研究学习使用，请遵守当地法律法规。
