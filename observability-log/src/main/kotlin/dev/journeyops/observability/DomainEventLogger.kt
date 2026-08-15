package dev.journeyops.observability

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DomainEventLogger(
    private val logger: Logger = LoggerFactory.getLogger("journeyops.domain-event"),
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        val log =
            logger
                .atInfo()
                .addKeyValue("event.id", event.eventId)
                .addKeyValue("event.action", event.action)
                .addKeyValue("event.dataset", "journeyops.domain-event")
                .addKeyValue("event.outcome", event.outcome)
        event.userId?.let { log.addKeyValue("user.id", it) }
        event.applicationId?.let { log.addKeyValue("loan.application.id", it) }
        log.log("domain-event")
    }
}
