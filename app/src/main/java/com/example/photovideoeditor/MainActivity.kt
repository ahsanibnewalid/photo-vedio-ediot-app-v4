package com.example.photovideoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.photovideoeditor.ui.theme.PhotoVideoEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoVideoEditorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F10)) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F10)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Photo Video Editor", color = Color.White)
                        Text("Editor engine build", color = Color(0xFF00F0FF))
                    }
                }
            }
        }
    }
}
