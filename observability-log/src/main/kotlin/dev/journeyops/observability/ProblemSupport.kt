package dev.journeyops.observability

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

object ProblemSupport {
    fun create(
        status: HttpStatus,
        code: String,
        detail: String = status.reasonPhrase,
        requestId: String? = null,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).also {
            it.title = status.reasonPhrase
            it.setProperty("code", code)
            it.setProperty("requestId", requestId)
        }
}
