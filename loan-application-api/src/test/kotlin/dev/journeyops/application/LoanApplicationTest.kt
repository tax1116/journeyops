package dev.journeyops.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoanApplicationTest {
    @Test
    fun `application transitions only to the next state`() {
        val application = LoanApplication.create("app-1", "user-1", "evt-created")

        val result = application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-offer")

        assertEquals(LoanApplicationState.OFFER_PROVIDED, result.state)
        assertTrue(result.changed)
    }

    @Test
    fun `application rejects a skipped state`() {
        val application = LoanApplication.create("app-1", "user-1", "evt-created")

        assertThrows(InvalidApplicationStateException::class.java) {
            application.transitionTo(LoanApplicationState.DOCUMENTS_SUBMITTED, "evt-documents")
        }
    }

    @Test
    fun `retry returns the original event id without another change`() {
        val application = LoanApplication.create("app-1", "user-1", "evt-created")
        application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-offer")

        val retry = application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-new")

        assertEquals("evt-offer", retry.eventId)
        assertFalse(retry.changed)
    }

    @Test
    fun `paid application rejects any later transition safely`() {
        val application = LoanApplication.create("app-1", "user-1", "evt-created")
        LoanApplicationState.entries.drop(1).forEach {
            application.transitionTo(it, "evt-${it.name.lowercase()}")
        }

        assertThrows(InvalidApplicationStateException::class.java) {
            application.transitionTo(LoanApplicationState.CREATED, "evt-new")
        }
    }
}
