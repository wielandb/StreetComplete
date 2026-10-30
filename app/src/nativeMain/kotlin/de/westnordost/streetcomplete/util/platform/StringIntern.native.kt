package de.westnordost.streetcomplete.util.platform

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

// Native has no VM string pool. Bound this pool so discarded downloads cannot keep growing it.
private val strings = LinkedHashMap<String, String>()
private val lock = ReentrantLock()
private const val MAX_STRINGS = 32768

actual fun String.internString(): String = lock.withLock {
    strings[this]?.let { return it }
    if (strings.size >= MAX_STRINGS) strings.remove(strings.keys.first())
    strings[this] = this
    this
}
