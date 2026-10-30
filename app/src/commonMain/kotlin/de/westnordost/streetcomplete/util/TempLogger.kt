package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.data.logs.LogLevel
import de.westnordost.streetcomplete.data.logs.LogMessage
import de.westnordost.streetcomplete.data.logs.LogsSource
import de.westnordost.streetcomplete.data.logs.toChar
import de.westnordost.streetcomplete.util.ktx.now
import de.westnordost.streetcomplete.util.ktx.toInstant
import de.westnordost.streetcomplete.util.logs.Logger
import kotlinx.datetime.LocalDateTime
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

object TempLogger : Logger, LogsSource {
    private val lock = ReentrantLock()
    override fun e(tag: String, message: String, exception: Throwable?) {
        if (exception == null) {
            log(LogLine('E', tag, message))
        } else {
            log(LogLine('E', tag, "$message\n${exception.stackTraceToString()}"))
        }
    }

    override fun w(tag: String, message: String, exception: Throwable?) {
        if (exception == null) {
            log(LogLine('W', tag, message))
        } else {
            log(LogLine('W', tag, "$message\n${exception.stackTraceToString()}"))
        }
    }

    override fun i(tag: String, message: String) {
        log(LogLine('I', tag, message))
    }

    override fun d(tag: String, message: String) {
        log(LogLine('D', tag, message))
    }

    override fun v(tag: String, message: String) {
        log(LogLine('V', tag, message))
    }

    private fun log(line: LogLine) {
        lock.withLock {
            if (logLines.size > 12000) // clear oldest entries if list gets too long
                logLines.subList(0, 2000).clear()
            logLines.add(line)
        }
        listeners.forEach { it.onAdded(line.toLogMessage()) }
    }

    private val logLines: MutableList<LogLine> = ArrayList(2000)

    /** returns a copy of [logLines] */
    fun getLog() = lock.withLock { logLines.toList() }

    override fun getLogs(
        levels: Set<LogLevel>,
        messageContains: String?,
        newerThan: Long?,
        olderThan: Long?
    ): List<LogMessage> {
        val charLevels = levels.mapTo(hashSetOf()) { it.toChar() }
        return lock.withLock {
            logLines.asSequence().filter {
                if (it.level !in charLevels) return@filter false
                if (messageContains != null && !it.message.contains(messageContains, true))return@filter false
                if (newerThan != null && it.time.toInstant().toEpochMilliseconds() < newerThan) return@filter false
                if (olderThan != null && it.time.toInstant().toEpochMilliseconds() > olderThan) return@filter false
                true
            }.map { it.toLogMessage() }.toList()
        }
    }

    private val listeners = Listeners<LogsSource.Listener>()

    override fun addListener(listener: LogsSource.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: LogsSource.Listener) {
        listeners.remove(listener)
    }
}

data class LogLine(val level: Char, val tag: String, val message: String,) {
    val time = LocalDateTime.now()
    fun toLogMessage() = LogMessage(
        LogLevel.entries.first { it.toChar() == level }, tag, message, null,
        time.toInstant().toEpochMilliseconds()
    )
    override fun toString(): String = // should look like a normal android log line
        "${time.toString().replace('T', ' ')} $level $tag: $message"
}
