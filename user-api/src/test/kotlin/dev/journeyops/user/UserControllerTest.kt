package dev.journeyops.user

import dev.journeyops.observability.DomainEventPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class UserControllerTest {
    private lateinit var tokenStore: TokenStore
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        tokenStore = TokenStore()
        val verifier = PhoneVerifier(tokenStore, DomainEventPublisher { })
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(UserController(verifier, tokenStore))
                .setControllerAdvice(UserProblemHandler())
                .build()
    }

    @Test
    fun `invalid phone returns bad request`() {
        mockMvc
            .perform(
                post("/api/v1/phone-verifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phoneNumber":"123"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"))
    }

    @Test
    fun `unknown bearer token returns unauthorized`() {
        mockMvc
            .perform(get("/internal/v1/users/me").header("Authorization", "Bearer missing"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `verified token resolves user`() {
        val verified = PhoneVerifier(tokenStore, DomainEventPublisher { }).verify("010-1234-5678")

        mockMvc
            .perform(get("/internal/v1/users/me").header("Authorization", "Bearer ${verified.accessToken}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(verified.userId))
    }
}
