package dev.journeyops.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class StructuredLogSanitizerTest {
    private val sanitizer = StructuredLogSanitizer()

    @Test
    fun `sanitizer protects reserved fields and moves unknown fields to custom`() {
        val members =
            linkedMapOf<String, Any?>(
                "service.name" to "loan-evaluation-api",
                "partner" to "kakao-bank",
                "note" to "call 010-1234-5678",
            )

        sanitizer.customize(members)

        assertEquals("loan-evaluation-api", members["service.name"])
        assertEquals(
            mapOf("partner" to "kakao-bank", "note" to "call 010-****-5678"),
            members["custom"],
        )
        assertFalse(members.containsKey("partner"))
    }
}
