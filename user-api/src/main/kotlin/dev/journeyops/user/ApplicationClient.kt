package dev.journeyops.user

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@ConfigurationProperties("journeyops.clients.application")
data class ApplicationApiProperties(
    val baseUrl: String,
)

data class TransitionRequest(
    val userId: String,
    val targetState: String,
)

data class TransitionResponse(
    val applicationId: String,
    val state: String,
    val eventId: String,
    val changed: Boolean,
)

class ApplicationUpstreamException(
    val status: Int,
    cause: Throwable? = null,
) : RuntimeException("Application API failed", cause)

class ApplicationUnavailableException(
    cause: Throwable? = null,
) : RuntimeException("Application API is unavailable", cause)

fun interface ApplicationGateway {
    fun transition(
        applicationId: String,
        userId: String,
        targetState: String,
    ): TransitionResponse
}

@Component
class ApplicationClient(
    builder: RestClient.Builder,
    properties: ApplicationApiProperties,
) : ApplicationGateway {
    private val client = builder.baseUrl(properties.baseUrl).build()

    override fun transition(
        applicationId: String,
        userId: String,
        targetState: String,
    ): TransitionResponse =
        try {
            requireNotNull(
                client
                    .post()
                    .uri("/internal/v1/loan-applications/{id}/transitions", applicationId)
                    .body(TransitionRequest(userId, targetState))
                    .retrieve()
                    .body(TransitionResponse::class.java),
            )
        } catch (exception: RestClientResponseException) {
            throw ApplicationUpstreamException(exception.statusCode.value(), exception)
        } catch (exception: ResourceAccessException) {
            throw ApplicationUnavailableException(exception)
        }
}
