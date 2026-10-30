package com.aiolos.cutemarmot.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiolos.cutemarmot.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SECRET_CODE" &&
            intent.action != "android.telephony.action.SECRET_CODE") return
        if (intent.data?.scheme != "android_secret_code") return
        val profile = when (intent.data?.host) {
            "1111" -> "profile_1"
            "2222" -> "profile_2"
            "3333" -> "profile_3"
            "8888" -> null
            else -> return
        }
        // Broadcasts never mutate policy. The activity authenticates every request.
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TRIGGER_SOURCE", "SECRET_CODE")
            putExtra(MainActivity.EXTRA_PROFILE_ID, profile)
        })
    }
}
