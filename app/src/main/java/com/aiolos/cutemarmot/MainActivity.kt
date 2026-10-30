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
                                    requestUnlockToStealthVault()
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
            // 外部暗码唤醒或 Intent 调用时，强制调起系统级生物识别/锁屏凭据，杜绝任何免密越权！
            window.decorView.post {
                requestUnlockToStealthVault()
            }
        }
    }

    /**
     * 统一安全入口：无论从高仿设置界面触发、还是通过暗码/外部Intent唤醒，均必须通过强身份验证
     */
    private fun requestUnlockToStealthVault() {
        biometricHelper.authenticate(
            title = "设备安全校验",
            subtitle = "验证指纹、面容或锁屏密码以进入高级维护空间",
            onSuccess = {
                currentMode = AppMode.STEALTH_VAULT
            },
            onFallback = {
                // 如果设备未设置任何生物识别或锁屏密码，提示用户设置，绝不直接放行进入密柜
                android.widget.Toast.makeText(
                    this,
                    "为了您的数据安全，请先在系统设置中设置锁屏密码或指纹",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                currentMode = AppMode.NORMAL_FAKE
            }
        )
    }
}
