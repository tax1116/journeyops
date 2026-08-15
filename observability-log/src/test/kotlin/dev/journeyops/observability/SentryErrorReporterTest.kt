package dev.journeyops.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SentryErrorReporterTest {
    @Test
    fun `error reporter sends bounded tags and no authorization value`() {
        lateinit var captured: Map<String, String>
        val reporter = SentryErrorReporter(SentryCapture { _, tags -> captured = tags })

        reporter.report(IllegalStateException("demo failure"), "user-api", "app-1")

        assertEquals("user-api", captured["service.name"])
        assertEquals("app-1", captured["loan.application.id"])
        assertFalse(captured.values.any { it.contains("Bearer") })
    }
}
