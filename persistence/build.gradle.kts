import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

// Run-history persistence: a durable RunStore (metadata/status of past runs) + the consolidated poll
// cursor, behind small interfaces so the Server/API feature can later swap the file default for a DB.
// Engine-only, Spring-free, no new external dependency — records are JSON via the catalog's
// serialization-json runtime (no compiler plugin).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":engine"))
    implementation(rootProject.libs.serialization.json)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
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
