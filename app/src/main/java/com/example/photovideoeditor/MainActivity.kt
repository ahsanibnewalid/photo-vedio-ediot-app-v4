package com.example.photovideoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.photovideoeditor.ui.editor.EditorViewModel
import com.example.photovideoeditor.ui.editor.MainEditorScreen
import com.example.photovideoeditor.ui.theme.PhotoVideoEditorTheme

class MainActivity : ComponentActivity() {
    private val editorViewModel: EditorViewModel by viewModels()

    private val mediaPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult

            // Photo Picker URIs are normally readable without persistable permission.
            // Attempt persistence for providers that support it; failure is harmless here.
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            when (contentResolver.getType(uri)?.substringBefore('/')) {
                "video" -> editorViewModel.importVideo(uri)
                "image" -> Toast.makeText(
                    this,
                    "Photo selected. Photo timeline and export support are not implemented yet.",
                    Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(
                    this,
                    "This media type is not supported.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            editorViewModel.importAudio(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhotoVideoEditorTheme {
                MainEditorScreen(
                    viewModel = editorViewModel,
                    onImport = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    onImportAudio = {
                        audioPicker.launch(arrayOf("audio/*"))
                    },
                    onExport = {
                        editorViewModel.export()
                    }
                )
            }
        }
    }
}
