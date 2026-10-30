package com.aiolos.cutemarmot.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.aiolos.cutemarmot.shizuku.ShizukuManager

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    var isHidden: Boolean,
    var isLocked: Boolean = false
)

enum class HideBackendMode {
    DEVICE_OWNER,
    SHIZUKU,
    NONE
}

class AppHideManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, StealthAdminReceiver::class.java)
    private val prefs = context.getSharedPreferences("stealth_hide_manager_prefs", Context.MODE_PRIVATE)

    /**
     * 获取已被 Cute Marmot 记录为隐藏状态的包名集合
     */
    fun getHiddenPackages(): Set<String> {
        return prefs.getStringSet("hidden_packages_set", emptySet()) ?: emptySet()
    }

    private fun setHiddenPackageRecord(packageName: String, hidden: Boolean) {
        val current = getHiddenPackages().toMutableSet()
        if (hidden) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet("hidden_packages_set", current).apply()
    }

    /**
     * 检测当前应用是否已被授权为 Device Owner (设备所有者)
     */
    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检测 Shizuku 权限是否就绪
     */
    fun isShizukuReady(): Boolean {
        return ShizukuManager.isPermissionGranted()
    }

    /**
     * 检测当前是否具备隐藏/冻结应用能力（满足 Device Owner 或 Shizuku 任意一种即可）
     */
    fun canHideApps(): Boolean {
        return isDeviceOwner() || isShizukuReady()
    }

    /**
     * 获取当前生效的隐藏后端模式
     */
    fun getCurrentMode(): HideBackendMode {
        return when {
            isDeviceOwner() -> HideBackendMode.DEVICE_OWNER
            isShizukuReady() -> HideBackendMode.SHIZUKU
            else -> HideBackendMode.NONE
        }
    }

    /**
     * 隐藏/冻结目标 APP
     * @param packageName 目标 APP 的包名
     * @return true 表示成功
     */
    fun hideApp(packageName: String): Boolean {
        // 1. 若拥有 Device Owner 权限则优先走 DPM
        if (isDeviceOwner()) {
            try {
                val result = dpm.setApplicationHidden(adminComponent, packageName, true)
                Log.i(TAG, "DeviceOwner 模式隐藏应用 $packageName: $result")
                if (result) {
                    setHiddenPackageRecord(packageName, true)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "DeviceOwner 隐藏失败，尝试 Shizuku 备用: ${e.message}")
            }
        }

        // 2. 走 Shizuku 模式 (pm uninstall -k --user 0)
        if (isShizukuReady()) {
            val result = ShizukuManager.hideAppSync(packageName)
            Log.i(TAG, "Shizuku 模式隐藏应用 $packageName: $result")
            if (result) {
                setHiddenPackageRecord(packageName, true)
                return true
            }
        }

        Log.e(TAG, "无法隐藏应用：既不是 Device Owner，也没有 Shizuku 授权")
        return false
    }

    /**
     * 解除隐藏/恢复目标 APP
     * @param packageName 目标 APP 的包名
     * @return true 表示成功
     */
    fun unhideApp(packageName: String): Boolean {
        // 1. 若拥有 Device Owner 权限则优先走 DPM
        if (isDeviceOwner()) {
            try {
                val result = dpm.setApplicationHidden(adminComponent, packageName, false)
                Log.i(TAG, "DeviceOwner 模式恢复应用 $packageName: $result")
                if (result) {
                    setHiddenPackageRecord(packageName, false)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "DeviceOwner 恢复失败，尝试 Shizuku 备用: ${e.message}")
            }
        }

        // 2. 走 Shizuku 模式 (cmd package install-existing --user 0)
        if (isShizukuReady()) {
            val result = ShizukuManager.unhideAppSync(packageName)
            Log.i(TAG, "Shizuku 模式恢复应用 $packageName: $result")
            if (result) {
                setHiddenPackageRecord(packageName, false)
                return true
            }
        }

        Log.e(TAG, "无法恢复应用：既不是 Device Owner，也没有 Shizuku 授权")
        return false
    }

    /**
     * 检查某个应用当前是否已被隐藏
     */
    fun isAppHidden(packageName: String): Boolean {
        // 1. 检查 Device Owner 标志
        if (isDeviceOwner()) {
            try {
                if (dpm.isApplicationHidden(adminComponent, packageName)) return true
            } catch (_: Exception) {}
        }

        // 2. 检查系统真实组件状态（通过 disable-user 隐藏的包）
        try {
            val state = context.packageManager.getApplicationEnabledSetting(packageName)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                return true
            }
        } catch (_: Exception) {}

        // 3. 检查 Cute Marmot 维护的隐藏清单
        if (getHiddenPackages().contains(packageName)) {
            return true
        }

        return false
    }

    /**
     * 获取手机上所有安装的应用列表（过滤掉自身）
     */
    fun getInstalledApps(): List<AppInfoItem> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                PackageManager.MATCH_DISABLED_COMPONENTS
        val installed = pm.getInstalledApplications(flags)
        val list = mutableListOf<AppInfoItem>()

        for (app in installed) {
            if (app.packageName == context.packageName) continue // 排除自己

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appName = pm.getApplicationLabel(app).toString()
            val icon = try { pm.getApplicationIcon(app) } catch (e: Exception) { null }
            val isHidden = isAppHidden(app.packageName)
            val isLocked = com.aiolos.cutemarmot.lock.AppLockRepository(context).isAppLocked(app.packageName)

            list.add(
                AppInfoItem(
                    packageName = app.packageName,
                    appName = appName,
                    icon = icon,
                    isSystemApp = isSystem,
                    isHidden = isHidden,
                    isLocked = isLocked
                )
            )
        }

        // 排序：隐藏的置顶，其次按用户安装应用优先，再按名称排序
        return list.sortedWith(
            compareByDescending<AppInfoItem> { it.isHidden }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    companion object {
        private const val TAG = "AppHideManager"
    }
}
