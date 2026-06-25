package org.khorum.oss.kontinuance.engine.descriptor

import org.khorum.oss.kontinuance.engine.execution.PipelineValidationException

/**
 * Raised when a YAML descriptor cannot be parsed or validated into a pipeline model.
 *
 * The message identifies the offending location (e.g. `pipeline.stages[0].steps[1].run`) so the
 * author can find it; per FR-003 a descriptor error means **no step executes**.
 */
class DescriptorException(
    message: String,
    cause: Throwable? = null,
) : PipelineValidationException(message, cause)
