package dev.journeyops.observability

class SensitiveDataMasker {
    fun mask(value: String): String {
        val truncated = value.length > MAX_INPUT_LENGTH
        var result = value.take(MAX_INPUT_LENGTH)
        result = JWT.replace(result, "[REDACTED_JWT]")
        result = BEARER.replace(result) { "${it.groupValues[1]}[REDACTED]" }
        result =
            PHONE.replace(result) {
                "${it.groupValues[1]}${it.groupValues[2]}****${it.groupValues[3]}${it.groupValues[4]}"
            }
        result = RRN.replace(result) { "${it.groupValues[1]}-*******" }
        result = EMAIL.replace(result) { "${it.groupValues[1]}***@${it.groupValues[2]}" }
        result =
            CARD.replace(result) {
                val digits = it.value.filter(Char::isDigit)
                "${digits.take(6)}******${digits.takeLast(4)}"
            }
        return if (truncated) "$result[TRUNCATED]" else result
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 16 * 1024
        val JWT = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])")
        val BEARER = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*")
        val PHONE = Regex("\\b(01[016789])([- ]?)\\d{3,4}([- ]?)(\\d{4})\\b")
        val RRN = Regex("\\b(\\d{6})-[1-8]\\d{6}\\b")
        val EMAIL = Regex("\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b")
        val CARD = Regex("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b")
    }
}
