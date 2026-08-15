package dev.journeyops.user

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventAction
import dev.journeyops.observability.DomainEventPublisher
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PhoneVerificationRequest(
    val phoneNumber: String,
)

data class PhoneVerificationResponse(
    val accessToken: String,
    val userId: String,
)

data class UserResponse(
    val userId: String,
)

data class IdentityVerificationResponse(
    val applicationId: String,
    val result: String,
    val state: String,
)

class InvalidAccessTokenException : RuntimeException("Invalid access token")

@RestController
@RequestMapping
class UserController(
    private val verifier: PhoneVerifier,
    private val tokenStore: TokenStore,
    private val applicationGateway: ApplicationGateway,
    private val eventPublisher: DomainEventPublisher,
) {
    @PostMapping("/api/v1/phone-verifications")
    fun verifyPhone(
        @RequestBody request: PhoneVerificationRequest,
    ): ResponseEntity<PhoneVerificationResponse> {
        val verified = verifier.verify(request.phoneNumber)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(PhoneVerificationResponse(verified.accessToken, verified.userId))
    }

    @GetMapping("/internal/v1/users/me")
    fun me(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): UserResponse = UserResponse(resolveUser(authorization))

    @PostMapping("/api/v1/loan-applications/{applicationId}/identity-verifications")
    fun verifyIdentity(
        @PathVariable applicationId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
    ): IdentityVerificationResponse {
        val userId = resolveUser(authorization)
        val transition = applicationGateway.transition(applicationId, userId, "IDENTITY_VERIFIED")
        if (transition.changed) {
            eventPublisher.publish(
                DomainEvent(
                    transition.eventId,
                    DomainEventAction.IDENTITY_CARD_VERIFIED,
                    userId,
                    applicationId,
                ),
            )
        }
        return IdentityVerificationResponse(applicationId, "VERIFIED", transition.state)
    }

    private fun resolveUser(authorization: String): String {
        val token = authorization.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        return token?.let(tokenStore::resolve) ?: throw InvalidAccessTokenException()
    }
}
