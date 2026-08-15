package dev.journeyops.observability

import org.slf4j.MDC

class LogContext {
    fun <T> withFields(
        fields: Map<String, Any?>,
        block: () -> T,
    ): T {
        val previous = fields.keys.associateWith(MDC::get)
        fields.forEach { (key, value) ->
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
}
