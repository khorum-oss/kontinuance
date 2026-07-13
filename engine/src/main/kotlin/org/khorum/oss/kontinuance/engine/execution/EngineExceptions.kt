package org.khorum.oss.kontinuance.engine.execution

import org.khorum.oss.kontinuance.dsl.model.StepDefinition

/**
 * Thrown when a pipeline fails validation **before any step executes** (FR-003): malformed
 * definitions, or — for the secret pre-flight — a required secret that cannot be resolved.
 */
open class PipelineValidationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Thrown when no registered [StepExecutor] supports a step's [StepDefinition]. */
class UnsupportedStepException(definition: StepDefinition) :
    PipelineValidationException("no executor registered for step definition: $definition")

/** Thrown during the secret pre-flight when a referenced secret cannot be resolved. */
class UnresolvedSecretException(secretName: String) :
    PipelineValidationException("required secret '$secretName' could not be resolved")
