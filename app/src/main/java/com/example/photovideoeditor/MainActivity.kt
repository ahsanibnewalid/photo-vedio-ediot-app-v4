package com.example.photovideoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photovideoeditor.ui.editor.EditorViewModel
import com.example.photovideoeditor.ui.editor.MainEditorScreen
import com.example.photovideoeditor.ui.theme.PhotoVideoEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoVideoEditorTheme {
                val editorViewModel: EditorViewModel = viewModel()
                MainEditorScreen(
                    viewModel = editorViewModel,
                    onExport = {
                        // Export pipeline is wired through the editor screen's export action.
                    }
                )
            }
        }
    }
}
