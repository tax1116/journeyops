package dev.journeyops.evaluation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LoanEvaluationApiApplication

fun main(args: Array<String>) {
    runApplication<LoanEvaluationApiApplication>(*args)
}
