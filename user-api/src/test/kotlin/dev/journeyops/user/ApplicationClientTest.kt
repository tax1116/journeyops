package dev.journeyops.user

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class ApplicationClientTest {
    @Test
    fun `transition sends pseudonymous owner and identity target`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"applicationId":"app-1","state":"IDENTITY_VERIFIED","eventId":"evt-id","changed":true}""")
                    .build(),
            )
            server.start()
            val client = ApplicationClient(RestClient.builder(), ApplicationApiProperties(server.url("/").toString()))

            val response = client.transition("app-1", "sha256:user", "IDENTITY_VERIFIED")

            assertEquals("evt-id", response.eventId)
            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(body.contains("sha256:user"))
            assertTrue(body.contains("IDENTITY_VERIFIED"))
        }
    }
}
