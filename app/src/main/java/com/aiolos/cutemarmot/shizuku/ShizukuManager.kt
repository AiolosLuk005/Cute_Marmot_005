package com.aiolos.cutemarmot.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val REQUEST_CODE_SHIZUKU = 9001

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku Binder received")
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku Binder dead")
        _isServiceRunning.value = false
        _hasPermission.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku permission result: $grantResult (granted=$granted)")
            _hasPermission.value = granted
        }
    }

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            updateState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners: ${e.message}")
        }
    }

    fun updateState() {
        val running = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
        _isServiceRunning.value = running

        val granted = if (running) {
            try {
                if (Shizuku.isPreV11()) {
                    false
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
        _hasPermission.value = granted
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            if (!isRunning()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (isRunning()) {
                if (!isPermissionGranted()) {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission: ${e.message}")
        }
    }

    /**
     * 同步执行 shell 命令（阻塞当前线程，需在 IO 线程调用）
     */
    fun execSync(cmd: Array<String>): ShizukuCommandResult {
        if (!isPermissionGranted()) {
            return ShizukuCommandResult(-1, "", "Shizuku 未授权或未运行")
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = newProcessMethod.invoke(null, cmd, null, null) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            ShizukuCommandResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku execSync error: ${e.message}")
            ShizukuCommandResult(-1, "", e.message ?: "未知异常")
        }
    }

    /**
     * 协程异步执行 shell 命令
     */
    suspend fun exec(cmd: Array<String>): ShizukuCommandResult = withContext(Dispatchers.IO) {
        execSync(cmd)
    }

    /**
     * 隐藏应用（通过 pm disable-user --user 0 彻底隐形，从桌面、应用抽屉与系统设置中完全消失，保留全部数据与APK）
     */
    fun hideAppSync(packageName: String): Boolean {
        var result = execSync(arrayOf("pm", "disable-user", "--user", "0", packageName))
        Log.i(TAG, "hideAppSync $packageName (attempt 1): exitCode=${result.exitCode}, out=${result.stdout}, err=${result.stderr}")
        if (result.isSuccess || result.stdout.contains("disabled", ignoreCase = true)) {
            return true
        }

        // 若因三星系统「深度休眠」(SecurityException) 导致首次失败，尝试唤醒并自动重试
        execSync(arrayOf("am", "set-standby-bucket", packageName, "active"))
        try {
            Thread.sleep(250)
        } catch (_: Exception) {}

        result = execSync(arrayOf("pm", "disable-user", "--user", "0", packageName))
        Log.i(TAG, "hideAppSync $packageName (attempt 2): exitCode=${result.exitCode}, out=${result.stdout}, err=${result.stderr}")
        return result.isSuccess || result.stdout.contains("disabled", ignoreCase = true)
    }

    /**
     * 取消隐藏/恢复应用（通过 pm enable --user 0 瞬间恢复到桌面）
     */
    fun unhideAppSync(packageName: String): Boolean {
        var result = execSync(arrayOf("pm", "enable", "--user", "0", packageName))
        Log.i(TAG, "unhideAppSync $packageName (attempt 1): exitCode=${result.exitCode}, out=${result.stdout}, err=${result.stderr}")
        if (result.isSuccess || result.stdout.contains("enabled", ignoreCase = true)) {
            return true
        }

        try {
            Thread.sleep(200)
        } catch (_: Exception) {}

        result = execSync(arrayOf("pm", "enable", "--user", "0", packageName))
        Log.i(TAG, "unhideAppSync $packageName (attempt 2): exitCode=${result.exitCode}, out=${result.stdout}, err=${result.stderr}")
        return result.isSuccess || result.stdout.contains("enabled", ignoreCase = true)
    }

    /**
     * 免手动跳转，通过 Shizuku 特权一键静默激活应用锁无障碍服务
     */
    fun enableAccessibilityServiceSync(packageName: String): Boolean {
        val serviceComponent = "$packageName/com.aiolos.cutemarmot.lock.AppLockAccessibilityService"
        val cmd = arrayOf("sh", "-c", "settings put secure enabled_accessibility_services $serviceComponent; settings put secure accessibility_enabled 1")
        val result = execSync(cmd)
        Log.i(TAG, "enableAccessibilityServiceSync: exitCode=${result.exitCode}, out=${result.stdout}")
        return result.isSuccess
    }
}

data class ShizukuCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}
