package org.khorum.oss.kontinuance.github.cli

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.github.config.EventSourceConfig
import org.khorum.oss.kontinuance.github.poll.InMemoryCursorStore
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EventSourceRunnerTest {

    private val config = EventSourceConfig.parse(
        """
        eventSource:
          tokenEnv: "GH_TOKEN"
          repositories:
            - owner: "o"
              name: "r"
              prPipeline: "pr.yaml"
        """.trimIndent(),
    )

    @Test
    fun `wires an event source when the token env var is set`() {
        val source = eventSourceFrom(config, InMemoryCursorStore()) { name ->
            if (name == "GH_TOKEN") "a-token" else null
        }
        assertNotNull(source)
    }

    @Test
    fun `fails fast when the configured token env var is not set`() {
        assertFailsWith<IllegalStateException> {
            eventSourceFrom(config, InMemoryCursorStore()) { null }
        }
    }
}
