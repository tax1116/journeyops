package dev.journeyops.evaluation

import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

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
    properties: EvaluationClientProperties,
) : ApplicationGateway {
    private val client = builder.baseUrl(properties.applicationBaseUrl).build()

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
            throw UpstreamHttpException(exception.statusCode.value(), exception)
        } catch (exception: ResourceAccessException) {
            throw UpstreamUnavailableException(exception)
        }
}
