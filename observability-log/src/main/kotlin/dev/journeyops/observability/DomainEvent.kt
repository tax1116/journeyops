package dev.journeyops.observability

data class DomainEvent(
    val eventId: String,
    val action: String,
    val userId: String? = null,
    val applicationId: String? = null,
    val outcome: String = "success",
)

fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
