package com.aiolos.cutemarmot.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

class UsbSecurityManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, StealthAdminReceiver::class.java)

    /**
     * 检测当前应用是否具备 Device Owner 权限
     */
    private fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * 禁用或允许通过 USB 数据线传输文件 (MTP/PTP 盘符)
     * @param disable true: 彻底禁止电脑连线读取手机文件 (只能充电，不显示盘符)
     */
    fun setUsbFileTransferDisabled(disable: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "非 Device Owner，无法配置 USB 文件传输策略")
            return false
        }
        return try {
            if (disable) {
                // 禁止 USB 文件传输 (电脑插线不弹出手机盘符)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                Log.i(TAG, "已开启：USB 文件传输已全面封锁")
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                Log.i(TAG, "已恢复：允许 USB 文件传输")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "配置 USB 传输限制失败: ${e.message}")
            false
        }
    }

    /**
     * 检查当前 USB 文件传输是否已被封锁
     */
    fun isUsbFileTransferDisabled(): Boolean {
        if (!isDeviceOwner()) return false
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)
    }

    companion object {
        private const val TAG = "UsbSecurityManager"
    }
}
