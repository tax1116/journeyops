package dev.journeyops.observability

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class LogContextTest {
    private val logContext = LogContext()

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `nested fields restore previous MDC values`() {
        MDC.put("partner", "outer")

        logContext.withFields(mapOf("partner" to "inner", "campaign" to "summer")) {
            assertEquals("inner", MDC.get("partner"))
            assertEquals("summer", MDC.get("campaign"))
        }

        assertEquals("outer", MDC.get("partner"))
        assertNull(MDC.get("campaign"))
    }
}
