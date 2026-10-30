package com.aiolos.cutemarmot.stealth

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricSecurityHelper(private val activity: FragmentActivity) {

    /**
     * 检查当前手机是否已经设置了指纹、面容或锁屏凭据（手势/PIN/密码）
     */
    fun canAuthenticateWithDeviceCredential(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 调起手机系统的指纹/面容/锁屏手势/PIN码认证
     * @param onSuccess 认证成功
     * @param onFallback 当设备从未设置过锁屏密码/指纹时的备选方案
     */
    fun authenticate(
        title: String = "系统安全身份验证",
        subtitle: String = "请验证手机指纹、面容或锁屏密码以继续",
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
        onError: () -> Unit = {}
    ) {
        if (!canAuthenticateWithDeviceCredential()) {
            Log.w(TAG, "设备尚未设置任何锁屏凭据或指纹，走备用校验通道")
            onFallback()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "身份认证成功（指纹/面容/锁屏凭据）")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "认证取消或失败: $errorCode, $errString")
                if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                    errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                    errorCode == 11
                ) {
                    onFallback()
                } else {
                    onError()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "指纹或密码比对不匹配")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    companion object {
        private const val TAG = "BiometricHelper"
    }
}
