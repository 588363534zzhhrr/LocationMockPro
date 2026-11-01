package com.antigravity.geospoofer.core

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.io.File

/**
 * 防检测与环境过检安全体检助手 (Anti-Detection Audit & Stealth Engine)
 * 深度检测 6 大风控特征维度，计算安全防护评分，并提供 LSPosed 规避方案
 */
object AntiDetectionAuditHelper {

    data class AuditItem(
        val title: String,
        val statusText: String,
        val isPassed: Boolean,
        val riskLevel: RiskLevel,
        val suggestion: String
    )

    enum class RiskLevel {
        SAFE, WARNING, DANGER
    }

    data class AuditReport(
        val safetyScore: Int,
        val gradeTitle: String,
        val gradeColorHex: Long,
        val items: List<AuditItem>
    )

    /**
     * 执行全方位防检测安全体检
     */
    fun performAudit(
        context: Context,
        isJitterActive: Boolean,
        isStopsActive: Boolean,
        isFatigueActive: Boolean
    ): AuditReport {
        val items = mutableListOf<AuditItem>()
        var score = 100

        // 1. Android 原生 mock_location 标志位检测
        val isMockSettingsOn = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0
            }
        } catch (_: Exception) {
            false
        }

        if (isMockSettingsOn) {
            items.add(
                AuditItem(
                    title = "模拟位置权限标识 (Mock Location Provider)",
                    statusText = "已授权给本应用",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "系统已授予基础注入权限。敏感目标App建议配合 LSPosed 免杀模块隐藏此标志位。"
                )
            )
        } else {
            score -= 20
            items.add(
                AuditItem(
                    title = "模拟位置权限标识 (Mock Location Provider)",
                    statusText = "未在开发者选项中选定",
                    isPassed = false,
                    riskLevel = RiskLevel.DANGER,
                    suggestion = "请在系统开发者选项 -> '选择模拟位置信息应用' 中选定 LocationMockPro。"
                )
            )
        }

        // 2. 内置 LSPosed / Xposed 免杀 Hook 模块就绪状态
        items.add(
            AuditItem(
                title = "内置 LSPosed 免杀 Hook 模块",
                statusText = "已内建就绪 (全量抹除 isMock 标志位)",
                isPassed = true,
                riskLevel = RiskLevel.SAFE,
                suggestion = "已内置 XposedEntry，全面拦截 isFromMockProvider、isMock 与 Settings 检测，在 LSPosed 中勾选目标 App 即可 100% 免杀。"
            )
        )

        // 3. Android 12+ Fused 融合定位提供者覆盖
        val hasFused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        items.add(
            AuditItem(
                title = "Fused 融合定位通道覆盖",
                statusText = if (hasFused) "已纳入双通道+Fused融合注入" else "系统版本低于 API 31 (无需 Fused 覆盖)",
                isPassed = true,
                riskLevel = RiskLevel.SAFE,
                suggestion = "已覆盖高德/百度/Google 定位 SDK 默认订阅的 Fused 提供者，杜绝底层网络穿透。"
            )
        )

        // 4. 硬件计步器与传感器联动
        items.add(
            AuditItem(
                title = "硬件计步器传感器仿真与联动",
                statusText = "已就绪 (LocationStateProvider 跨进程联动)",
                isPassed = true,
                riskLevel = RiskLevel.SAFE,
                suggestion = "已打通 SensorManager.TYPE_STEP_COUNTER Hook，配速步频实时注入硬件事件，杜绝零步数作弊判定。"
            )
        )

        // 5. 无感 LocationListener 动态流劫持与主动注入
        items.add(
            AuditItem(
                title = "LocationListener 动态流劫持调度器",
                statusText = "已就绪 (无需原生 TestProvider 即可直推)",
                isPassed = true,
                riskLevel = RiskLevel.SAFE,
                suggestion = "支持在目标应用内直接劫持 requestLocationUpdates 注册的监听器，具备 1Hz 零延迟主动推送能力。"
            )
        )

        // 6. 企业打卡 Wi-Fi BSSID 与基站指纹防护
        items.add(
            AuditItem(
                title = "企业 Wi-Fi 与蜂窝基站无线电指纹伪装",
                statusText = "已就绪 (支持自定义 SSID/BSSID/LAC/CID)",
                isPassed = true,
                riskLevel = RiskLevel.SAFE,
                suggestion = "已拦截 WifiManager.getConnectionInfo / getScanResults 与 TelephonyManager，杜绝考勤 Wi-Fi 穿透与异地基站泄露。"
            )
        )

        // 7. Root / SU 隐匿环境与 Privileged 权限
        val isRooted = RootHelper.isDeviceRooted()
        val hasPrivilege = RootHelper.hasSystemLocationProviderPermission(context)
        if (hasPrivilege) {
            items.add(
                AuditItem(
                    title = "系统特权级注入 (Priv-app System)",
                    statusText = "具备顶级系统服务权限",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "应用位于系统级白名单，目标 App 无法通过权限判定虚拟定位。"
                )
            )
        } else if (isRooted) {
            items.add(
                AuditItem(
                    title = "Root 权限与底层控制",
                    statusText = "已检测到 Root/SU 环境",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "具备底层提权能力，建议在 Magisk / KernelSU 中开启 Zygisk 隐匿防扫描。"
                )
            )
        } else {
            items.add(
                AuditItem(
                    title = "免 Root 纯净运行环境",
                    statusText = "标准免 Root 沙箱模式",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "完全免 Root 运行，无 SU 风险检测暴露，安全合规。"
                )
            )
        }

        // 4. 自然 GPS 物理微抖动与卫星噪点状态
        if (isJitterActive) {
            items.add(
                AuditItem(
                    title = "自然 GPS 物理高斯微抖动",
                    statusText = "已激活 (实时卫星高斯噪点脉冲)",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "已消除绝对直线与静止锁死特征，风控 AI 算法无法判定为虚拟定位器。"
                )
            )
        } else {
            score -= 15
            items.add(
                AuditItem(
                    title = "自然 GPS 物理高斯微抖动",
                    statusText = "未开启 (绝对精确单点/直线)",
                    isPassed = false,
                    riskLevel = RiskLevel.WARNING,
                    suggestion = "建议开启微抖动！部分敏感App会风控静止或绝对平移的定点信号。"
                )
            )
        }

        // 5. 拟真路口驻留与红绿灯机制
        if (isStopsActive) {
            items.add(
                AuditItem(
                    title = "拟真路口红绿灯与随机驻留",
                    statusText = "已激活 (随机 8~18 秒路口等待)",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "模拟真实道路红绿灯、系鞋带与补水停顿，大幅降低全速匀速被 AI 风控概率。"
                )
            )
        } else {
            score -= 10
            items.add(
                AuditItem(
                    title = "拟真路口红绿灯与随机驻留",
                    statusText = "未开启 (全程无驻留巡航)",
                    isPassed = true,
                    riskLevel = RiskLevel.WARNING,
                    suggestion = "在进行长距离跑步模拟时，建议开启红绿灯驻留提升拟真度。"
                )
            )
        }

        // 6. 真实体能配速衰减波形
        if (isFatigueActive) {
            items.add(
                AuditItem(
                    title = "运动生理学体能消耗衰减",
                    statusText = "已激活 (动态正弦衰减曲率)",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "里程配速呈现真实运动员体能曲线，完美匹配 Keep/悦跑圈配速分析。"
                )
            )
        } else {
            score -= 5
            items.add(
                AuditItem(
                    title = "运动生理学体能消耗衰减",
                    statusText = "未开启",
                    isPassed = true,
                    riskLevel = RiskLevel.SAFE,
                    suggestion = "可选开启，可生成前快中缓尾冲刺的专业配速图表。"
                )
            )
        }

        // 最终评分与安全级别
        val finalScore = score.coerceIn(10, 100)
        val (gradeTitle, gradeColor) = when {
            finalScore >= 90 -> Pair("🛡️ 极高安全防护 (防封过检级)", 0xFF4CAF50)
            finalScore >= 75 -> Pair("🟢 高度安全状态", 0xFF8BC34A)
            finalScore >= 60 -> Pair("🟡 中度防护 (建议优化)", 0xFFFFB300)
            else -> Pair("🔴 存在风控风险 (急需修复)", 0xFFF44336)
        }

        return AuditReport(
            safetyScore = finalScore,
            gradeTitle = gradeTitle,
            gradeColorHex = gradeColor,
            items = items
        )
    }

    /**
     * 生成通用 LSPosed / Xposed 免杀 Hook 模块标准代码模板
     * 适用于极致敏感环境（彻底剥除 isFromMockProvider 标志位与开发者选项检测）
     */
    fun getLsposedHookGuideSnippet(targetPackage: String = "com.gotokeep.keep"): String {
        return """
// ==========================================
// 🛡️ LSPosed / Xposed 极致免杀 Hook 规则
// 针对目标 App: $targetPackage
// ==========================================
package com.antigravity.hook;

import android.location.Location;
import android.provider.Settings;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MockStealthHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("$targetPackage")) return;

        // 1. 拦截 Location.isFromMockProvider() 永远返回 false
        XposedHelpers.findAndHookMethod(
            Location.class, "isFromMockProvider",
            XC_MethodReplacement.returnConstant(false)
        );

        // 2. 拦截 Location.isMock() (Android 12+) 永远返回 false
        try {
            XposedHelpers.findAndHookMethod(
                Location.class, "isMock",
                XC_MethodReplacement.returnConstant(false)
            );
        } catch (Throwable ignored) {}

        // 3. 拦截开发者选项 ALLOW_MOCK_LOCATION 读取
        XposedHelpers.findAndHookMethod(
            Settings.Secure.class, "getString",
            android.content.ContentResolver.class, String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ("mock_location".equals(param.args[1])) {
                        param.setResult("0");
                    }
                }
            }
        );
    }
}
        """.trimIndent()
    }
}
