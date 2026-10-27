package com.aiolos.cutemarmot.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    var isHidden: Boolean,
    var isLocked: Boolean = false
)

class AppHideManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, StealthAdminReceiver::class.java)

    /**
     * 检测当前应用是否已被授权为 Device Owner (设备所有者)
     */
    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * 隐藏/冻结目标 APP
     * @param packageName 目标 APP 的包名
     * @return true 表示成功
     */
    fun hideApp(packageName: String): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "无法隐藏应用：当前应用并非 Device Owner")
            return false
        }
        return try {
            val result = dpm.setApplicationHidden(adminComponent, packageName, true)
            Log.i(TAG, "隐藏应用 $packageName: $result")
            result
        } catch (e: SecurityException) {
            Log.e(TAG, "隐藏应用权限不足: ${e.message}")
            false
        }
    }

    /**
     * 解除隐藏/恢复目标 APP
     * @param packageName 目标 APP 的包名
     * @return true 表示成功
     */
    fun unhideApp(packageName: String): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "无法解冻应用：当前应用并非 Device Owner")
            return false
        }
        return try {
            val result = dpm.setApplicationHidden(adminComponent, packageName, false)
            Log.i(TAG, "恢复应用 $packageName: $result")
            result
        } catch (e: SecurityException) {
            Log.e(TAG, "恢复应用失败: ${e.message}")
            false
        }
    }

    /**
     * 检查某个应用当前是否已被隐藏
     */
    fun isAppHidden(packageName: String): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            dpm.isApplicationHidden(adminComponent, packageName)
        } catch (e: Exception) {
            false
        }
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
