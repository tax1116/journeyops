package dev.journeyops.application

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventAction
import dev.journeyops.observability.DomainEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

data class LoanApplicationResponse(
    val applicationId: String,
    val userId: String,
    val state: LoanApplicationState,
    val eventId: String,
    val changed: Boolean,
)

@Service
class LoanApplicationService(
    private val repository: LoanApplicationRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    fun create(userId: String): LoanApplicationResponse {
        val applicationId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val application = repository.save(LoanApplication.create(applicationId, userId, eventId))
        eventPublisher.publish(
            DomainEvent(
                eventId = eventId,
                action = DomainEventAction.LOAN_APPLICATION_CREATED,
                userId = userId,
                applicationId = applicationId,
            ),
        )
        return application.toResponse(eventId, true)
    }

    fun get(
        id: String,
        userId: String? = null,
    ): LoanApplicationResponse {
        val application = repository.getById(id)
        if (userId != null && application.userId != userId) throw LoanApplicationNotFoundException(id)
        return application.toResponse("", false)
    }

    fun transition(
        id: String,
        userId: String,
        target: LoanApplicationState,
        action: String? = null,
    ): LoanApplicationResponse {
        val application = repository.getById(id)
        if (application.userId != userId) throw LoanApplicationNotFoundException(id)
        val result = application.transitionTo(target, UUID.randomUUID().toString())
        if (result.changed && action != null) {
            eventPublisher.publish(DomainEvent(result.eventId, action, userId, id))
        }
        return application.toResponse(result.eventId, result.changed)
    }

    private fun LoanApplication.toResponse(
        eventId: String,
        changed: Boolean,
    ): LoanApplicationResponse = LoanApplicationResponse(id, userId, state, eventId, changed)
}
