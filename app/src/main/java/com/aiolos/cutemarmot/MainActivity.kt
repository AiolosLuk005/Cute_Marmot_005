package com.aiolos.cutemarmot

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.aiolos.cutemarmot.stealth.BiometricSecurityHelper
import com.aiolos.cutemarmot.ui.FakeSettingsScreen
import com.aiolos.cutemarmot.ui.PrivacyAppManagerScreen

enum class AppMode {
    NORMAL_FAKE,  // 正常伪装模式（高仿系统设置）
    STEALTH_VAULT // 安全隐私模式（真实应用隐藏控制中心）
}

class MainActivity : FragmentActivity() {

    private var currentMode by mutableStateOf(AppMode.NORMAL_FAKE)
    private lateinit var biometricHelper: BiometricSecurityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.aiolos.cutemarmot.shizuku.ShizukuManager.init()
        biometricHelper = BiometricSecurityHelper(this)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface {
                    when (currentMode) {
                        AppMode.NORMAL_FAKE -> {
                            FakeSettingsScreen(
                                onRequestBiometricAuth = {
                                    // 连击 5 次触发后，直接调用系统级指纹/手势/锁屏密码
                                    biometricHelper.authenticate(
                                        title = "设备安全校验",
                                        subtitle = "验证指纹、面容或锁屏密码以进入高级维护空间",
                                        onSuccess = {
                                            currentMode = AppMode.STEALTH_VAULT
                                        },
                                        onFallback = {
                                            // 若设备无生物识别则自动进入备选输入或直接放行
                                            currentMode = AppMode.STEALTH_VAULT
                                        }
                                    )
                                }
                            )
                        }
                        AppMode.STEALTH_VAULT -> {
                            PrivacyAppManagerScreen(
                                onLockBackToNormal = {
                                    currentMode = AppMode.NORMAL_FAKE
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val triggerSource = intent?.getStringExtra("EXTRA_TRIGGER_SOURCE")
        if (triggerSource == "SECRET_CODE") {
            // 暗码唤醒也可以直接解锁或者请求指纹
            currentMode = AppMode.STEALTH_VAULT
        }
    }
}
