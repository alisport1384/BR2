package io.github.immaghzbad.aetherst.shared.data

import android.util.Log
import io.github.immaghzbad.aetherst.shared.model.AetherLogLevel
import io.github.immaghzbad.aetherst.shared.model.LogEntry
import io.github.immaghzbad.aetherst.shared.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val ids = AtomicLong(1)

    @Volatile var currentAppLogLevel: AetherLogLevel = AetherLogLevel.INFO
    @Volatile var currentCoreLogLevel: AetherLogLevel = AetherLogLevel.INFO
    @Volatile var fileLogWriter: ((LogLevel, String, String) -> Unit)? = null

    fun log(level: LogLevel, message: String, tag: String = "AetherSystem") {
        val sanitized = sanitize(message)
        runCatching { fileLogWriter?.invoke(level, tag, sanitized) }
        val enabled = when (if (tag == "AetherCore" || tag == "AetherRegistration") currentCoreLogLevel else currentAppLogLevel) {
            AetherLogLevel.OFF -> false
            AetherLogLevel.ERROR -> level == LogLevel.ERROR
            AetherLogLevel.WARN -> level == LogLevel.WARN || level == LogLevel.ERROR
            AetherLogLevel.INFO -> true
            AetherLogLevel.DEBUG -> true
        }
        if (!enabled) return
        val entry = LogEntry(
            id = ids.getAndIncrement(),
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()),
            level = level,
            tag = tag,
            message = sanitized,
        )
        val next = (_logs.value + entry).takeLast(1000)
        _logs.value = next
        when (level) {
            LogLevel.ERROR -> Log.e(tag, sanitized)
            LogLevel.WARN -> Log.w(tag, sanitized)
            LogLevel.DEBUG -> Log.d(tag, sanitized)
            LogLevel.INFO -> Log.i(tag, sanitized)
        }
    }

    fun i(message: String, tag: String = "AetherSystem") = log(LogLevel.INFO, message, tag)
    fun w(message: String, tag: String = "AetherSystem") = log(LogLevel.WARN, message, tag)
    fun e(message: String, tag: String = "AetherSystem") = log(LogLevel.ERROR, message, tag)
    fun d(message: String, tag: String = "AetherSystem") = log(LogLevel.DEBUG, message, tag)
    fun clear() { _logs.value = emptyList() }

    private fun sanitize(input: String): String {
        var out = input
        listOf("access_token", "private_key", "client_secret", "Authorization", "Bearer").forEach { key ->
            out = out.replace(Regex("$key[:\\s=]+[^\\s,;]+", RegexOption.IGNORE_CASE), "$key: [REDACTED]")
        }
        return out
    }
}
