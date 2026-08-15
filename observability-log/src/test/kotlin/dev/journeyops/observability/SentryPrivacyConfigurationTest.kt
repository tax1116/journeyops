package dev.journeyops.observability

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SentryPrivacyConfigurationTest {
    @Test
    fun `before send masks exception and removes request credentials`() {
        val event = SentryEvent()
        event.message = Message().also { it.formatted = "customer 010-1234-5678 failed" }
        event.exceptions = listOf(SentryException().also { it.value = "email theo@example.com" })
        event.breadcrumbs = listOf(Breadcrumb("call 010-1234-5678"))
        event.setTag("partner", "Bearer secret-token")
        event.request =
            Request().also {
                it.headers = mutableMapOf("Authorization" to "Bearer secret-token", "Accept" to "application/json")
                it.data = "phone=010-1234-5678"
            }

        val sanitized = SentryPrivacyConfiguration(SensitiveDataMasker()).execute(event, Hint())!!

        assertEquals("customer 010-****-5678 failed", sanitized.message!!.formatted)
        assertEquals("email t***@example.com", sanitized.exceptions!!.single().value)
        assertEquals("call 010-****-5678", sanitized.breadcrumbs!!.single().message)
        assertEquals("Bearer [REDACTED]", sanitized.getTag("partner"))
        assertNull(sanitized.request!!.headers?.get("Authorization"))
        assertNull(sanitized.request!!.data)
    }
}
