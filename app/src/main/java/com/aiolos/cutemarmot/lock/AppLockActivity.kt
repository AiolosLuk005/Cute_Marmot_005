package com.aiolos.cutemarmot.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.aiolos.cutemarmot.stealth.BiometricSecurityHelper
import com.aiolos.cutemarmot.ui.toBitmap

class AppLockActivity : FragmentActivity() {

    private lateinit var biometricHelper: BiometricSecurityHelper
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        biometricHelper = BiometricSecurityHelper(this)

        // 读取目标应用的名称与图标
        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "受保护的应用"
        }

        val appIcon = try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }

        // 界面刚渲染完毕，立即自动拉起系统指纹/手势/锁屏密码
        triggerBiometricAuth()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val bitmap = remember(appIcon) { appIcon?.toBitmap() }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = appName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "该应用已被安全锁定",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = { triggerBiometricAuth() }) {
                            Text("点击验证指纹 / 手势密码")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = { exitToHome() }) {
                            Text("取消并返回桌面")
                        }
                    }
                }
            }
        }
    }

    private fun triggerBiometricAuth() {
        biometricHelper.authenticate(
            title = "应用安全锁",
            subtitle = "验证手机指纹、面容或锁屏凭据以启动应用",
            onSuccess = {
                // 标记为临时放行，关闭锁屏，用户正常进入原应用
                AppLockRepository.setTemporarilyUnlocked(targetPackage)
                finish()
            },
            onFallback = {
                exitToHome()
            }
        )
    }

    private fun exitToHome() {
        // 退回系统桌面
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        exitToHome()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
