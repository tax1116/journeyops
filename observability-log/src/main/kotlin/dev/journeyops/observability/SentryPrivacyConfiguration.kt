package dev.journeyops.observability

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions

class SentryPrivacyConfiguration(
    private val masker: SensitiveDataMasker = SensitiveDataMasker(),
) : SentryOptions.BeforeSendCallback {
    override fun execute(
        event: SentryEvent,
        hint: Hint,
    ): SentryEvent {
        event.message?.let { message ->
            message.formatted = message.formatted?.let(masker::mask)
            message.message = message.message?.let(masker::mask)
            message.params = message.params?.map(masker::mask)
        }
        event.exceptions?.forEach { exception ->
            exception.value = exception.value?.let(masker::mask)
        }
        event.breadcrumbs?.forEach { breadcrumb ->
            breadcrumb.message = breadcrumb.message?.let(masker::mask)
            breadcrumb.data.keys.toList().forEach { key ->
                val value = breadcrumb.getData(key)
                if (value is String) breadcrumb.setData(key, masker.mask(value))
            }
        }
        event.tags = event.tags?.mapValues { (_, value) -> masker.mask(value) }
        event.request?.let { request ->
            request.data = null
            request.headers =
                request.headers
                    ?.filterKeys { header -> SENSITIVE_HEADERS.none { it.equals(header, ignoreCase = true) } }
                    ?.toMutableMap()
        }
        return event
    }

    private companion object {
        val SENSITIVE_HEADERS = setOf("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization")
    }
}
