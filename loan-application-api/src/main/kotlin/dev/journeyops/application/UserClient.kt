package dev.journeyops.application

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@ConfigurationProperties("journeyops.clients.user")
data class UserApiProperties(
    val baseUrl: String,
)

data class UserResponse(
    val userId: String,
)

class UpstreamUnavailableException(
    cause: Throwable? = null,
) : RuntimeException("Upstream service is unavailable", cause)

class UpstreamUnauthorizedException : RuntimeException("Upstream rejected the access token")

fun interface UserGateway {
    fun resolveUser(authorization: String): String
}

@Component
class UserClient(
    builder: RestClient.Builder,
    properties: UserApiProperties,
) : UserGateway {
    private val client = builder.baseUrl(properties.baseUrl).build()

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
            if (exception.statusCode.value() == 401) throw UpstreamUnauthorizedException()
            throw UpstreamUnavailableException(exception)
        } catch (exception: ResourceAccessException) {
            throw UpstreamUnavailableException(exception)
        }
}
