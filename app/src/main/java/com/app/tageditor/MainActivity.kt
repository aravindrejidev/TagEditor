package com.app.tageditor

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.app.tageditor.ui.TagEditorApp
import com.app.tageditor.ui.theme.TagEditorTheme
import com.app.tageditor.viewmodel.TagEditorViewModel

class MainActivity : ComponentActivity() {

    // The same instance the UI gets from viewModel(): both live in this activity's store.
    private val viewModel: TagEditorViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // The user may have changed the storage permission in the system settings.
        viewModel.refreshAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            TagEditorTheme {
                TagEditorApp()
            }
        }
    }
}
