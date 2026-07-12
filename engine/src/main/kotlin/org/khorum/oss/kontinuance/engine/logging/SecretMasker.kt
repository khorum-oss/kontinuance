package org.khorum.oss.kontinuance.engine.logging

/**
 * Replaces any registered secret value with a fixed mask before a log line is emitted.
 *
 * Masking happens at the single emission point so a registered secret can never appear
 * unmasked in streamed output (SC-003). Longer values are masked first so that a secret
 * which contains another secret as a substring is still fully redacted.
 *
 * @param secrets the sensitive values to redact; blank values are ignored.
 * @param mask the replacement token.
 */
class SecretMasker(
    secrets: Collection<String>,
    private val mask: String = MASK,
) {
    private val values: List<String> = secrets
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }

    /** True when there is nothing to redact. */
    val isEmpty: Boolean get() = values.isEmpty()

    /** Returns [line] with every registered secret value replaced by the mask. */
    fun mask(line: String): String {
        if (values.isEmpty()) return line
        var result = line
        for (value in values) {
            result = result.replace(value, mask)
        }
        return result
    }

    private companion object {
        const val MASK = "***"
    }
}
