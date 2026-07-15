import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.plugins.JavaApplication

// The GitHub event-source module: polls GitHub (outbound only), starts pipeline runs via the :engine,
// and reports outcomes back as commit statuses. Implemented engine-only and Spring-free (the poll loop
// is a coroutine loop; Spring/DI/HTTP-server belongs to the future Server/API feature). No new external
// dependency — the client uses JDK 21's java.net.http.HttpClient, and JSON/YAML reuse the catalog's
// serialization-json + snakeyaml-engine (already used by :engine, already verified).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":engine"))
    implementation(rootProject.libs.coroutines.core)
    implementation(rootProject.libs.serialization.json)
    implementation(rootProject.libs.snakeyaml.engine)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
}

// The `application` plugin is applied to every module by the root build; point this module's launcher
// at the event-source runner so `./gradlew :github:run --args="<config.yaml>"` and installDist work.
configure<JavaApplication> {
    applicationName = "kontinuance-ci"
    mainClass.set("org.khorum.oss.kontinuance.github.cli.EventSourceRunnerKt")
}

// `./gradlew :github:install` installs the `kontinuance-ci` service launcher into ~/.local (mirrors the
// `:engine:install` convention for the `kontinuance` CLI).
tasks.register("install") {
    group = "distribution"
    description = "Install the `kontinuance-ci` event-source launcher into ~/.local (lib + bin wrapper)."
    dependsOn("installDist")
    doLast {
        val home = System.getProperty("user.home")
        val lib = file("$home/.local/lib/kontinuance-ci")
        lib.deleteRecursively()
        copy {
            from(layout.buildDirectory.dir("install/kontinuance-ci"))
            into(lib)
        }
        val bin = file("$home/.local/bin").apply { mkdirs() }
        val wrapper = file("$bin/kontinuance-ci")
        wrapper.writeText("#!/usr/bin/env bash\nexec \"$home/.local/lib/kontinuance-ci/bin/kontinuance-ci\" \"\$@\"\n")
        wrapper.setExecutable(true)
        println("installed: $wrapper -> $lib/bin/kontinuance-ci")
    }
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
