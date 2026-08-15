package dev.journeyops.user

import dev.journeyops.observability.RequestId
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class UserProblemHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidPhone(exception: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", exception.message)

    @ExceptionHandler(InvalidAccessTokenException::class)
    fun invalidToken(exception: InvalidAccessTokenException): ProblemDetail = problem(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", exception.message)

    @ExceptionHandler(ApplicationUpstreamException::class)
    fun applicationError(exception: ApplicationUpstreamException): ProblemDetail {
        val status = HttpStatus.resolve(exception.status) ?: HttpStatus.BAD_GATEWAY
        return problem(status, "APPLICATION_${status.value()}", exception.message)
    }

    @ExceptionHandler(ApplicationUnavailableException::class)
    fun applicationUnavailable(exception: ApplicationUnavailableException): ProblemDetail = problem(HttpStatus.SERVICE_UNAVAILABLE, "APPLICATION_UNAVAILABLE", exception.message)

    private fun problem(
        status: HttpStatus,
        code: String,
        detail: String?,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase).also {
            it.title = status.reasonPhrase
            it.setProperty("code", code)
            it.setProperty("requestId", MDC.get(RequestId.MDC_KEY))
        }
}
