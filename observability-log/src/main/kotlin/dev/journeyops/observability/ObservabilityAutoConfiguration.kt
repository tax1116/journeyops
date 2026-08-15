package dev.journeyops.observability

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
}
