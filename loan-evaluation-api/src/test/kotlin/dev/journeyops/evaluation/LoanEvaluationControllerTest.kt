package dev.journeyops.evaluation

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

class LoanEvaluationControllerTest {
    @Test
    fun `limit inquiry transitions application and logs offer event`() {
        val events = mutableListOf<DomainEvent>()
        val application = FakeApplicationGateway(TransitionResponse("app-1", "OFFER_PROVIDED", "evt-offer", true))
        val controller =
            LoanEvaluationController(
                UserGateway { "user-1" },
                application,
                DomainEventPublisher(events::add),
            )

        val response = controller.inquire("app-1", "Bearer token")

        assertEquals(30_000_000L, response.approvedAmount)
        assertEquals("loan-offer-provided", events.single().action)
        assertEquals("evt-offer", events.single().eventId)
    }

    @Test
    fun `evaluation approval logs only when transition changed`() {
        val events = mutableListOf<DomainEvent>()
        val application = FakeApplicationGateway(TransitionResponse("app-1", "EVALUATION_APPROVED", "evt-eval", false))
        val controller =
            LoanEvaluationController(
                UserGateway { "user-1" },
                application,
                DomainEventPublisher(events::add),
            )

        controller.evaluate("app-1", "Bearer token")

        assertTrue(events.isEmpty())
    }

    private class FakeApplicationGateway(
        vararg responses: TransitionResponse,
    ) : ApplicationGateway {
        private val responses = ArrayDeque(responses.toList())

        override fun transition(
            applicationId: String,
            userId: String,
            targetState: String,
        ): TransitionResponse = responses.removeFirst()
    }
}
