package dev.journeyops.evaluation

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventAction
import dev.journeyops.observability.DomainEventPublisher
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LimitInquiryResponse(
    val applicationId: String,
    val approvedAmount: Long,
    val state: String,
)

data class EvaluationResponse(
    val applicationId: String,
    val result: String,
    val state: String,
)

@RestController
@RequestMapping("/api/v1/loan-applications/{applicationId}")
class LoanEvaluationController(
    private val userGateway: UserGateway,
    private val applicationGateway: ApplicationGateway,
    private val eventPublisher: DomainEventPublisher,
) {
    @PostMapping("/limit-inquiries")
    fun inquire(
        @PathVariable applicationId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): LimitInquiryResponse {
        val userId = userGateway.resolveUser(authorization)
        val transition = applicationGateway.transition(applicationId, userId, "OFFER_PROVIDED")
        publishWhenChanged(transition, userId, DomainEventAction.LOAN_OFFER_PROVIDED)
        return LimitInquiryResponse(applicationId, DEMO_APPROVED_AMOUNT, transition.state)
    }

    @PostMapping("/evaluations")
    fun evaluate(
        @PathVariable applicationId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): EvaluationResponse {
        val userId = userGateway.resolveUser(authorization)
        val transition = applicationGateway.transition(applicationId, userId, "EVALUATION_APPROVED")
        publishWhenChanged(transition, userId, DomainEventAction.LOAN_EVALUATION_APPROVED)
        return EvaluationResponse(applicationId, "APPROVED", transition.state)
    }

    private fun publishWhenChanged(
        transition: TransitionResponse,
        userId: String,
        action: String,
    ) {
        if (transition.changed) {
            eventPublisher.publish(DomainEvent(transition.eventId, action, userId, transition.applicationId))
        }
    }

    private companion object {
        const val DEMO_APPROVED_AMOUNT = 30_000_000L
    }
}
