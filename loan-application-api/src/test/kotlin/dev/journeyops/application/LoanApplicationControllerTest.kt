package dev.journeyops.application

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class LoanApplicationControllerTest {
    private val events = mutableListOf<DomainEvent>()
    private lateinit var service: LoanApplicationService
    private lateinit var controller: LoanApplicationController

    @BeforeEach
    fun setUp() {
        service = LoanApplicationService(LoanApplicationRepository(), DomainEventPublisher(events::add))
        controller = LoanApplicationController(UserGateway { "user-1" }, service)
    }

    @Test
    fun `create returns application id and CREATED state`() {
        val response = controller.create("Bearer token")

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(LoanApplicationState.CREATED, response.body!!.state)
        assertEquals("loan-application-created", events.single().action)
    }

    @Test
    fun `internal transition validates owner and is idempotent`() {
        val created = controller.create("Bearer token").body!!
        val internal = InternalLoanApplicationController(service)

        val first =
            internal.transition(
                created.applicationId,
                TransitionRequest("user-1", LoanApplicationState.OFFER_PROVIDED),
            )
        val retry =
            internal.transition(
                created.applicationId,
                TransitionRequest("user-1", LoanApplicationState.OFFER_PROVIDED),
            )

        assertTrue(first.changed)
        assertFalse(retry.changed)
        assertEquals(first.eventId, retry.eventId)
    }
}
