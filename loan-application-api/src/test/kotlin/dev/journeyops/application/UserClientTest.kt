package dev.journeyops.application

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class UserClientTest {
    @Test
    fun `user client forwards bearer token and resolves user`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"userId":"user-1"}""")
                    .build(),
            )
            server.start()
            val client = UserClient(RestClient.builder(), UserApiProperties(server.url("/").toString()))

            assertEquals("user-1", client.resolveUser("Bearer token"))
            assertEquals("Bearer token", server.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun `user client maps unavailable upstream to service unavailable`() {
        val server = MockWebServer()
        server.start()
        val url = server.url("/").toString()
        server.close()
        val client = UserClient(RestClient.builder(), UserApiProperties(url))

        assertThrows(UpstreamUnavailableException::class.java) {
            client.resolveUser("Bearer token")
        }
    }
}
