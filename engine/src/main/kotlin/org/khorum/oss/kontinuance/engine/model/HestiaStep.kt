package org.khorum.oss.kontinuance.engine.model

/**
 * A khorum delivery tool a [HestiaStep] fronts, with the host binary and conventional subcommand each
 * wraps. These are the named-but-external delivery steps (roadmap item 7): `render`→**zosn**,
 * `deploy`→**logos**, `UAT`→**euri** (Playwright). The step supplies the tool and a pass-through argument
 * list; the exact flag surface is the real tool's, so `args` is the escape hatch for anything tool-specific.
 */
enum class HestiaTool(val binary: String, val command: String) {
    RENDER("zosn", "render"),
    DEPLOY("logos", "deploy"),
    UAT("euri", "test"),
}

/**
 * Runs a khorum delivery tool (`zosn`/`logos`/`euri`) as a first-class step, executed through the shared
 * `ProcessBuilder` path by [org.khorum.oss.kontinuance.engine.execution.steps.HestiaStepExecutor] — so it
 * inherits the same isolation, secret masking, timeout, and (024) optional container runner as every other
 * step. The invocation is `<binary> <command> <args…>`; [args] is passed through verbatim.
 *
 * Prefer the [render] / [deploy] / [uat] factories (and the `renderStep`/`deployStep`/`uatStep` DSL) over
 * the primary constructor.
 *
 * @param tool which delivery tool to invoke.
 * @param args extra arguments appended after the tool's subcommand.
 */
data class HestiaStep(
    val tool: HestiaTool,
    val args: List<String> = emptyList(),
) : StepDefinition {

    companion object {
        /** A `zosn render …` step (manifest rendering). */
        fun render(args: List<String> = emptyList()): HestiaStep = HestiaStep(HestiaTool.RENDER, args)

        /** A `logos deploy …` step (delivery). */
        fun deploy(args: List<String> = emptyList()): HestiaStep = HestiaStep(HestiaTool.DEPLOY, args)

        /** A `euri test …` step (Playwright-based UAT). */
        fun uat(args: List<String> = emptyList()): HestiaStep = HestiaStep(HestiaTool.UAT, args)
    }
}
