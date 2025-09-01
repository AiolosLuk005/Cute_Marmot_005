package com.aiolos.marmot.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.aiolos.marmot.model.HandOff
import com.aiolos.marmot.model.Hello
import com.aiolos.marmot.model.Message
import com.aiolos.marmot.net.ConnectionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPickImage: (Array<String>) -> Unit,
    registerImagePicked: (((Uri) -> Unit) -> Unit)
) {
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf(Role.None) } // None / Host / Join
    var status by remember { mutableStateOf("Idle") }
    var peerIp by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }

    val conn = remember { ConnectionManager() }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // register callback to receive picked image
    LaunchedEffect(Unit) {
        registerImagePicked { uri -> imageUri = uri }
    }

    // receive messages
    LaunchedEffect(conn) {
        conn.incoming.collect { msg ->
            when (msg) {
                is Message.HandOff -> {
                    status = "HANDOFF from peer"
                    _handoffSignal.value = !_handoffSignal.value
                }
                is Message.Hello -> status = "Peer: ${msg.name}"
                else -> {}
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Marmot (Two-Screen Handoff MVP)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = role == Role.Host,
                        onClick = { role = Role.Host },
                        label = { Text("Host") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = role == Role.Join,
                        onClick = { role = Role.Join },
                        label = { Text("Join") }
                    )
                }

                if (role == Role.Join) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = peerIp,
                        onValueChange = { peerIp = it },
                        label = { Text("Host IP (e.g. 192.168.1.5)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        scope.launch {
                            connected = if (role == Role.Host) {
                                status = "Listening on :9898..."
                                conn.startServer()
                            } else if (role == Role.Join) {
                                status = "Connecting..."
                                conn.connectTo(peerIp.trim(), 9898)
                            } else false
                            if (connected) {
                                status = "Connected"
                                conn.send(Hello(name = android.os.Build.MODEL))
                            } else status = "Connect failed"
                        }
                    }) { Text(if (connected) "Reconnect" else "Start") }

                    Spacer(Modifier.width(8.dp))
                    Text("Status: " + status)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row {
                    Button(onClick = { onPickImage(arrayOf("image/*")) }) {
                        Text("选择图片")
                    }
                    Spacer(Modifier.width(8.dp))
                    if (imageUri != null) {
                        Button(onClick = {
                            scope.launch { if (connected) conn.send(HandOff(edge = "RIGHT")) }
                        }, enabled = connected) { Text("模拟越界接棒") }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri == null) {
                        Text("请选择一张图片，然后向右边缘拖拽（或点“模拟越界接棒”）")
                    } else {
                        val painter = rememberAsyncImagePainter(model = imageUri)
                        var dragAccum by remember { mutableStateOf(0f) }
                        val animatedOffset by animateFloatAsState(
                            targetValue = if (_handoffSignal.value) 1f else 0f,
                            label = "handoffAnim"
                        )

                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(imageUri, connected) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            dragAccum += dragAmount
                                            if (dragAccum > size.width * 0.2f) {
                                                dragAccum = 0f
                                                if (connected) {
                                                    launch { conn.send(HandOff(edge = "RIGHT")) }
                                                }
                                            }
                                        },
                                        onDragEnd = { dragAccum = 0f }
                                    )
                                }
                        )

                        Box(
                            Modifier.fillMaxHeight().width((animatedOffset * 16).dp)
                                .align(Alignment.CenterStart)
                                .background(Color(0xFF4CAF50))
                        )
                    }
                }
            }
        }
    }
}

private enum class Role { None, Host, Join }
private val _handoffSignal = mutableStateOf(false)
