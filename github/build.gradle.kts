import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

// The GitHub event-source module: polls GitHub (outbound only), starts pipeline runs via the :engine,
// and reports outcomes back as commit statuses. Implemented engine-only and Spring-free (the poll loop
// is a coroutine loop; Spring/DI/HTTP-server belongs to the future Server/API feature). No new runtime
// dependency — the client uses JDK 21's java.net.http.HttpClient and the catalog's serialization-json.
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":engine"))
    implementation(rootProject.libs.coroutines.core)
    implementation(rootProject.libs.serialization.json)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy("org.khorum.oss.kontinuance.engine.common.ExcludeFromCoverage")
            }
        }
    }
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
