package dev.journeyops.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class DomainEventLoggerTest {
    @Test
    fun `domain event logs stable ECS fields without raw customer data`() {
        val logger = LoggerFactory.getLogger("domain-event-test") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        val publisher = DomainEventLogger(logger)

        publisher.publish(
            DomainEvent(
                eventId = "evt-1",
                action = DomainEventAction.LOAN_APPLICATION_CREATED,
                userId = "sha256:user",
                applicationId = "app-1",
            ),
        )

        val fields =
            appender.list
                .single()
                .keyValuePairs
                .associate { it.key to it.value }
        assertEquals("evt-1", fields["event.id"])
        assertEquals("loan-application-created", fields["event.action"])
        assertEquals("journeyops.domain-event", fields["event.dataset"])
        assertEquals("app-1", fields["loan.application.id"])
        assertFalse(fields.values.any { it.toString().contains("01012345678") })
        assertEquals("domain-event", appender.list.single().formattedMessage)
    }
}
