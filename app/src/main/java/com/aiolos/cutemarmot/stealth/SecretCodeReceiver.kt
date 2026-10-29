package com.aiolos.cutemarmot.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.aiolos.cutemarmot.MainActivity
import com.aiolos.cutemarmot.profile.StealthProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.data?.host ?: ""
        Log.i(TAG, "收到暗码广播: uri=${intent.data}, host=$host")

        when (host) {
            "1111", "2222", "3333" -> {
                // 拨号盘快捷盲切方案
                val profileId = when (host) {
                    "1111" -> "profile_1"
                    "2222" -> "profile_2"
                    else -> "profile_3"
                }
                val manager = StealthProfileManager(context)
                val targetProfile = manager.getProfiles().find { it.id == profileId }

                if (targetProfile != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        manager.applyProfile(targetProfile)
                        Toast.makeText(
                            context,
                            "🛡️ 已切换至防御方案：${targetProfile.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> {
                // 默认 8888 唤醒隐私中枢
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_TRIGGER_SOURCE", "SECRET_CODE")
                }
                context.startActivity(launchIntent)
            }
        }
    }

    companion object {
        private const val TAG = "SecretCodeReceiver"
    }
}
