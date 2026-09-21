package dev.homepanel.app

import android.app.Application
import dev.homepanel.app.diagnostics.CrashLogStore
import kotlin.system.exitProcess

class HomePanelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogStore.beginProcess(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { CrashLogStore.recordCrash(this, thread, throwable) }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(10)
            }
        }
    }
}
