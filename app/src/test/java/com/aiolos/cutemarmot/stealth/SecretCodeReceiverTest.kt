package com.aiolos.cutemarmot.stealth

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.aiolos.cutemarmot.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecretCodeReceiverTest {
    @Test fun arbitraryActionSchemeAndUnknownProfileAreRejected() {
        val app = RuntimeEnvironment.getApplication()
        val receiver = SecretCodeReceiver()
        for ((action, data) in listOf(
            "evil.action" to "android_secret_code://1111",
            "android.provider.Telephony.SECRET_CODE" to "https://1111",
            "android.provider.Telephony.SECRET_CODE" to "android_secret_code://9999"
        )) receiver.onReceive(app, Intent(action, Uri.parse(data)))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun profileCodeOnlyLaunchesAuthenticationEntry() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("app_lock_prefs", 0)
        prefs.edit().putStringSet("locked_packages", setOf("protected.app")).commit()
        SecretCodeReceiver().onReceive(app,
            Intent("android.provider.Telephony.SECRET_CODE", Uri.parse("android_secret_code://1111")))
        val launch = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, launch.component?.className)
        assertEquals("SECRET_CODE", launch.getStringExtra("EXTRA_TRIGGER_SOURCE"))
        assertEquals("profile_1", launch.getStringExtra(MainActivity.EXTRA_PROFILE_ID))
        assertEquals(setOf("protected.app"), prefs.getStringSet("locked_packages", emptySet()))
    }
}
