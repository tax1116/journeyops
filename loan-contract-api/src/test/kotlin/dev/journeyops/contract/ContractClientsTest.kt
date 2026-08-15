package dev.journeyops.contract

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class ContractClientsTest {
    @Test
    fun `user client resolves bearer owner`() {
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
            val properties = ContractClientProperties(server.url("/").toString(), "http://unused")
            val client = UserClient(RestClient.builder(), properties)

            assertEquals("user-1", client.resolveUser("Bearer token"))
            assertEquals("Bearer token", server.takeRequest().headers["Authorization"])
        }
    }
}
