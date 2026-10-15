package dev.homepanel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.homepanel.app.ui.HomePanelApp
import dev.homepanel.app.ui.theme.HomePanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val language = settings?.interfaceLanguage?.takeIf { it in setOf("en", "es") } ?: "en"
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, language) {
                val config = android.content.res.Configuration(baseContext.resources.configuration)
                config.setLocale(Locale.forLanguageTag(language))
                val languageContext = baseContext.createConfigurationContext(config)
                object : android.content.ContextWrapper(baseContext) {
                    override fun getResources(): android.content.res.Resources = languageContext.resources
                }
            }
            val localizedConfiguration = remember(localizedContext) {
                android.content.res.Configuration(localizedContext.resources.configuration)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                HomePanelTheme {
                    HomePanelApp(mainViewModel)
                }
            }
        }
    }
}
