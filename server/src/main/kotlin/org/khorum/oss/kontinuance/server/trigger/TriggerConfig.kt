package org.khorum.oss.kontinuance.server.trigger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Beans for manual run triggering: the shared [PipelineEngine] (the same in-process engine used by the
 * CI service) and a long-lived [CoroutineScope] on which triggered runs execute in the background.
 *
 * The scope uses a [SupervisorJob] so one failed run never cancels the others, and [Dispatchers.IO]
 * because pipeline execution shells out to external tools (blocking I/O). It is application-scoped: the
 * request that starts a run returns immediately while the run continues on this scope.
 */
@Configuration
class TriggerConfig {

    @Bean
    fun pipelineEngine(): PipelineEngine = PipelineEngine.default()

    @Bean
    fun triggerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
