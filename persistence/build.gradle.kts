import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

// Run-history persistence: a durable RunStore (metadata/status of past runs) + the consolidated poll
// cursor, behind small interfaces with two backends — the original file store and an embedded SQLite
// database (041). Engine-only, Spring-free; records serialize as JSON via the catalog's
// serialization-json runtime (no compiler plugin).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":engine"))
    implementation(rootProject.libs.serialization.json)
    // Embedded SQLite backend (041), behind the RunStore/RunLogStore seams; the file default stays.
    implementation(rootProject.libs.sqlite.jdbc)

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
