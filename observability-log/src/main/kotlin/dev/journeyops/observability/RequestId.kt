package dev.journeyops.observability

import java.util.UUID

object RequestId {
    const val HEADER_NAME = "X-Request-Id"
    const val MDC_KEY = "http.request.id"

    fun create(): String = UUID.randomUUID().toString()
}
