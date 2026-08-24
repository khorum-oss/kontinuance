package org.khorum.oss.kontinuance.server.flow.auth

import org.junit.jupiter.api.Test
import org.khorum.oss.kontinuance.server.service.AuthCredentials
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertTrue

/**
 * The startup guarantee, at the Spring boundary rather than the bean's: a misconfigured credential pair must
 * fail the **application context**, not merely throw somewhere inside it. That distinction is the whole point
 * — a bean exception Spring swallowed or logged would still leave a server running unauthenticated. Here the
 * context itself refuses to come up, which in a container means the process exits before binding a port.
 */
class AuthStartupIT {

    private val contexts = ApplicationContextRunner().withUserConfiguration(AuthCredentials::class.java)

    @Test
    fun `a half-configured credential pair fails the application context`() {
        contexts.withPropertyValues("kontinuance.auth.username=operator").run { context ->
            assertTrue(context.startupFailure != null, "context should have refused to start")
            assertTrue(context.startupFailure!!.stackTraceToString().contains("kontinuance.auth.password"))
        }
    }

    @Test
    fun `requiring auth with no credentials fails the application context`() {
        contexts.withPropertyValues("kontinuance.auth.required=true").run { context ->
            assertTrue(context.startupFailure != null, "context should have refused to start")
            assertTrue(context.startupFailure!!.stackTraceToString().contains("kontinuance.auth.required"))
        }
    }

    @Test
    fun `a fully configured credential pair starts`() {
        contexts
            .withPropertyValues(
                "kontinuance.auth.username=operator",
                "kontinuance.auth.password=s3cret",
                "kontinuance.auth.required=true",
            )
            .run { context ->
                assertTrue(context.startupFailure == null)
                assertTrue(context.getBean(AuthCredentials::class.java).enabled)
            }
    }

    @Test
    fun `no credentials and no requirement still starts open`() {
        contexts.run { context ->
            assertTrue(context.startupFailure == null)
            assertTrue(!context.getBean(AuthCredentials::class.java).enabled)
        }
    }
}
