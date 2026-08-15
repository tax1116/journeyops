package dev.journeyops.user

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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

class InvalidAccessTokenException : RuntimeException("Invalid access token")

@RestController
@RequestMapping
class UserController(
    private val verifier: PhoneVerifier,
    private val tokenStore: TokenStore,
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
    ): UserResponse {
        val token = authorization.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        val userId = token?.let(tokenStore::resolve) ?: throw InvalidAccessTokenException()
        return UserResponse(userId)
    }
}
