package dev.journeyops.application

import dev.journeyops.observability.DomainEventAction
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/loan-applications")
class LoanApplicationController(
    private val userGateway: UserGateway,
    private val service: LoanApplicationService,
) {
    @PostMapping
    fun create(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): ResponseEntity<LoanApplicationResponse> {
        val response = service.create(userGateway.resolveUser(authorization))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): LoanApplicationResponse =
        service.transition(
            id,
            userGateway.resolveUser(authorization),
            LoanApplicationState.APPLICATION_SUBMITTED,
            DomainEventAction.LOAN_APPLICATION_SUBMITTED,
        )

    @PostMapping("/{id}/documents")
    fun submitDocuments(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): LoanApplicationResponse =
        service.transition(
            id,
            userGateway.resolveUser(authorization),
            LoanApplicationState.DOCUMENTS_SUBMITTED,
            DomainEventAction.LOAN_DOCUMENTS_SUBMITTED,
        )
}
