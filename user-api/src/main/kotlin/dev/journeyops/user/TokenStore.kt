package dev.journeyops.user

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TokenStore {
    private val usersByToken = ConcurrentHashMap<String, String>()

    fun save(
        accessToken: String,
        userId: String,
    ) {
        usersByToken[accessToken] = userId
    }

    fun resolve(accessToken: String): String? = usersByToken[accessToken]
}
