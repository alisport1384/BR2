package io.github.immaghzbad.aetherst.shared.model

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    DEBUG
}

data class LogEntry(
    val id: Long = 0,
    val timestamp: String,
    val level: LogLevel,
    val tag: String = "AetherCore",
    val message: String
)
