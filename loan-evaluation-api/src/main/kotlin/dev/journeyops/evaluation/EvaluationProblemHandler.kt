package dev.journeyops.evaluation

import dev.journeyops.observability.RequestId
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class EvaluationProblemHandler {
    @ExceptionHandler(UpstreamHttpException::class)
    fun upstreamHttp(exception: UpstreamHttpException): ProblemDetail {
        val status = HttpStatus.resolve(exception.status) ?: HttpStatus.BAD_GATEWAY
        return problem(status, "UPSTREAM_${status.value()}")
    }

    @ExceptionHandler(UpstreamUnavailableException::class)
    fun unavailable(): ProblemDetail = problem(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE")

    private fun problem(
        status: HttpStatus,
        code: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, status.reasonPhrase).also {
            it.title = status.reasonPhrase
            it.setProperty("code", code)
            it.setProperty("requestId", MDC.get(RequestId.MDC_KEY))
        }
}
