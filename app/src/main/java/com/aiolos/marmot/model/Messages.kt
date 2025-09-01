package com.aiolos.marmot.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    @Serializable @SerialName("HELLO")
    data class Hello(val name: String, val proto: Int = 1): Message()

    @Serializable @SerialName("HANDOFF")
    data class HandOff(val edge: String, val atMs: Long = now()): Message()

    companion object {
        fun now() = System.currentTimeMillis()
    }
}

typealias Hello = Message.Hello
typealias HandOff = Message.HandOff
