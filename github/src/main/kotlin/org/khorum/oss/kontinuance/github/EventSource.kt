package org.khorum.oss.kontinuance.github

import kotlinx.coroutines.delay
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.khorum.oss.kontinuance.engine.execution.PipelineEngine
import org.khorum.oss.kontinuance.engine.model.Run
import org.khorum.oss.kontinuance.engine.secret.EnvSecretSource
import org.khorum.oss.kontinuance.engine.secret.SecretSource
import org.khorum.oss.kontinuance.github.client.GitHubApiException
import org.khorum.oss.kontinuance.github.poll.Poller
import org.khorum.oss.kontinuance.github.report.RunReporter
import org.khorum.oss.kontinuance.github.trigger.TriggerEvent
import org.khorum.oss.kontinuance.github.trigger.TriggerResolver
import java.nio.file.Path

/**
 * The external-CI loop (US1): poll GitHub for new PR head commits, run each repository's pipeline via
 * the 001 [PipelineEngine], and report the outcome back as a commit status (`pending → success |
 * failure`). The event's SHA is injected into the run as the `KONTINUANCE_SHA` secret, so pipeline
 * steps deliver exactly the observed commit. Engine-only and Spring-free — the poll cadence is a
 * coroutine loop, not `@Scheduled`.
 *
 * @param poller the event source.
 * @param resolver maps an event to the descriptor to run (or `null` to ignore it).
 * @param reporter posts commit statuses.
 * @param engine the 001 engine (default in-process engine).
 * @param baseSecrets the underlying secret source for pipeline steps (env by default); `KONTINUANCE_SHA`
 *   is overlaid per run from the event.
 */
class EventSource(
    private val poller: Poller,
    private val resolver: TriggerResolver,
    private val reporter: RunReporter,
    private val engine: PipelineEngine = PipelineEngine.default(),
    private val baseSecrets: SecretSource = EnvSecretSource(),
) {

    /**
     * Runs one poll cycle: for each new event with a configured pipeline, posts `pending`, runs the
     * pipeline, and posts the terminal status. Returns the runs started this cycle. Events for
     * unconfigured repos/refs are skipped silently (no status posted).
     */
    suspend fun pollAndRun(): List<Run> {
        val runs = mutableListOf<Run>()
        for (event in poller.poll()) {
            val descriptor = resolver.resolve(event) ?: continue
            reporter.reportPending(event.repo, event.sha)
            val run = runPipeline(descriptor, event)
            reporter.reportOutcome(event.repo, event.sha, run)
            runs += run
        }
        return runs
    }

    /**
     * Long-running operational loop: [pollAndRun] on [intervalMillis] cadence. A [GitHubApiException]
     * (rate limit / transient outage) backs off by its `Retry-After` (or the interval) rather than
     * hot-looping; other errors propagate. Runs until the coroutine is cancelled.
     */
    suspend fun runForever(intervalMillis: Long) {
        while (true) {
            val waitMillis = try {
                pollAndRun()
                intervalMillis
            } catch (e: GitHubApiException) {
                maxOf(intervalMillis, (e.retryAfterSeconds ?: 0L) * MILLIS_PER_SECOND)
            }
            delay(waitMillis)
        }
    }

    private suspend fun runPipeline(path: Path, event: TriggerEvent): Run {
        val pipeline = PipelineDescriptor.load(path)
        return engine.run(pipeline, withSha(event.sha))
    }

    private fun withSha(sha: String): SecretSource =
        SecretSource { name -> if (name == KONTINUANCE_SHA) sha else baseSecrets.resolve(name) }

    companion object {
        /** The immutable commit under delivery, injected into every run from the trigger event. */
        const val KONTINUANCE_SHA = "KONTINUANCE_SHA"
        private const val MILLIS_PER_SECOND = 1000L
    }
}
