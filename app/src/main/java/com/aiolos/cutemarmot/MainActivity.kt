package com.aiolos.cutemarmot

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aiolos.cutemarmot.profile.StealthProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var authenticationInProgress = false
    private var suppressAutoLockForExternalFlow = false

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
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            if (suppressAutoLockForExternalFlow) {
                suppressAutoLockForExternalFlow = false
            } else {
                currentMode = AppMode.NORMAL_FAKE
            }
        }
    }

    fun markLaunchingExternalSubFlow() {
        suppressAutoLockForExternalFlow = true
    }

    private fun handleIntent(intent: Intent?) {
        val triggerSource = intent?.getStringExtra("EXTRA_TRIGGER_SOURCE")
        if (triggerSource == "SECRET_CODE") {
            if (authenticationInProgress) return
            val requestedProfile = intent.getStringExtra(EXTRA_PROFILE_ID)
            if (requestedProfile != null && requestedProfile !in StealthProfileManager.PROFILE_IDS) {
                intent.removeExtra(EXTRA_PROFILE_ID)
                intent.removeExtra("EXTRA_TRIGGER_SOURCE")
                return
            }
            intent.removeExtra(EXTRA_PROFILE_ID)
            intent.removeExtra("EXTRA_TRIGGER_SOURCE")
            // 外部暗码唤醒或 Intent 调用时，强制调起系统级生物识别/锁屏凭据，杜绝任何免密越权！
            window.decorView.post {
                requestUnlockToStealthVault(requestedProfile)
            }
        }
    }

    /**
     * 统一安全入口：无论从高仿设置界面触发、还是通过暗码/外部Intent唤醒，均必须通过强身份验证
     */
    private fun requestUnlockToStealthVault(profileId: String? = null) {
        if (authenticationInProgress) return
        authenticationInProgress = true
        currentMode = AppMode.NORMAL_FAKE
        biometricHelper.authenticate(
            title = "设备安全校验",
            subtitle = if (profileId == null) "验证指纹、面容或锁屏密码以进入高级维护空间"
                else "验证身份以应用方案 ${profileId.substringAfterLast('_')}，这可能解除应用锁或 USB 防护",
            onSuccess = {
                // Capture this request, never read a newer Intent from an old success callback.
                val appContext = applicationContext
                // Once authorized, rotation must not interrupt a partially applied profile.
                val application = profileApplyScope.async {
                    if (profileId != null) profileApplyMutex.withLock {
                        val manager = StealthProfileManager(appContext)
                        manager.getProfiles().find { it.id == profileId }?.let {
                            manager.applyProfile(it)
                        }
                    }
                }
                lifecycleScope.launch {
                    try {
                        application.await()
                        currentMode = AppMode.STEALTH_VAULT
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, "方案应用失败，请重新验证后重试", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        authenticationInProgress = false
                    }
                }
            },
            onError = { authenticationInProgress = false },
            onFallback = {
                authenticationInProgress = false
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

    companion object {
        const val EXTRA_PROFILE_ID = "EXTRA_PROFILE_ID"
        private val profileApplyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val profileApplyMutex = Mutex()
    }
}
