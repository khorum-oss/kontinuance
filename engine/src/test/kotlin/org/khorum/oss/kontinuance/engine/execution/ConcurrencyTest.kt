package org.khorum.oss.kontinuance.engine.execution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrencyTest {

    @Test
    fun `the gate never lets simultaneously running tasks exceed K`() = runBlocking {
        val cap = 3
        val gate = ConcurrencyGate(cap)
        val active = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        val tasks = (1..20).map {
            async(Dispatchers.Default) {
                gate.withPermit {
                    val current = active.incrementAndGet()
                    maxObserved.updateAndGet { observed -> max(observed, current) }
                    delay(WORK_MS)
                    active.decrementAndGet()
                }
            }
        }
        tasks.awaitAll()

        assertTrue(maxObserved.get() <= cap, "observed ${maxObserved.get()} simultaneously running, cap is $cap")
        // With 20 tasks competing for 3 permits, the cap should actually be reached.
        assertEquals(cap, maxObserved.get())
    }

    private companion object {
        const val WORK_MS = 50L
    }
}
