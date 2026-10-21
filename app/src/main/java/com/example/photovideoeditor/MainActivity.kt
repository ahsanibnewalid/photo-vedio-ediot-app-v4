package com.example.photovideoeditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photovideoeditor.ui.editor.EditorViewModel
import com.example.photovideoeditor.ui.editor.MainEditorScreen
import com.example.photovideoeditor.ui.theme.PhotoVideoEditorTheme

class MainActivity : ComponentActivity() {
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        editorViewModel?.importVideo(uri)
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        editorViewModel?.importAudio(uri)
    }

    private var editorViewModel: EditorViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoVideoEditorTheme {
                val vm: EditorViewModel = viewModel()
                editorViewModel = vm
                MainEditorScreen(
                    viewModel = vm,
                    onImport = { mediaPicker.launch(arrayOf("video/*")) },
                    onImportAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onExport = vm::export
                )
            }
        }
    }
}
