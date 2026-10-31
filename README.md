# LocationMockPro - 极客级 Android 定位伪装与高拟真动力学轨迹仿真系统

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0%2B-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

`LocationMockPro` 是一套集成了 **高仿真传感器动力学**、**底层硬件级 NMEA 卫星流**、**防风控安全体检** 与 **全景交互式悬浮操纵** 的专业 Android 定位与运动仿真系统。

---

## 🌟 核心功能特性

### 1. 🛡️ 防检测与安全过检体检中心 (Stealth Engine)
- **多维度风险诊断**：覆盖 Android 官方 Mock API、系统 Priv-App 特权提供者、SU 二进制隐藏、高斯自然微抖动、路口驻留机制、体能疲劳阻尼 6 大安全向量。
- **LSPosed 核心免杀代码一键导出**：一键生成并复制标准 Xposed / LSPosed 隐藏 Hook 源码，在 Java 底层拦截 `Location.isFromMockProvider()`、`Location.isMock()` 与 `Settings.Secure.getString("mock_location")`。

### 2. 🛰️ NMEA-0183 硬件卫星数据流实时终端
- **真实硬件级串口卫星流仿真**：实时生成标准 `$GPRMC` 与 `$GPGGA` 校验和报文。
- **星历与信噪比拟真**：实时模拟 16~18 颗可见 GPS 卫星与 ~34.5 dB-Hz 高保真载噪比（CNR/SNR），使任何读取底层 GPS NMEA 原始数据流的打卡及风控程序均无法识别出虚拟环境。

### 3. 🌐 三坐标系原生即时换算 (Tri-Coordinate Converter)
- **零偏移转换**：原生支持 **百度坐标系 (BD-09)**、**高德/腾讯火星坐标系 (GCJ-02)** 与 **国际标准 GPS (WGS-84)** 之间的双向亚米级精准换算。
- **即选即用**：提供一键三选胶囊切换，无论从哪款地图复制粘贴经纬度，均能自动归一化注入。

### 4. ⛰️ 真实地形海拔起伏与累计爬升波形拟真 (Terrain Elevation Engine)
- **自然起伏波形**：基于双正弦动力学波动算法拟真真实地貌微坡度。
- **爬坡数据闭环**：动态计算即时海拔高度与正向累计爬升（`elevationGainMeters`），并同步写入 GPS 坐标与 NMEA `$GPGGA`，完美契合 Keep / Strava / 咕咚等运动软件的爬坡曲线审计。

### 5. 🪟 灵动微型悬浮控制岛 Pro (Floating HUD & Smart Joystick)
- **极简灵动药丸模式 (Mini Capsule)**：一键折叠为贴边极小胶囊（`🕹️ 📍 7.2km/h · 1.0km ↗️`），在微信、高德、打卡软件或游戏中无任何视线遮挡；轻点胶囊任意位置即可秒级展开完整控制台。
- **独立米级四向微调键 (Cardinal Micro-Nudges)**：在 360° 模拟摇杆上方集成 `[⬆️ 北微调]`、`[⬇️ 南]`、`[⬅️ 西]`、`[➡️ 东]` 独立按键，单击精准向指定方位步进 2 米，专为高精度电子围栏打卡与微操打造。
- **全维状态仪表盘**：集成时速调节、档位快捷轮换（步行/慢跑/骑行/驾车）、暂停继续与实时海拔显示。

### 6. 🎯 路线模式与目标里程自动化守护 (Target Goal Guard)
- **三大巡航模态**：
  - `🏁 单程终点驻留`：到达终点后进入平缓驻留守护。
  - `🔄 往返折返巡航`：到达路线终点后无缝反转路点掉头折返，彻底避免终点瞬移突变。
  - `🔁 闭环循环巡航`：经典环形闭环跑道不间断循环巡航。
- **目标里程平缓刹车阻尼**：支持预设 `[1 km | 3 km | 5 km | 10 km]` 目标，达成时执行 5 秒自然缓冲减速并进入终点原地常驻保护。

### 7. 📤 标准 GPX 1.1 轨迹一键导出与全平台分享
- 支持将当前绘制或已模拟行进的轨迹打包为标准 GPX 1.1 XML 文件（携带高精度 UTC ISO-8601 时间戳与海拔标签）。
- 通过 Android 系统 `FileProvider` 一键调起系统分享面板（支持微信、QQ、网盘、Strava、Keep 等应用导出互通）。

---

## 🛠️ 项目工程结构

```text
LocationMockPro/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml                  # 权限、服务与 FileProvider 声明
│       ├── res/xml/file_paths.xml               # GPX 安全分享路径配置
│       └── java/com/antigravity/geospoofer/
│           ├── MainActivity.kt                  # Jetpack Compose M3 主界面与交互
│           ├── core/
│           │   ├── MockLocationEngine.kt        # 底层双 Provider 注入与 NMEA 卫星流生成
│           │   ├── RouteSimulator.kt            # 动力学步态、拐角减速、折返与目标守护
│           │   ├── CoordinateUtils.kt           # WGS-84 / GCJ-02 / BD-09 三坐标系双向互转
│           │   ├── AntiDetectionAuditHelper.kt  # 六维防检测体检中心与 LSPosed 源码生成器
│           │   ├── GpxExportHelper.kt           # 标准 GPX 1.1 轨迹导出与系统分享引擎
│           │   ├── RouteStorageHelper.kt        # 路线持久化与 GPX 解析
│           │   ├── FavoritePlacesHelper.kt      # 常用预设地标收藏夹
│           │   └── RootHelper.kt                # Root 权限检测与 su 提权辅助
│           ├── service/
│           │   ├── MockLocationService.kt       # 前台常驻保活与防休眠定位服务
│           │   └── FloatingJoystickService.kt   # 全局灵动胶囊悬浮窗与方向微调摇杆
│           └── ui/
│               ├── MapWebView.kt                # 基于 Leaflet / 高德瓦片的可视化标点地图
│               └── theme/                       # Material 3 深色主题
```

---

## 🚀 快速开始与编译指南

### 1. 环境要求
- **JDK**：OpenJDK 17 或 21
- **Android SDK**：API 35 (Android 15)
- **编译工具**：Gradle 8.7+

### 2. 编译与安装
```bash
# 克隆仓库
git clone https://github.com/your-username/LocationMockPro.git
cd LocationMockPro

# 编译 Debug APK
gradlew assembleDebug

# 安装至手机或模拟器
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚖️ 免责声明 (Disclaimer)

本项目仅用于 Android 定位相关技术研究、移动端应用在弱信号及多样环境下的功能自动化测试。请勿将本项目用于任何违反法律法规或第三方服务使用协议之用途。使用者需自行承担因违规使用产生的一切风险与法律后果。
