package com.aiolos.cutemarmot.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aiolos.cutemarmot.admin.AppHideManager
import com.aiolos.cutemarmot.admin.UsbSecurityManager
import com.aiolos.cutemarmot.lock.AppLockRepository
import com.aiolos.cutemarmot.stealth.IconHideManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ProfileApplySummary(
    val profileName: String,
    val hiddenAppsApplied: Int,
    val unhiddenAppsApplied: Int,
    val lockedAppsApplied: Int,
    val usbBlocked: Boolean,
    val selfIconHidden: Boolean,
    val vaultDisguised: Boolean
)

class StealthProfileManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stealth_profiles_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val appHideManager = AppHideManager(context)
    private val appLockRepo = AppLockRepository(context)
    private val usbSecurityManager = UsbSecurityManager(context)
    private val iconHideManager = IconHideManager(context)

    companion object {
        private const val TAG = "StealthProfileManager"
        private const val KEY_PROFILES = "saved_profiles_v1"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_VAULT_DISGUISED = "is_vault_disguised"

        val PROFILE_IDS = listOf("profile_1", "profile_2", "profile_3")
    }

    /**
     * 获取全部 3 个方案（若首次读取则自动初始化预设方案）
     */
    fun getProfiles(): List<StealthProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) {
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            return defaults
        }
        return try {
            val list = json.decodeFromString<List<StealthProfile>>(raw)
            if (list.size < 3) {
                val defaults = createDefaultProfiles()
                val merged = defaults.map { def ->
                    list.find { it.id == def.id } ?: def
                }
                saveProfiles(merged)
                merged
            } else {
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析保存的方案失败，恢复默认: ${e.message}")
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            defaults
        }
    }

    /**
     * 获取当前生效的方案 ID
     */
    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "profile_1") ?: "profile_1"
    }

    fun setActiveProfileId(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    }

    fun getActiveProfile(): StealthProfile {
        val currentId = getActiveProfileId()
        return getProfiles().find { it.id == currentId } ?: getProfiles().first()
    }

    /**
     * 重命名方案
     */
    fun renameProfile(profileId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false

        val list = getProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profileId }
        if (index != -1) {
            list[index] = list[index].copy(name = trimmed)
            saveProfiles(list)
            Log.i(TAG, "方案 $profileId 已重命名为: $trimmed")
            return true
        }
        return false
    }

    /**
     * 更新指定方案的目标隐藏与加锁应用名单并持久化保存
     */
    fun updateProfileAppSelections(
        profileId: String,
        hiddenPackages: Set<String>,
        lockedPackages: Set<String>
    ): StealthProfile? {
        val list = getProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profileId }
        if (index == -1) return null

        val updated = list[index].copy(
            hiddenPackages = hiddenPackages,
            lockedPackages = lockedPackages
        )
        list[index] = updated
        saveProfiles(list)
        Log.i(TAG, "已更新方案 ${updated.name} 的应用配置: 隐藏${hiddenPackages.size}款, 加锁${lockedPackages.size}款")
        return updated
    }

    /**
     * 将当前手机上实时配置（隐藏、加锁、USB开关、自身隐身、密柜隐身）保存覆盖到指定方案
     */
    fun saveCurrentStateToProfile(profileId: String): StealthProfile? {
        val list = getProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profileId }
        if (index == -1) return null

        val currentInstalled = appHideManager.getInstalledApps()
        val currentHidden = currentInstalled.filter { it.isHidden }.map { it.packageName }.toSet()
        val currentLocked = appLockRepo.getLockedPackages()
        val currentUsbBlocked = usbSecurityManager.isUsbFileTransferDisabled()
        val currentSelfHidden = iconHideManager.isIconHidden()
        val currentDisguised = isVaultDisguised()

        val updated = list[index].copy(
            hiddenPackages = currentHidden,
            lockedPackages = currentLocked,
            usbDefenseEnabled = currentUsbBlocked,
            hideSelfIcon = currentSelfHidden,
            disguiseVault = currentDisguised
        )
        list[index] = updated
        saveProfiles(list)
        setActiveProfileId(profileId)

        Log.i(TAG, "已将当前防御状态保存至方案: ${updated.name} (隐藏${currentHidden.size}款, 加锁${currentLocked.size}款, USB=${currentUsbBlocked})")
        return updated
    }

    /**
     * 一键执行并应用指定方案
     */
    suspend fun applyProfile(profile: StealthProfile): ProfileApplySummary {
        Log.i(TAG, "一键应用方案: ${profile.name} (${profile.id})")
        setActiveProfileId(profile.id)

        val canHide = appHideManager.canHideApps()
        val isOwner = appHideManager.isDeviceOwner()
        val installedApps = appHideManager.getInstalledApps()

        var hiddenCount = 0
        var unhiddenCount = 0

        // 1. 批量隐藏/解冻应用 (支持 Device Owner 或 Shizuku 模式)
        if (canHide) {
            for (app in installedApps) {
                val shouldBeHidden = profile.hiddenPackages.contains(app.packageName)
                if (shouldBeHidden && !app.isHidden) {
                    if (appHideManager.hideApp(app.packageName)) {
                        hiddenCount++
                    }
                    kotlinx.coroutines.delay(150) // 关键延迟缓冲：让系统 PMS 有充足时间写入状态广播，避免并发冲突导致后续应用失败
                } else if (!shouldBeHidden && app.isHidden) {
                    if (appHideManager.unhideApp(app.packageName)) {
                        unhiddenCount++
                    }
                    kotlinx.coroutines.delay(150) // 关键延迟缓冲
                }
            }
        }

        // 2. 批量加锁/解锁应用
        var lockedCount = 0
        for (app in installedApps) {
            val shouldBeLocked = profile.lockedPackages.contains(app.packageName)
            appLockRepo.setAppLocked(app.packageName, shouldBeLocked)
            if (shouldBeLocked) lockedCount++
        }

        // 3. USB 防御开关
        if (isOwner) {
            usbSecurityManager.setUsbFileTransferDisabled(profile.usbDefenseEnabled)
        }

        // 4. 自身桌面图标隐形
        iconHideManager.setIconHidden(profile.hideSelfIcon)

        // 5. 密柜伪装状态
        setVaultDisguised(profile.disguiseVault)

        return ProfileApplySummary(
            profileName = profile.name,
            hiddenAppsApplied = hiddenCount,
            unhiddenAppsApplied = unhiddenCount,
            lockedAppsApplied = lockedCount,
            usbBlocked = profile.usbDefenseEnabled,
            selfIconHidden = profile.hideSelfIcon,
            vaultDisguised = profile.disguiseVault
        )
    }

    /**
     * 密柜伪装隐身状态
     */
    fun isVaultDisguised(): Boolean {
        return prefs.getBoolean(KEY_VAULT_DISGUISED, false)
    }

    fun setVaultDisguised(disguised: Boolean) {
        prefs.edit().putBoolean(KEY_VAULT_DISGUISED, disguised).apply()
    }

    private fun saveProfiles(profiles: List<StealthProfile>) {
        val str = json.encodeToString(profiles)
        prefs.edit().putString(KEY_PROFILES, str).apply()
    }

    /**
     * 初始预设 3 套方案
     */
    private fun createDefaultProfiles(): List<StealthProfile> {
        return listOf(
            StealthProfile(
                id = "profile_1",
                name = "日常办公",
                hiddenPackages = emptySet(),
                lockedPackages = emptySet(),
                usbDefenseEnabled = false,
                hideSelfIcon = false,
                disguiseVault = false
            ),
            StealthProfile(
                id = "profile_2",
                name = "借机防查",
                hiddenPackages = emptySet(),
                lockedPackages = emptySet(),
                usbDefenseEnabled = true,
                hideSelfIcon = false,
                disguiseVault = true
            ),
            StealthProfile(
                id = "profile_3",
                name = "终极隐身",
                hiddenPackages = emptySet(),
                lockedPackages = emptySet(),
                usbDefenseEnabled = true,
                hideSelfIcon = true,
                disguiseVault = true
            )
        )
    }
}
