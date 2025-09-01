package com.aiolos.cutemarmot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.aiolos.cutemarmot.ui.MainScreen

class MainActivity : ComponentActivity() {

    private var onImagePicked: ((Uri) -> Unit)? = null

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImagePicked?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(
                        onPickImage = { mimeTypes ->
                            openDocument.launch(mimeTypes)
                        },
                        registerImagePicked = { cb -> onImagePicked = cb }
                    )
                }
            }
        }
    }
}
