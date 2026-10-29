package com.aiolos.cutemarmot.profile

import kotlinx.serialization.Serializable

/**
 * 用户自定义隐身与防御方案
 * @param id 方案唯一标识 (如 profile_1, profile_2, profile_3)
 * @param name 方案名称 (可由用户自由重命名，如 "日常办公", "借机防查", "终极隐身")
 * @param hiddenPackages 该方案下需要从桌面和系统彻底抹除隐藏的应用包名列表
 * @param lockedPackages 该方案下需要加锁（拦截弹出指纹/密码）的应用包名列表
 * @param usbDefenseEnabled 该方案是否开启 USB 物理防读取（电脑插线只能充电，不显示盘符）
 * @param hideSelfIcon 该方案是否从桌面彻底隐藏 Cute Marmot 自身图标（拨号盘呼出）
 * @param disguiseVault 该方案是否开启密柜伪装隐身模式（密柜对外呈隐匿伪装状态）
 */
@Serializable
data class StealthProfile(
    val id: String,
    val name: String,
    val hiddenPackages: Set<String> = emptySet(),
    val lockedPackages: Set<String> = emptySet(),
    val usbDefenseEnabled: Boolean = false,
    val hideSelfIcon: Boolean = false,
    val disguiseVault: Boolean = false
)
