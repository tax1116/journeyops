package dev.journeyops.user

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/demo")
class DemoFailureController {
    @PostMapping("/failures")
    fun fail(): Nothing = throw IllegalStateException("Demo failure for 010-1234-5678")
}
