package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SettingsListener
import de.westnordost.streetcomplete.util.Listeners
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json

/** Keeps the value types needed for portable settings backups. Native preferences otherwise
 *  coerce numeric types, and desktop preferences store everything as strings. */
class SnapshotSettings(
    private val delegate: ObservableSettings,
    private val legacyValues: () -> Map<String, Any?> = { emptyMap() },
) : ObservableSettings by delegate {
    private val lock = ReentrantLock()
    private val listeners = Listeners<(String) -> Unit>()
    private val types = delegate.getStringOrNull(TYPE_KEY)?.let {
        Json.decodeFromString<Map<String, String>>(it).toMutableMap()
    } ?: mutableMapOf()

    override val keys: Set<String> get() = delegate.keys - TYPE_KEY
    override val size: Int get() = keys.size

    val all: Map<String, Any?> get() = lock.withLock {
        val result = legacyValues().filterKeys { it != TYPE_KEY }.toMutableMap()
        for ((key, type) in types) {
            if (!delegate.hasKey(key)) continue
            result[key] = when (type) {
                "boolean" -> delegate.getBooleanOrNull(key)
                "int" -> delegate.getIntOrNull(key)
                "long" -> delegate.getLongOrNull(key)
                "float" -> delegate.getFloatOrNull(key)
                "double" -> delegate.getDoubleOrNull(key)
                "string" -> delegate.getStringOrNull(key)
                else -> continue
            }
        }
        result
    }

    fun onChange(callback: (String) -> Unit): SettingsListener {
        listeners.add(callback)
        return object : SettingsListener {
            override fun deactivate() { listeners.remove(callback) }
        }
    }

    private fun recordType(key: String, type: String) {
        if (types[key] == type) return
        types[key] = type
        delegate.putString(TYPE_KEY, Json.encodeToString(types))
    }

    private inline fun <T> read(key: String, type: String, get: () -> T): T = lock.withLock {
        val value = get()
        if (delegate.hasKey(key)) recordType(key, type)
        value
    }

    private inline fun write(key: String, type: String, put: () -> Unit) {
        lock.withLock {
            put()
            recordType(key, type)
        }
        listeners.forEach { it(key) }
    }

    override fun putBoolean(key: String, value: Boolean) = write(key, "boolean") { delegate.putBoolean(key, value) }
    override fun putInt(key: String, value: Int) = write(key, "int") { delegate.putInt(key, value) }
    override fun putLong(key: String, value: Long) = write(key, "long") { delegate.putLong(key, value) }
    override fun putFloat(key: String, value: Float) = write(key, "float") { delegate.putFloat(key, value) }
    override fun putDouble(key: String, value: Double) = write(key, "double") { delegate.putDouble(key, value) }
    override fun putString(key: String, value: String) = write(key, "string") { delegate.putString(key, value) }

    override fun getBoolean(key: String, defaultValue: Boolean) = read(key, "boolean") { delegate.getBoolean(key, defaultValue) }
    override fun getInt(key: String, defaultValue: Int) = read(key, "int") { delegate.getInt(key, defaultValue) }
    override fun getLong(key: String, defaultValue: Long) = read(key, "long") { delegate.getLong(key, defaultValue) }
    override fun getFloat(key: String, defaultValue: Float) = read(key, "float") { delegate.getFloat(key, defaultValue) }
    override fun getDouble(key: String, defaultValue: Double) = read(key, "double") { delegate.getDouble(key, defaultValue) }
    override fun getString(key: String, defaultValue: String) = read(key, "string") { delegate.getString(key, defaultValue) }

    override fun getBooleanOrNull(key: String) = read(key, "boolean") { delegate.getBooleanOrNull(key) }
    override fun getIntOrNull(key: String) = read(key, "int") { delegate.getIntOrNull(key) }
    override fun getLongOrNull(key: String) = read(key, "long") { delegate.getLongOrNull(key) }
    override fun getFloatOrNull(key: String) = read(key, "float") { delegate.getFloatOrNull(key) }
    override fun getDoubleOrNull(key: String) = read(key, "double") { delegate.getDoubleOrNull(key) }
    override fun getStringOrNull(key: String) = read(key, "string") { delegate.getStringOrNull(key) }

    override fun remove(key: String) {
        lock.withLock {
            delegate.remove(key)
            if (types.remove(key) != null) delegate.putString(TYPE_KEY, Json.encodeToString(types))
        }
        listeners.forEach { it(key) }
    }

    override fun clear() { keys.forEach(::remove) }

    companion object {
        private const val TYPE_KEY = "scee.settings.value_types"
    }
}
