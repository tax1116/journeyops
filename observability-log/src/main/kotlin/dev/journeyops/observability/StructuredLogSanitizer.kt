package dev.journeyops.observability

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer

class StructuredLogSanitizer(
    private val masker: SensitiveDataMasker = SensitiveDataMasker(),
) : StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    override fun customize(members: JsonWriter.Members<ILoggingEvent>) {
        members.applyingValueProcessor { _, value -> sanitizeValue(value) }
    }

    fun customize(members: MutableMap<String, Any?>) {
        val custom = linkedMapOf<String, Any?>()
        val iterator = members.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == "custom") {
                @Suppress("UNCHECKED_CAST")
                custom.putAll((entry.value as? Map<String, Any?>).orEmpty())
                iterator.remove()
            } else if (!isReserved(entry.key)) {
                custom[entry.key] = sanitizeValue(entry.value)
                iterator.remove()
            } else {
                entry.setValue(sanitizeValue(entry.value))
            }
        }
        if (custom.isNotEmpty()) members["custom"] = sanitizeValue(custom)
    }

    private fun isReserved(key: String): Boolean =
        key == "@timestamp" ||
            key == "message" ||
            key == "user.id" ||
            key == "loan.application.id" ||
            RESERVED_PREFIXES.any(key::startsWith)

    private fun sanitizeValue(value: Any?): Any? =
        when (value) {
            is String -> masker.mask(value)
            is Map<*, *> -> value.entries.associate { it.key.toString() to sanitizeValue(it.value) }
            is Iterable<*> -> value.map(::sanitizeValue)
            is Array<*> -> value.map(::sanitizeValue)
            else -> value
        }

    private companion object {
        val RESERVED_PREFIXES =
            listOf(
                "service.",
                "trace.",
                "span.",
                "http.",
                "event.",
                "error.",
                "log.",
                "process.",
                "host.",
                "ecs.",
                "thread.",
            )
    }
}
