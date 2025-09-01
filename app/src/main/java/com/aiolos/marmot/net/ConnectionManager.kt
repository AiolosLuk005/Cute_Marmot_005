package com.aiolos.marmot.net

import com.aiolos.marmot.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*
import kotlin.coroutines.CoroutineContext

/**
 * Minimal TCP text protocol (JSON Lines)
 */
class ConnectionManager(
    private val ioDispatcher: CoroutineContext = Dispatchers.IO
) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Message> = _incoming

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startServer(port: Int = 9898): Boolean = withContext(ioDispatcher) {
        try {
            stop()
            serverSocket = ServerSocket(port)
            val s = serverSocket!!.accept()
            attach(s)
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    suspend fun connectTo(host: String, port: Int): Boolean = withContext(ioDispatcher) {
        try {
            stop()
            val addr = InetAddress.getByName(host)
            val s = Socket(addr, port)
            attach(s)
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    private fun attach(s: Socket) {
        socket = s
        reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))

        CoroutineScope(ioDispatcher).launch {
            try {
                while (isActive && !s.isClosed) {
                    val line = reader?.readLine() ?: break
                    val msg = json.decodeFromString(Message.serializer(), line)
                    _incoming.tryEmit(msg)
                }
            } catch (_: SocketException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun send(msg: Message) = withContext(ioDispatcher) {
        val w = writer ?: return@withContext
        val line = Json.encodeToString(Message.serializer(), msg)
        w.write(line); w.write("\n"); w.flush()
    }

    fun stop() {
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        reader = null; writer = null; socket = null; serverSocket = null
    }
}
