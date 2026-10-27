package com.aiolos.cutemarmot.lock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var repository: AppLockRepository
    private var lastInterceptedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = AppLockRepository(this)
        Log.i(TAG, "AppLockAccessibilityService 无障碍服务已启动")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // 忽略自身界面和系统输入法/桌面启动器
        if (packageName == this.packageName ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.inputmethod.latin" ||
            packageName.contains("launcher")
        ) {
            return
        }

        // 检查该应用是否在锁定名单中
        if (repository.isAppLocked(packageName)) {
            // 检查当前是否已经通过了指纹/手势临时放行
            if (!AppLockRepository.isTemporarilyUnlocked(packageName)) {
                Log.i(TAG, "拦截受保护应用启动: $packageName -> 弹出指纹/密码验证")
                launchLockScreen(packageName)
            }
        }
    }

    private fun launchLockScreen(targetPackage: String) {
        val lockIntent = Intent(this, AppLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        startActivity(lockIntent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务中断")
    }

    companion object {
        private const val TAG = "AppLockService"

        /**
         * 辅助方法：检查系统的无障碍服务是否已经为我们开启
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${AppLockAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
