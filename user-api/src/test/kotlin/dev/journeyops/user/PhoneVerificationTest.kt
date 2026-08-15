package dev.journeyops.user

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneVerificationTest {
    @Test
    fun `verified phone produces stable pseudonymous user and opaque token`() {
        val events = mutableListOf<DomainEvent>()
        val verifier = PhoneVerifier(TokenStore(), DomainEventPublisher(events::add))

        val result = verifier.verify("010-1234-5678")

        assertTrue(result.userId.startsWith("sha256:"))
        assertFalse(result.userId.contains("010"))
        assertFalse(result.accessToken.contains("01012345678"))
        assertTrue(events.single().userId == result.userId)
    }
}
