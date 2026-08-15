package dev.journeyops.user

import dev.journeyops.observability.DomainEvent
import dev.journeyops.observability.DomainEventAction
import dev.journeyops.observability.DomainEventPublisher
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

data class VerifiedUser(
    val userId: String,
    val accessToken: String,
)

@Service
class PhoneVerifier(
    private val tokenStore: TokenStore,
    private val eventPublisher: DomainEventPublisher,
) {
    fun verify(phoneNumber: String): VerifiedUser {
        val normalized = phoneNumber.filter(Char::isDigit)
        require(PHONE_PATTERN.matches(normalized)) { "Invalid phone number" }
        val userId = "sha256:${sha256(normalized)}"
        val accessToken = UUID.randomUUID().toString()
        tokenStore.save(accessToken, userId)
        eventPublisher.publish(
            DomainEvent(
                eventId = UUID.randomUUID().toString(),
                action = DomainEventAction.PHONE_VERIFICATION_COMPLETED,
                userId = userId,
            ),
        )
        return VerifiedUser(userId, accessToken)
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val PHONE_PATTERN = Regex("01[016789][0-9]{7,8}")
    }
}
