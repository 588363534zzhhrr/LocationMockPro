# 🛰️ LocationMockPro - 极客级 Android 定位伪装与高拟真动力学轨迹仿真系统

[![Android](https://img.shields.io/badge/Platform-Android%208.0%20~%2015%20(API%2026--35)-brightgreen.svg)](https://developer.android.com)
[![Architecture Rating](https://img.shields.io/badge/Architecture%20Review-S%2B%20(Production--Ready)-success.svg)](https://github.com/SyncPulseDev/LocationMockPro)
[![Version](https://img.shields.io/badge/Version-v1.2.3-blue.svg)](https://github.com/SyncPulseDev/LocationMockPro/releases)
[![LSPosed](https://img.shields.io/badge/Module-LSPosed%20%2F%20Xposed%20Supported-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0%2B-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Release Package](https://img.shields.io/badge/Release%20APK-1.49%20MB%20(R8%20Optimized)-informational.svg)](https://github.com/SyncPulseDev/LocationMockPro/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

`LocationMockPro` 是一套面向移动安全研究、高精度自动化测试与地理位置算法验证的 **工业级 Android 深度仿真框架**。项目不仅提供全套高拟真传感器动力学算法与悬浮微操交互，更深度集成了原生 **LSPosed / Xposed 系统底层免杀 Hook 引擎**，具备全闭环的物理与无线电环境指纹伪装能力，正式获评 **S+ 级（Release 产线封版状态）** 工业级交付标准。

---

## 🌟 核心功能特性矩阵

### 1. 🛡️ LSPosed 系统级免杀 Hook 引擎 (`XposedEntry.kt`)
- **系统标志位彻底抹除**：底层拦截 `Location.isFromMockProvider()`、Android 12+ (API 31) 的 `Location.isMock()` 并永真返回 `false`；清除 `Location.getExtras()` 中 `mockLocation` / `isMock` 标记。
- **全星历卫星 Extras 深度补齐**：注入 Location 时自动附带 `satellites=16`、`maxSatellites=28`、`meanCn0=33.5f`、`numSatellitesInView=16` 等全套卫星状态参数，彻底杜绝大厂安全 SDK 依赖“0 卫星/空 Extras”判定作弊。
- **开发者选项与权限沙箱遮蔽**：拦截 `Settings.Secure.getString/getInt("mock_location")` 强制返回 `0`；Hook `AppOpsManager.checkOp(OP_MOCK_LOCATION, 58)` 返回 `MODE_IGNORED`，彻底瓦解银行与大厂 App 的权限交叉比对。
- **Android 11+ 包可见性 (Package Visibility) 终极穿透**：宿主侧 Hook `ApplicationPackageManager.canPackageQuery` 与 `resolveContentProvider` 动态补齐 `ProviderInfo`，即使宿主未配置 `<queries>` 标签亦可秒级穿透。
- **LSPosed Manager 原生适配**：清单配置 `xposedmodule`，并在 `arrays.xml` 预置包含运动世界校园、闪动校园、钉钉、企业微信、Keep、Pokémon GO、滴滴、美团等主流高频目标 App 的推荐勾选作用域。

### 2. ⚡ 纳秒时钟、动态精度与物理状态机自治
- **双时钟物理同步**：每次位置更新同步刷新 `location.time = System.currentTimeMillis()` 与纳秒级单调时钟 `location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()`，穿透 `FusedLocationProvider` 的时间戳交叉防作弊校验。
- **动态微幅精度抖动**：摒弃固定整数精度破绽，依据时间函数注入 `3.5m ~ 8.0m` 正弦连续自然精度波动，并装配方位角、垂直高度及速度精度因子。
- **GNSS 3D 定位快速冷启动**：Hook `LocationManager.registerGnssStatusCallback`，在 800ms 内瞬间触发 `onStarted()` 与 `onFirstFix(800)` 3D 卫星锁定，彻底杜绝目标应用因“获取不到 GNSS 卫星锁”判定定位无效。

### 3. 📡 全星座 NMEA-0183 卫星视场与无线电指纹
- **多星视场完整模拟**：生成标准 `$GPRMC`、`$GPGGA` 及 **`$GPGSV`** 多报文序列，真实计算各卫星仰角 (20°~75°)、方位角 (0°~360°) 与高保真载噪比（CNR 32~44 dB-Hz），流式输出至主界面终端。
- **基站 `CellInfoLte` 完备装配**：反射构造合规中国移动 LTE 基站信息，写入合法 MCC (`460`)、MNC (`00`)、TAC、CI 以及非空 `CellSignalStrengthLte`（`-75 ~ -84 dBm`，Level 4），根除国内主流定位 SDK（高德/百度/腾讯）因信号对象为空导致的 `NullPointerException` 崩溃。
- **企业 Wi-Fi 扫描与连接指纹注入**：拦截 `WifiManager.getConnectionInfo()` 与 `getScanResults()`，按需注入目标企业打卡路由器的 SSID、BSSID 及 `-52 dBm` 拟真 RSSI。

### 4. 🏃 模拟器主动计步器仿真与开机基准增量叠加
- **无硬件计步设备/模拟器主动事件生成 (Active Sensor Dispatcher)**：
  - 拦截 `SensorManager.getDefaultSensor`，在**安卓模拟器（如 MuMu）**或无计步硬件设备上伪装注入标准的 **Bosch 硬件计步传感器**；
  - 维持 `activeStepListeners` 调度队列，在后台由独立调度线程按照真实跑步速度与步频主动合成合规的 `SensorEvent` 投递给宿主应用，彻底攻克模拟器“有位移但步数为零”被判定为骑车/作弊的风控痛点！
- **真机开机物理基数锚定**：真实设备上首次回调捕获底层硬件步数 `baseHardwareSteps`（无硬件回调时以 `12580f` 垫底），向目标 App 注入 `baseHardwareSteps + simulatedSteps` 的连续增量，符合 Android 物理步数单调递增契约。
- **生理学步频阻尼**：速度与步频实时联动，拐角与红绿灯驻留时自动平滑衰减，步频传感器与 GPS 速度完全同步。

### 5. 🏟️ 400m 标准田径场跑道与精细校准对齐引擎
- **IAAF 国际田联标准跑道数学几何重构**：严格按照国际田径联合会标准跑道参数构建（标准直道长 84.39m，第 1 道内突沿 36.50m / 计算跑线半径 36.80m，闭环周长精确为 400.0 米），彻底消除传统近似算法导致的椭圆畸变与位置偏移。
- **1~8 分道道次与顺/逆时针自适应**：支持 1~8 跑道半径自适应扩展（道宽 1.22m 增量计算），默认符合田联规范左转逆时针奔跑，亦可一键反转为顺时针。
- **跑道长轴航向旋转与四向米级对齐罗盘**：独家研发可视化跑道校准器（`Track400mDialog`），支持 0~360° 航向角平滑滑块微调，结合上/下/左/右四向米级步进罗盘（1m/5m/10m/20m），在卫星地图底图上实现像素级精准贴合。
- **地图视野中心感知与车标火星坐标纠偏**：地图视野移动时实时同步视野中心点，未选点时智能锚定当前屏幕中心；路线模拟巡航时自动对车标位置进行 GCJ-02 动态纠偏，保证车标与高德/卫星航线绝对对齐贴合。
- **道路吸附 WGS-84 / GCJ-02 双向纠偏引擎**：OpenStreetMap OSRM 拓扑引擎在请求前先将 GCJ-02 转为 WGS-84，吸附返回后再转回 GCJ-02，彻底消除两点吸附在地图上的 500m~1km 剧烈偏移问题。

### 6. 🪟 灵动悬浮控制岛 Pro & 巡航自走锁定 (Cruise Lock)
- **极简灵动药丸模式 (Mini Capsule)**：一键折叠为贴边极小胶囊（`🕹️ 📍 7.2km/h · 1.0km ↗️`），在微信、打卡或游戏界面无遮挡；轻点即刻平滑展开。
- **一键巡航锁定 (Cruise Lock)**：新增 `🔒 巡航自走锁定`，松开手指后沿当前航向与速度自动持续前行，免除长途游戏与跑量模拟时的按压疲劳。
- **独立米级四向微调键**：集成 `[⬆️ 北微调]`、`[⬇️ 南]`、`[⬅️ 西]`、`[➡️ 东]`，单击精准向指定方位步进 2 米，专为高精度电子围栏打卡打造。
- **摇杆触控防抖重构**：将触摸监听移至外层固定容器，推杆时如引擎未就绪自动补齐启动，彻底杜绝相对坐标反馈引起的抖动。

### 7. 🛡️ 前台服务强保活与三级 IPC 容错防崩
- **前台服务强生命周期守护**：显式结合 `startForegroundService` 与 `bindService`，彻底解决切出后台/锁屏时 Activity 销毁导致模拟定位服务被系统回收的致命缺陷。
- **`WeakReference` 闭包解耦双保险**：`ListenerDispatcher` 采用弱引用持有宿主 `LocationListener`，在派发 Runnable 内部**二次动态解引** `weakRef.get()`，结合 `pruneStaleDispatchers()` 存活性清扫，根除长尾劣质 App 忘记注销引发的 Activity 内存泄漏。
- **三级容错 IPC 通道**：
  - **Tier 1**：毫秒级 `ContentResolver.call("getState")` 极速跨进程通信（250ms 高频刷新）；
  - **Tier 2**：`ContentResolver.query()` 基于 `MatrixCursor` 自动降级退避；
  - **Tier 3**：LSPosed 原生 `XSharedPreferences`（`location_mock_pro_stealth_prefs`）只读持久化兜底，确保在极端沙箱环境下 100% 通信可达且永不阻塞或弄崩宿主应用。

### 8. 🔄 智能在线自动更新与 Root 静默热升级 (`UpdateManager.kt`)
- **双通道云端版本检测**：基于 GitHub Releases API 与轻量 `update.json` 双协议检测，冷启动智能轻量探测，严格遵循 SemVer 语义化版本规范。
- **国内反代镜像极速下载 (gh-proxy)**：内置国内 CDN 镜像下载加速开关，具备镜像与官方源自动故障切换 (Failover) 与流式断点汇报。
- **Root 极速静默秒升 (Seamless Silent Upgrade)**：若设备具备 Root（KernelSU / Magisk / su），采用 `pm install -r` 实施无感热更新，无需用户点击系统确认框；非 Root 环境无缝降级调起系统 `PackageInstaller`。
- **现代暗黑仪表盘弹窗**：实时呈现发布日志、版本号、下载进度百分比、已下载/总计 MB，支持后台下载与异常重试。

---

## 🛠️ 项目工程结构

```text
LocationMockPro/
├── app/
│   ├── build.gradle.kts                         # 产线级 R8 混淆配置与编译管线 (v1.1.1)
│   ├── proguard-rules.pro                       # Xposed、Provider、Models 反射防剔除规则
│   └── src/main/
│       ├── AndroidManifest.xml                  # queries 包可见性、多进程 Provider 声明
│       ├── assets/xposed_init                   # LSPosed 模块入口定义 (com.antigravity.geospoofer.hook.XposedEntry)
│       ├── res/
│       │   ├── values/arrays.xml                # LSPosed 建议作用域预置列表 (14 款核心与校园 App)
│       │   └── xml/file_paths.xml               # GPX 安全分享路径配置
│       └── java/com/antigravity/geospoofer/
│           ├── MainActivity.kt                  # Jetpack Compose M3 核心控制台 (含400m操场与多坐标系)
│           ├── hook/
│           │   └── XposedEntry.kt               # LSPosed 终极免杀、主动计步仿真与物理状态机自治
│           ├── provider/
│           │   └── LocationStateProvider.kt     # 跨进程毫秒级 IPC 双模状态广播 Provider
│           ├── core/
│           │   ├── MockLocationEngine.kt        # 底层双 Provider 注入与 NMEA GSV/GGA 发生器
│           │   ├── RouteSimulator.kt            # 动力学步态、拐角减速、折返与目标守护
│           │   ├── CoordinateUtils.kt           # WGS-84 / GCJ-02 / BD-09 互转与标准400m操场生成
│           │   ├── RoadSnapHelper.kt            # 双向纠偏 OSRM 道路吸附规划器
│           │   ├── AntiDetectionAuditHelper.kt  # 六维防检测体检中心
│           │   ├── GpxExportHelper.kt           # 标准 GPX 1.1 轨迹导出与分享
│           │   └── RouteStorageHelper.kt        # 路线持久化与收藏夹
│           ├── service/
│           │   ├── MockLocationService.kt       # 前台常驻保活与防休眠定位服务
│           │   └── FloatingJoystickService.kt   # 全局灵动胶囊悬浮窗与平滑防抖操纵
│           └── ui/
│               ├── Components.kt                # 极客暗黑风玻璃卡片、呼吸状态胶囊、遥测指标磁贴
│               ├── SingleMockTab.kt             # 单点定位控制中心 (地图选点、快速城市、打卡收藏)
│               ├── RouteMockTab.kt              # 路线巡航控制台 (4-Grid 航空级遥测HUD、路点与预设管理)
│               ├── EngineDefenseTab.kt          # 极客引擎与安全防护中心 (Root/LSPosed、4Hz防跳、高斯抖动、Wi-Fi基站伪装)
│               ├── Dialogs.kt                   # 统一暗黑弹窗系统 (体检报告、基站伪装、防跳指南等)
│               ├── MapWebView.kt                # 交互式路线标点地图
│               └── theme/                       # 极客座舱暗黑主题 (Dark Precision Design System)
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
| **Debug** | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | 15.4 MB | 包含完整调试符号，开发联调首选 |
| **Release** | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | **1.49 MB** | **R8 生产级混淆**、压缩率超 90%、去除冗余依赖、入口类安全留存 |

---

## 📝 更新日志 (Changelog)

### v1.2.1 (Build 6) - 2026-11-04
- **🏟️ 400m 标准操场跑道算法纠偏与精细校准引擎**：
  - **IAAF 田联标准数学几何重构**：依据国际田联真实标准重新建模（直道 84.39m，第 1 道周长精准 400.0m），修复航向角旋转算法正负逆转导致的位置偏移缺陷；
  - **跑道全方位校准弹窗 (`Track400mDialog`)**：支持 0°~360° 航向角旋转滑块、四向米级微调罗盘（1m/5m/10m/20m）、1~8 道次切换、顺/逆时针方向与目标圈数总里程动态计算；
  - **地图视图中心感知与底图实时联动**：未选点时自动感知地图当前视野中心生成跑道，调整时在卫星底图上实时呈现预览；
  - **模拟运行车标 GCJ-02 坐标贴合纠偏**：解决火星坐标系下模拟运行中车标与高德图层及轨迹线轻微错位的问题。

### v1.2.0 (Build 5) - 2026-11-03
- **🎨 交互设计重构：极客座舱暗黑视觉体系 (Dark Precision Design System)**：
  - **三维功能分区解耦**：彻底告别以往单页十几个卡片堆叠滚动的杂乱布局，采用现代化分段导航栏划分三大独立主视图：
    - `📍 单点定位`：专一聚焦地图选点、坐标微调校准、常用打卡收藏与秒级瞬移；
    - `🛣️ 路线巡航`：静态规划（操场/环线/吸附/路点）与动态巡航彻底解耦；
    - `🛡️ 引擎安全`：整合 Root 提权、后台保活、4Hz 高频防跳、动力学物理仿真、Wi-Fi/基站伪装与 NMEA 终端；
  - **🚀 航空级 4-Grid 实时运动遥测 HUD**：巡航时动态呼出实时时速、累计里程、计步步频、仿真海拔爬升 4 大高斯毛玻璃指标磁贴；
  - **⚡ 动态呼吸状态胶囊 (Status Pill)**：顶栏实时感知【待命中 / 单点模拟中 / 路线巡航中 / 挂起暂停】状态并呈现动态微光呼吸灯；
  - **💎 统一材质与配色系统**：去除所有杂乱刺眼的混杂色块，全套重构为深邃冷灰底色（`#0B0F17`）、精密卡片表面（`#131B29`）与极客电光蓝/青色（`#38BDF8` / `#00E5FF`）高亮色系；
  - **📱 模块化架构**：将原 2600+ 行庞大巨石类彻底拆解为 `SingleMockTab`、`RouteMockTab`、`EngineDefenseTab` 与 `Dialogs` 模块，大幅提升流畅度与渲染帧率。

### v1.1.2 (Build 4) - 2026-11-02
- **🛰️ 高精度 GNSS 3D 卫星合成引擎 (Synthetic GnssStatus)**：
  - 彻底解决高德/百度地图定位 SDK 在虚拟机或室内因无法收到卫星回调报 `#1401: no enough satellites` 判定为“GPS信号弱”的痛点；
  - 采用 `GnssStatus.Builder` 合成 16 颗全绿卫星（12 颗 GPS + 4 颗北斗，全部标记 `usedInFix=true`，平均信噪比 36 dB-Hz），以 1Hz 频率向高德 SDK 主动推流；
  - 拦截 `addGpsStatusListener` 与 `getGpsStatus`，全覆盖老旧地图 SDK 版本；
  - 原生模拟位置模式下收敛精度至 `2.8m ~ 4.6m` 黄金区间（符合高德优良信号 $\le 5.0\text{m}$ 判定标准），并补全 Extras 卫星字段；
  - LSPosed 白名单补全【运动世界校园】官方正式主包名 `com.zjwh.android_wh_physicalfitness`。

### v1.1.1 (Build 3) - 2026-11-02
- **⚡ 架构加固**：
  - 修复 `MockLocationService` 后台保活缺陷，显式结合 `startForegroundService` 与 `bindService`，切出后台不被系统杀死；
  - 重构悬浮窗摇杆触控监听体系，彻底根治相对坐标正反馈引发的手指抖动跳跃；
  - 调优 LSPosed 跨进程读取缓存至 `250ms`，消除 1Hz 阶梯状移动卡顿。
- **🛡️ 模拟器计步与防风控增强**：
  - 新增模拟器与无计步芯片设备专属的**主动虚拟计步事件分发器（Active Sensor Dispatcher）**，解决 MuMu 等模拟器中“零步数”作弊判定；
  - 补齐 Location 注入实例中的卫星星历 `extras` 数据包（`satellites=16`、`numSatellitesInView=16` 等）；
  - LSPosed 作用域白名单扩充校园跑主流应用（运动世界校园、闪动校园）。
- **🛣️ 算法与交互**：
  - 新增 `400m` 标准田径场操场跑道一键生成算法，完美闭环；
  - 彻底解决道路吸附中 `GCJ-02 <-> WGS-84` 混淆造成的 500m~1km 航向漂移。

---

## ⚖️ 免责声明 (Disclaimer)

本项目仅用于 Android 定位相关技术研究、移动端应用在弱信号及多样环境下的功能自动化测试。请勿将本项目用于任何违反法律法规或第三方服务使用协议之用途。使用者需自行承担因违规使用产生的一切风险与法律后果。
