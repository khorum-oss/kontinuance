package org.khorum.oss.kontinuance.engine.descriptor

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parses a descriptor duration such as `"5m"`, `"30s"`, `"500ms"`, or `"2h"` into a [Duration].
 *
 * @param raw the textual duration.
 * @param location the descriptor location, used in error messages.
 * @throws DescriptorException when the value is malformed or not strictly positive.
 */
internal fun parseDescriptorDuration(raw: String, location: String): Duration {
    val match = DURATION_PATTERN.matchEntire(raw.trim())
    val duration = match?.let {
        val amount = it.groupValues[1].toLong()
        when (it.groupValues[2]) {
            "ms" -> amount.milliseconds
            "s" -> amount.seconds
            "m" -> amount.minutes
            "h" -> amount.hours
            else -> null
        }
    }
    if (duration == null || !duration.isPositive()) {
        throw DescriptorException(
            "$location: invalid duration '$raw' (expected a positive value like '500ms', '30s', '5m', '2h')",
        )
    }
    return duration
}

private val DURATION_PATTERN = Regex("""(\d+)\s*(ms|s|m|h)""")
