package org.khorum.oss.kontinuance.server.store

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.KontinuanceApiApplication
import org.springframework.boot.SpringApplication
import kotlin.test.assertTrue

/**
 * A misspelled `KONTINUANCE_STORE_BACKEND` refuses to start (042).
 *
 * The alternative — falling back to the default — would start the server against a store the operator
 * did not choose, and they would discover it from a dashboard showing no history rather than from a
 * message. Same stance as refusing to start on a half-configured credential pair (016).
 */
class UnknownStoreBackendIT {

    @Test
    fun `startup fails and the message names the bad value and the valid ones`() {
        val failure = runCatching {
            SpringApplication.run(
                KontinuanceApiApplication::class.java,
                "--server.port=0",
                "--kontinuance.store.backend=postgres",
                "--kontinuance.github.autostart=false",
            ).close()
        }.exceptionOrNull()

        val message = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        assertTrue(failure != null, "the context should have failed to start")
        assertTrue(message.contains("postgres"), "the message should name the bad value, was: $message")
        assertTrue(message.contains("sqlite"), "the message should list what is valid, was: $message")
    }
}
