package dev.journeyops.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HttpAccessLogFilterTest {
    @Test
    fun `filter returns request id and never logs authorization header`() {
        val logger = LoggerFactory.getLogger("http-access-test") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        val filter = HttpAccessLogFilter(logger)
        val request = MockHttpServletRequest("GET", "/api/v1/loan-applications/app-1")
        request.addHeader("Authorization", "Bearer secret-token")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertNotNull(response.getHeader(RequestId.HEADER_NAME))
        assertFalse(
            appender.list
                .single()
                .formattedMessage
                .contains("secret-token"),
        )
        assertFalse(
            appender.list
                .single()
                .keyValuePairs
                .any { it.value.toString().contains("secret-token") },
        )
    }
}
