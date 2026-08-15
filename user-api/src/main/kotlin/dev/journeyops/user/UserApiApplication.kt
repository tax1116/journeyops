package dev.journeyops.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class UserApiApplication

fun main(args: Array<String>) {
    runApplication<UserApiApplication>(*args)
}
