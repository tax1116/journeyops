package dev.journeyops.contract

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventAction
import dev.journeyops.observability.DomainEventPublisher
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ContractResponse(
    val applicationId: String,
    val result: String,
    val state: String,
)

@RestController
@RequestMapping("/api/v1/loan-applications/{applicationId}")
class LoanContractController(
    private val userGateway: UserGateway,
    private val applicationGateway: ApplicationGateway,
    private val eventPublisher: DomainEventPublisher,
) {
    @PostMapping("/contracts")
    fun sign(
        @PathVariable applicationId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): ContractResponse =
        perform(
            applicationId,
            authorization,
            "CONTRACT_SIGNED",
            "SIGNED",
            DomainEventAction.LOAN_CONTRACT_SIGNED,
        )

    @PostMapping("/payments")
    fun pay(
        @PathVariable applicationId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): ContractResponse =
        perform(
            applicationId,
            authorization,
            "PAID",
            "PAID",
            DomainEventAction.LOAN_PAYMENT_COMPLETED,
        )

    private fun perform(
        applicationId: String,
        authorization: String,
        targetState: String,
        result: String,
        action: String,
    ): ContractResponse {
        val userId = userGateway.resolveUser(authorization)
        val transition = applicationGateway.transition(applicationId, userId, targetState)
        if (transition.changed) {
            eventPublisher.publish(DomainEvent(transition.eventId, action, userId, applicationId))
        }
        return ContractResponse(applicationId, result, transition.state)
    }
}
