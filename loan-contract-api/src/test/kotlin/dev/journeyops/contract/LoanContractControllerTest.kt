package dev.journeyops.contract

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

class LoanContractControllerTest {
    @Test
    fun `contract and payment use event ids from application transition`() {
        val events = mutableListOf<DomainEvent>()
        val application =
            FakeApplicationGateway(
                TransitionResponse("app-1", "CONTRACT_SIGNED", "evt-contract", true),
                TransitionResponse("app-1", "PAID", "evt-payment", true),
            )
        val controller =
            LoanContractController(
                UserGateway { "user-1" },
                application,
                DomainEventPublisher(events::add),
            )

        controller.sign("app-1", "Bearer token")
        controller.pay("app-1", "Bearer token")

        assertEquals(listOf("evt-contract", "evt-payment"), events.map { it.eventId })
    }

    private class FakeApplicationGateway(
        vararg values: TransitionResponse,
    ) : ApplicationGateway {
        private val responses = ArrayDeque(values.toList())

        override fun transition(
            applicationId: String,
            userId: String,
            targetState: String,
        ): TransitionResponse = responses.removeFirst()
    }
}
