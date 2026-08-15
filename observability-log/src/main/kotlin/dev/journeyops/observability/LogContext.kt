package dev.journeyops.observability

import org.slf4j.MDC

class LogContext {
    fun <T> withFields(
        fields: Map<String, Any?>,
        block: () -> T,
    ): T {
        val customFields = fields.mapKeys { (key, _) -> key.takeIf { it.startsWith(CUSTOM_PREFIX) } ?: "$CUSTOM_PREFIX$key" }
        val previous = customFields.keys.associateWith(MDC::get)
        customFields.forEach { (key, value) ->
            if (value == null) {
                MDC.remove(key)
            } else {
                MDC.put(key, value.toString())
            }
        }
        return try {
            block()
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) MDC.remove(key) else MDC.put(key, value)
            }
        }
    }

    private companion object {
        const val CUSTOM_PREFIX = "custom."
    }
}
