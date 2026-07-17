package org.khorum.oss.kontinuance.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The Spring Boot application hosting the run-history read API (008). Replaces the 007 JDK
 * [com.sun.net.httpserver.HttpServer] launcher: the same `/api` contract is now served by a WebFlux
 * application with coroutine [RunController] handlers and actuator health, on the platform runtime the
 * constitution names. Configuration (store location, port) is bound in [ServerConfig] / `application.yml`.
 */
@SpringBootApplication
class KontinuanceApiApplication

// Spread is the idiomatic Spring Boot Kotlin entry point (runApplication(*args)); the copy is negligible.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<KontinuanceApiApplication>(*args)
}
