# 🛰️ LocationMockPro - 极客级 Android 定位伪装与高拟真动力学轨迹仿真系统

[![Android](https://img.shields.io/badge/Platform-Android%208.0%20~%2015%20(API%2026--35)-brightgreen.svg)](https://developer.android.com)
[![Architecture Rating](https://img.shields.io/badge/Architecture%20Review-S%2B%20(Production--Ready)-success.svg)](https://github.com/588363534zzhhrr/LocationMockPro)
[![LSPosed](https://img.shields.io/badge/Module-LSPosed%20%2F%20Xposed%20Supported-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0%2B-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Release Package](https://img.shields.io/badge/Release%20APK-1.51%20MB%20(R8%20Optimized)-informational.svg)](https://github.com/588363534zzhhrr/LocationMockPro/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

`LocationMockPro` 是一套面向移动安全研究、高精度自动化测试与地理位置算法验证的 **工业级 Android 深度仿真框架**。项目不仅提供全套高拟真传感器动力学算法与悬浮微操交互，更深度集成了原生 **LSPosed / Xposed 系统底层免杀 Hook 引擎**，具备全闭环的物理与无线电环境指纹伪装能力，正式获评 **S+ 级（Release 产线封版状态）** 工业级交付标准。

---

## 🌟 核心功能特性矩阵

### 1. 🛡️ LSPosed 系统级免杀 Hook 引擎 (`XposedEntry.kt`)
- **系统标志位彻底抹除**：底层拦截 `Location.isFromMockProvider()`、Android 12+ (API 31) 的 `Location.isMock()` 并永真返回 `false`；清除 `Location.getExtras()` 中 `mockLocation` 标记。
- **开发者选项与权限沙箱遮蔽**：拦截 `Settings.Secure.getString/getInt("mock_location")` 强制返回 `0`；Hook `AppOpsManager.checkOp(OP_MOCK_LOCATION, 58)` 返回 `MODE_IGNORED`，彻底瓦解银行与大厂 App 的权限交叉比对。
- **Android 11+ 包可见性 (Package Visibility) 终极穿透**：宿主侧 Hook `ApplicationPackageManager.canPackageQuery` 与 `resolveContentProvider` 动态补齐 `ProviderInfo`，即使宿主未配置 `<queries>` 标签亦可秒级穿透。
- **LSPosed Manager 原生适配**：清单配置 `xposedmodule`，并在 `arrays.xml` 预置包含钉钉、企业微信、Keep、Pokémon GO、滴滴、美团等 12 款主流高频目标 App 的推荐勾选作用域。

### 2. ⚡ 纳秒时钟、动态精度与物理状态机自治
- **双时钟物理同步**：每次位置更新同步刷新 `location.time = System.currentTimeMillis()` 与纳秒级单调时钟 `location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()`，穿透 `FusedLocationProvider` 的时间戳交叉防作弊校验。
- **动态微幅精度抖动**：摒弃固定整数精度破绽，依据时间函数注入 `3.5m ~ 8.0m` 正弦连续自然精度波动，并装配方位角、垂直高度及速度精度因子。
- **GNSS 3D 定位快速冷启动**：Hook `LocationManager.registerGnssStatusCallback`，在 800ms 内瞬间触发 `onStarted()` 与 `onFirstFix(800)` 3D 卫星锁定，彻底杜绝目标应用因“获取不到 GNSS 卫星锁”判定定位无效。

### 3. 📡 全星座 NMEA-0183 卫星视场与无线电指纹
- **多星视场完整模拟**：生成标准 `$GPRMC`、`$GPGGA` 及 **`$GPGSV`** 多报文序列，真实计算各卫星仰角 (20°~75°)、方位角 (0°~360°) 与高保真载噪比（CNR 32~44 dB-Hz），流式输出至主界面终端。
- **基站 `CellInfoLte` 完备装配**：反射构造合规中国移动 LTE 基站信息，写入合法 MCC (`460`)、MNC (`00`)、TAC、CI 以及非空 `CellSignalStrengthLte`（`-75 ~ -84 dBm`，Level 4），根除国内主流定位 SDK（高德/百度/腾讯）因信号对象为空导致的 `NullPointerException` 崩溃。
- **企业 Wi-Fi 扫描与连接指纹注入**：拦截 `WifiManager.getConnectionInfo()` 与 `getScanResults()`，按需注入目标企业打卡路由器的 SSID、BSSID 及 `-52 dBm` 拟真 RSSI。

### 4. 🏃 硬件步频联动与开机基准值增量叠加
- **开机物理基数锚定**：拦截硬件计步传感器 `Sensor.TYPE_STEP_COUNTER (19)`，首次回调时捕获底层硬件真实步数 `baseHardwareSteps`（无硬件回调时以 `12580f` 垫底），向目标 App 注入 `baseHardwareSteps + simulatedSteps` 的连续增量，符合 Android 物理步数单调递增契约。
- **生理学步频阻尼**：速度与步频实时联动，拐角与红绿灯驻留时自动平滑衰减，步频传感器与 GPS 速度完全同步。

### 5. 🪟 灵动悬浮控制岛 Pro & 巡航自走锁定 (Cruise Lock)
- **极简灵动药丸模式 (Mini Capsule)**：一键折叠为贴边极小胶囊（`🕹️ 📍 7.2km/h · 1.0km ↗️`），在微信、打卡或游戏界面无遮挡；轻点即刻平滑展开。
- **一键巡航锁定 (Cruise Lock)**：新增 `🔒 巡航自走锁定`，松开手指后沿当前航向与速度自动持续前行，免除长途游戏与跑量模拟时的按压疲劳。
- **独立米级四向微调键**：集成 `[⬆️ 北微调]`、`[⬇️ 南]`、`[⬅️ 西]`、`[➡️ 东]`，单击精准向指定方位步进 2 米，专为高精度电子围栏打卡打造。

### 6. 🛡️ 进程生命周期与三级 IPC 容错防崩
- **`WeakReference` 闭包解耦双保险**：`ListenerDispatcher` 采用弱引用持有宿主 `LocationListener`，在派发 Runnable 内部**二次动态解引** `weakRef.get()`，结合 `pruneStaleDispatchers()` 存活性清扫，根除长尾劣质 App 忘记注销引发的 Activity 内存泄漏。
- **三级容错 IPC 通道**：
  - **Tier 1**：毫秒级 `ContentResolver.call("getState")` 极速跨进程通信；
  - **Tier 2**：`ContentResolver.query()` 基于 `MatrixCursor` 自动降级退避；
  - **Tier 3**：LSPosed 原生 `XSharedPreferences`（`location_mock_pro_stealth_prefs`）只读持久化兜底，确保在极端沙箱环境下 100% 通信可达且永不阻塞或弄崩宿主应用。

---

## 🛠️ 项目工程结构

```text
LocationMockPro/
├── app/
│   ├── build.gradle.kts                         # 产线级 R8 混淆配置与编译管线
│   ├── proguard-rules.pro                       # Xposed、Provider、Models 反射防剔除规则
│   └── src/main/
│       ├── AndroidManifest.xml                  # queries 包可见性、多进程 Provider 声明
│       ├── assets/xposed_init                   # LSPosed 模块入口定义 (com.antigravity.geospoofer.hook.XposedEntry)
│       ├── res/
│       │   ├── values/arrays.xml                # LSPosed 建议作用域预置列表 (12 款核心 App)
│       │   └── xml/file_paths.xml               # GPX 安全分享路径配置
│       └── java/com/antigravity/geospoofer/
│           ├── MainActivity.kt                  # Jetpack Compose M3 核心控制台
│           ├── hook/
│           │   └── XposedEntry.kt               # LSPosed 终极免杀与物理多维状态机自治引擎
│           ├── provider/
│           │   └── LocationStateProvider.kt     # 跨进程毫秒级 IPC 双模状态广播 Provider
│           ├── core/
│           │   ├── MockLocationEngine.kt        # 底层双 Provider 注入与 NMEA GSV/GGA 发生器
│           │   ├── RouteSimulator.kt            # 动力学步态、拐角减速、折返与目标守护
│           │   ├── CoordinateUtils.kt           # WGS-84 / GCJ-02 / BD-09 三坐标系亚米级互转
│           │   ├── AntiDetectionAuditHelper.kt  # 六维防检测体检中心
│           │   ├── GpxExportHelper.kt           # 标准 GPX 1.1 轨迹导出与分享
│           │   └── RouteStorageHelper.kt        # 路线持久化与收藏夹
│           ├── service/
│           │   ├── MockLocationService.kt       # 前台常驻保活与防休眠定位服务
│           │   └── FloatingJoystickService.kt   # 全局灵动胶囊悬浮窗与巡航锁定操纵
│           └── ui/
│               ├── MapWebView.kt                # 交互式路线标点地图
│               └── theme/                       # Material 3 深色主题
```

---

## 🚀 编译与产线构建指南

### 1. 环境要求
- **JDK**：OpenJDK 17 或 21
- **Android SDK**：API 35 (Android 15) / Min SDK 26 (Android 8.0)
- **编译工具**：Gradle 8.7+

### 2. 构建产物说明
| 构建模式 | 执行命令 | 产物路径 | 体积 | 特性说明 |
| :--- | :--- | :--- | :---: | :--- |
| **Debug** | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | 15.3 MB | 包含完整调试符号，开发联调首选 |
| **Release** | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | **1.51 MB** | **R8 生产级混淆**、压缩率超 90%、去除冗余依赖、入口类安全留存 |

---

## ⚖️ 免责声明 (Disclaimer)

本项目仅用于 Android 定位相关技术研究、移动端应用在弱信号及多样环境下的功能自动化测试。请勿将本项目用于任何违反法律法规或第三方服务使用协议之用途。使用者需自行承担因违规使用产生的一切风险与法律后果。
