package dev.journeyops.application

enum class LoanApplicationState {
    CREATED,
    OFFER_PROVIDED,
    APPLICATION_SUBMITTED,
    IDENTITY_VERIFIED,
    DOCUMENTS_SUBMITTED,
    EVALUATION_APPROVED,
    CONTRACT_SIGNED,
    PAID,
}

data class TransitionResult(
    val applicationId: String,
    val state: LoanApplicationState,
    val eventId: String,
    val changed: Boolean,
)

class InvalidApplicationStateException(
    val current: LoanApplicationState,
    val target: LoanApplicationState,
) : RuntimeException("Cannot transition loan application from $current to $target")

class LoanApplication private constructor(
    val id: String,
    val userId: String,
    initialEventId: String,
) {
    var state: LoanApplicationState = LoanApplicationState.CREATED
        private set

    private val eventIds = mutableMapOf(LoanApplicationState.CREATED to initialEventId)

    @Synchronized
    fun transitionTo(
        target: LoanApplicationState,
        proposedEventId: String,
    ): TransitionResult {
        if (target == state) {
            return TransitionResult(id, state, requireNotNull(eventIds[target]), false)
        }
        val expected = LoanApplicationState.entries.getOrNull(state.ordinal + 1)
        if (target != expected) throw InvalidApplicationStateException(state, target)
        state = target
        eventIds[target] = proposedEventId
        return TransitionResult(id, state, proposedEventId, true)
    }

    companion object {
        fun create(
            id: String,
            userId: String,
            eventId: String,
        ): LoanApplication = LoanApplication(id, userId, eventId)
    }
}
