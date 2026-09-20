package dev.homepanel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.homepanel.app.ui.HomePanelApp
import dev.homepanel.app.ui.theme.HomePanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HomePanelTheme {
                val mainViewModel: MainViewModel = viewModel()
                HomePanelApp(mainViewModel)
            }
        }
    }
}
