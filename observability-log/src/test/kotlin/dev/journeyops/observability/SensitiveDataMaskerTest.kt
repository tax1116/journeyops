package dev.journeyops.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SensitiveDataMaskerTest {
    private val masker = SensitiveDataMasker()

    @ParameterizedTest
    @CsvSource(
        "'010-1234-5678','010-****-5678'",
        "'900101-1234567','900101-*******'",
        "'theo@example.com','t***@example.com'",
        "'1234-5678-9012-3456','123456******3456'",
        "'Bearer secret-token','Bearer [REDACTED]'",
        "'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature123','[REDACTED_JWT]'",
    )
    fun `sensitive values are masked before serialization`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, masker.mask(input))
    }

    @ParameterizedTest
    @CsvSource("'v1.2.3'", "'127.0.0.1'")
    fun `ordinary dotted values are not treated as JWT`(input: String) {
        assertEquals(input, masker.mask(input))
    }
}
