package com.aiolos.cutemarmot.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aiolos.cutemarmot.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "收到暗码广播: $action, uri=${intent.data}")

        // 当用户在拨号盘输入暗码时直接拉起应用的主界面并标记为暗码直达
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TRIGGER_SOURCE", "SECRET_CODE")
        }
        context.startActivity(launchIntent)
    }

    companion object {
        private const val TAG = "SecretCodeReceiver"
    }
}
