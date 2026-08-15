package dev.journeyops.user

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.journeyops.observability.HttpAccessLogFilter
import jakarta.servlet.ServletContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiContextTest {
    @Autowired
    private lateinit var restClientBuilder: RestClient.Builder

    @Autowired
    private lateinit var servletContext: ServletContext

    @LocalServerPort
    private var serverPort: Int = 0

    @Test
    fun `application context provides configured RestClient builder`() {
        assertNotNull(restClientBuilder)
    }

    @Test
    fun `application context registers HTTP access log filter`() {
        assertTrue(
            servletContext.filterRegistrations.values.any {
                it.className == HttpAccessLogFilter::class.java.name
            },
        )
    }

    @Test
    fun `HTTP request produces access log`() {
        val logger = LoggerFactory.getLogger("journeyops.http") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            RestClient
                .create("http://localhost:$serverPort")
                .get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()

            assertTrue(appender.list.any { it.formattedMessage == "http-access" })
        } finally {
            logger.detachAppender(appender)
        }
    }
}
