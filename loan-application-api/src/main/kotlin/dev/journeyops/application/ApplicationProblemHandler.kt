package dev.journeyops.application

import dev.journeyops.observability.ErrorReporter
import dev.journeyops.observability.ProblemSupport
import dev.journeyops.observability.RequestId
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApplicationProblemHandler(
    private val errorReporter: ErrorReporter = ErrorReporter.NOOP,
) {
    @ExceptionHandler(InvalidApplicationStateException::class)
    fun invalidState(exception: InvalidApplicationStateException): ProblemDetail = problem(HttpStatus.CONFLICT, "INVALID_APPLICATION_STATE", exception.message)

    @ExceptionHandler(LoanApplicationNotFoundException::class)
    fun notFound(exception: LoanApplicationNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "LOAN_APPLICATION_NOT_FOUND", exception.message)

    @ExceptionHandler(UpstreamUnauthorizedException::class)
    fun unauthorized(exception: UpstreamUnauthorizedException): ProblemDetail = problem(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", exception.message)

    @ExceptionHandler(UpstreamUnavailableException::class)
    fun unavailable(exception: UpstreamUnavailableException): ProblemDetail = problem(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", exception.message)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ProblemDetail {
        errorReporter.report(exception, "loan-application-api", null)
        return ProblemSupport.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "UNEXPECTED_ERROR",
            "Unexpected server error",
            MDC.get(RequestId.MDC_KEY),
        )
    }

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
