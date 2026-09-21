package dev.homepanel.app.diagnostics

import android.content.Context
import android.os.Build
import dev.homepanel.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Minimal persistent crash recorder.
 *
 * The report survives process death and is shown on the next launch so field crashes on wall
 * tablets can be diagnosed without adb/logcat. If the previous process crashed shortly after
 * launch, the next process starts in safe mode for that boot only.
 */
object CrashLogStore {
    private const val PREFS = "homepanel_crash_log"
    private const val KEY_REPORT = "last_report"
    private const val KEY_PENDING = "report_pending"
    private const val KEY_LIFETIME_MS = "last_lifetime_ms"
    private const val KEY_PROCESS_START = "process_start_ms"
    private const val MAX_REPORT_CHARS = 24_000

    @Volatile private var processStartMs: Long = 0L
    @Volatile private var safeModeThisBoot: Boolean = false

    fun beginProcess(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousPending = prefs.getBoolean(KEY_PENDING, false)
        // Any uncaught crash triggers one conservative safe-mode boot. Once the user sees or
        // copies the report the following launch returns to the saved configuration.
        safeModeThisBoot = previousPending
        processStartMs = System.currentTimeMillis()
        prefs.edit().putLong(KEY_PROCESS_START, processStartMs).apply()
    }

    fun isSafeMode(): Boolean = safeModeThisBoot

    fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val start = processStartMs.takeIf { it > 0L }
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_PROCESS_START, now)
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()
        val report = buildString {
            appendLine("HomePanel crash report")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("thread=${thread.name}")
            appendLine("processLifetimeMs=${(now - start).coerceAtLeast(0L)}")
            appendLine("timeEpochMs=$now")
            appendLine()
            append(stack)
        }.take(MAX_REPORT_CHARS)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORT, report)
            .putBoolean(KEY_PENDING, true)
            .putLong(KEY_LIFETIME_MS, (now - start).coerceAtLeast(0L))
            .apply()
    }

    fun pendingReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return null
        return prefs.getString(KEY_REPORT, null)?.takeIf { it.isNotBlank() }
    }

    fun markReportShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, false)
            .apply()
    }
}
