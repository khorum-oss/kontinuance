package org.khorum.oss.kontinuance.engine.logging

/**
 * A [LogSink] decorator that passes every line through a [SecretMasker] before delegating.
 *
 * Wrapping at the sink guarantees masking is applied uniformly to all executors regardless of
 * how they produce output (FR-011, SC-003).
 *
 * @param masker the masker applied to each line.
 * @param delegate the downstream sink that receives the masked line.
 */
class MaskingLogSink(
    private val masker: SecretMasker,
    private val delegate: LogSink,
) : LogSink {
    override fun emit(line: String) {
        delegate.emit(masker.mask(line))
    }
}
