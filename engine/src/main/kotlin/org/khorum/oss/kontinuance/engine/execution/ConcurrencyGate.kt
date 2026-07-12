package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Caps the number of step executions that may run simultaneously at K permits (FR-013, SC-006).
 *
 * Backed by a coroutine [Semaphore]: a step that cannot acquire a permit suspends (without
 * busy-waiting) until one frees, so the number of concurrently RUNNING steps never exceeds K.
 *
 * @param permits the concurrency cap K; must be >= 1.
 */
class ConcurrencyGate(permits: Int) {

    init {
        require(permits >= 1) { "concurrency permits must be >= 1, was $permits" }
    }

    private val semaphore = Semaphore(permits)

    /** Runs [block] while holding a permit, releasing it on completion or failure. */
    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}
