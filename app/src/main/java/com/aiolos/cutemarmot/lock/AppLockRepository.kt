package com.aiolos.cutemarmot.lock

import android.content.Context
import android.content.SharedPreferences

class AppLockRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    // 内存中缓存当前解锁成功的应用包名，避免每次点进同一个应用都死循环弹指纹
    companion object {
        private var temporarilyUnlockedPackage: String? = null
        private var unlockTimestamp: Long = 0L

        fun setTemporarilyUnlocked(packageName: String) {
            temporarilyUnlockedPackage = packageName
            unlockTimestamp = System.currentTimeMillis()
        }

        fun isTemporarilyUnlocked(packageName: String): Boolean {
            if (temporarilyUnlockedPackage == packageName) {
                // 放行有效期：用户连续使用中，或离开未超过 60 秒
                if (System.currentTimeMillis() - unlockTimestamp < 60_000) {
                    return true
                }
            }
            return false
        }

        fun resetUnlockState() {
            temporarilyUnlockedPackage = null
            unlockTimestamp = 0L
        }
    }

    /**
     * 判断某个应用是否被锁定了
     */
    fun isAppLocked(packageName: String): Boolean {
        val lockedSet = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
        return lockedSet.contains(packageName)
    }

    /**
     * 设置某个应用加锁/解锁
     */
    fun setAppLocked(packageName: String, locked: Boolean) {
        val current = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    /**
     * 获取所有已加锁的应用包名集合
     */
    fun getLockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    private val KEY_LOCKED_PACKAGES = "locked_packages"
}
