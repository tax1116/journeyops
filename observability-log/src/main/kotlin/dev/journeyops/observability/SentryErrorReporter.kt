package dev.journeyops.observability

import io.sentry.Sentry

fun interface ErrorReporter {
    fun report(
        throwable: Throwable,
        serviceName: String,
        applicationId: String?,
    )

    companion object {
        val NOOP = ErrorReporter { _, _, _ -> }
    }
}

fun interface SentryCapture {
    fun capture(
        throwable: Throwable,
        tags: Map<String, String>,
    )
}

class SentryErrorReporter(
    private val capture: SentryCapture =
        SentryCapture { throwable, tags ->
            Sentry.withScope { scope ->
                tags.forEach(scope::setTag)
                Sentry.captureException(throwable)
            }
        },
) : ErrorReporter {
    override fun report(
        throwable: Throwable,
        serviceName: String,
        applicationId: String?,
    ) {
        val tags =
            buildMap {
                put("service.name", serviceName)
                applicationId?.let { put("loan.application.id", it) }
            }
        capture.capture(throwable, tags)
    }
}
