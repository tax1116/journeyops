package dev.journeyops.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LoanApplicationApiApplication

fun main(args: Array<String>) {
    runApplication<LoanApplicationApiApplication>(*args)
}
