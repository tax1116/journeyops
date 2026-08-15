package dev.journeyops.observability

import io.sentry.SentryOptions
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class ObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun sensitiveDataMasker(): SensitiveDataMasker = SensitiveDataMasker()

    @Bean
    @ConditionalOnMissingBean
    fun logContext(): LogContext = LogContext()

    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher::class)
    fun domainEventPublisher(): DomainEventPublisher = DomainEventLogger()

    @Bean
    @ConditionalOnMissingBean
    fun httpAccessLogFilter(): HttpAccessLogFilter = HttpAccessLogFilter()

    @Bean
    @ConditionalOnMissingBean(ErrorReporter::class)
    fun errorReporter(): ErrorReporter = SentryErrorReporter()

    @Bean
    @ConditionalOnMissingBean(SentryOptions.BeforeSendCallback::class)
    fun sentryPrivacyConfiguration(masker: SensitiveDataMasker): SentryOptions.BeforeSendCallback = SentryPrivacyConfiguration(masker)
}
