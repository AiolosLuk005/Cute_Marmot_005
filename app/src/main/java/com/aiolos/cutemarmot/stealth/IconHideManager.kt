package com.aiolos.cutemarmot.stealth

import android.content.Context

class IconHideManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)

    /**
     * 判断当前是否处于隐蔽伪装状态
     */
    fun isIconHidden(): Boolean {
        return prefs.getBoolean("icon_stealth_disguised", true)
    }

    /**
     * 设置桌面伪装状态（始终保持「系统高级设置」伪装，避免因组件禁用导致三星桌面闪退或打不开）
     */
    fun setIconHidden(hidden: Boolean): Boolean {
        prefs.edit().putBoolean("icon_stealth_disguised", hidden).apply()
        return true
    }
}
