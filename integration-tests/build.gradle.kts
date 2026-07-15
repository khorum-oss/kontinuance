import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

// Cross-cutting / heavier integration tests that drive the built :engine end-to-end, kept out of the
// engine module so they can grow and be tuned independently (mirrors relikquary's integration-tests
// module). This module ships NO runnable or publishable artifact — it only runs tests against :engine,
// and its coverage over engine's classes is aggregated into the root Kover report (see root
// build.gradle.kts). When a real external integration lands (003+), this is the home for the
// constitution's @SpringBootTest + Testcontainers integration tests.
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    // The system under test and the coroutine runtime its API is built on (engine exposes it as
    // `implementation`, which does not propagate to this module's compile classpath).
    testImplementation(project(":engine"))
    testImplementation(rootProject.libs.coroutines.core)
    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_21.majorVersion
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = JavaVersion.VERSION_21.majorVersion
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
