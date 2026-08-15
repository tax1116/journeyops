package dev.journeyops.contract

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@ConfigurationProperties("journeyops.clients")
data class ContractClientProperties(
    val userBaseUrl: String,
    val applicationBaseUrl: String,
)

data class UserResponse(
    val userId: String,
)

class UpstreamHttpException(
    val status: Int,
    cause: Throwable? = null,
) : RuntimeException("Upstream request failed", cause)

class UpstreamUnavailableException(
    cause: Throwable? = null,
) : RuntimeException("Upstream service is unavailable", cause)

fun interface UserGateway {
    fun resolveUser(authorization: String): String
}

@Component
class UserClient(
    builder: RestClient.Builder,
    properties: ContractClientProperties,
) : UserGateway {
    private val client = builder.baseUrl(properties.userBaseUrl).build()

    override fun resolveUser(authorization: String): String =
        try {
            client
                .get()
                .uri("/internal/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(UserResponse::class.java)
                ?.userId ?: throw UpstreamUnavailableException()
        } catch (exception: RestClientResponseException) {
            throw UpstreamHttpException(exception.statusCode.value(), exception)
        } catch (exception: ResourceAccessException) {
            throw UpstreamUnavailableException(exception)
        }
}
