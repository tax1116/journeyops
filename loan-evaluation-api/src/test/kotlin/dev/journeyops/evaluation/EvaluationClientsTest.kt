package dev.journeyops.evaluation

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class EvaluationClientsTest {
    @Test
    fun `application client sends owner and target state`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"applicationId":"app-1","state":"OFFER_PROVIDED","eventId":"evt-1","changed":true}""")
                    .build(),
            )
            server.start()
            val client =
                ApplicationClient(
                    RestClient.builder(),
                    EvaluationClientProperties("http://unused", server.url("/").toString()),
                )

            val result = client.transition("app-1", "user-1", "OFFER_PROVIDED")

            assertEquals("evt-1", result.eventId)
            assertTrue(
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    ?.contains("OFFER_PROVIDED") == true,
            )
        }
    }

    private fun assertTrue(value: Boolean) =
        org.junit.jupiter.api.Assertions
            .assertTrue(value)
}
