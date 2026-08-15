package dev.journeyops.application

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TransitionRequest(
    val userId: String,
    val targetState: LoanApplicationState,
)

@RestController
@RequestMapping("/internal/v1/loan-applications")
class InternalLoanApplicationController(
    private val service: LoanApplicationService,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): LoanApplicationResponse = service.get(id)

    @PostMapping("/{id}/transitions")
    fun transition(
        @PathVariable id: String,
        @RequestBody request: TransitionRequest,
    ): LoanApplicationResponse = service.transition(id, request.userId, request.targetState)
}
