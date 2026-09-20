package dev.homepanel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.homepanel.app.ui.HomePanelApp
import dev.homepanel.app.ui.theme.HomePanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomePanelTheme {
                val mainViewModel: MainViewModel = viewModel()
                HomePanelApp(mainViewModel)
            }
        }
    }
}
