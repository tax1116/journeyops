package dev.journeyops.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter

class HttpAccessLogFilter(
    private val accessLogger: Logger = LoggerFactory.getLogger("journeyops.http"),
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(RequestId.HEADER_NAME)?.takeIf(String::isNotBlank) ?: RequestId.create()
        val startedAt = System.nanoTime()
        response.setHeader(RequestId.HEADER_NAME, requestId)
        MDC.put(RequestId.MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            accessLogger
                .atInfo()
                .addKeyValue("event.dataset", "journeyops.http")
                .addKeyValue("http.request.method", request.method)
                .addKeyValue("url.path", request.requestURI)
                .addKeyValue("http.response.status_code", response.status)
                .addKeyValue("event.duration", System.nanoTime() - startedAt)
                .log("http-access")
            MDC.remove(RequestId.MDC_KEY)
        }
    }
}
