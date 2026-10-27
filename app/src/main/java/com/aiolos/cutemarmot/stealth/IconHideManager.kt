package com.aiolos.cutemarmot.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

class IconHideManager(private val context: Context) {

    private val pm = context.packageManager
    private val launcherAlias = ComponentName(context, "com.aiolos.cutemarmot.LauncherAlias")

    /**
     * 判断当前桌面图标是否已经被隐藏
     */
    fun isIconHidden(): Boolean {
        val state = pm.getComponentEnabledSetting(launcherAlias)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * 设置桌面图标是否隐藏
     * @param hidden true: 从桌面彻底隐形; false: 恢复桌面图标
     */
    fun setIconHidden(hidden: Boolean): Boolean {
        return try {
            val newState = if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            pm.setComponentEnabledSetting(
                launcherAlias,
                newState,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "更新桌面图标状态 hidden=$hidden 成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新桌面图标失败: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "IconHideManager"
    }
}
