import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.plugins.JavaApplication

// The read API: a small long-running HTTP service exposing the run history (:persistence RunStore) so
// the Web UI and operators can list/inspect runs. Spring-free first increment on the JDK HttpServer,
// no new external dependency (JSON via the catalog's serialization-json). Read logic (RunApi) is kept
// separate from the transport (HttpApiServer) so a Spring/SSE layer can wrap it later.
plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":persistence"))
    implementation(rootProject.libs.serialization.json)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
}

// The `application` plugin is applied to every module by the root build; point this module's launcher
// at the API server main so `./gradlew :server:run` and installDist work.
configure<JavaApplication> {
    applicationName = "kontinuance-api"
    mainClass.set("org.khorum.oss.kontinuance.server.cli.ApiServerMainKt")
}

// `./gradlew :server:install` installs the `kontinuance-api` launcher into ~/.local (mirrors the
// `:engine:install` / `:github:install` convention).
tasks.register("install") {
    group = "distribution"
    description = "Install the `kontinuance-api` read-API server into ~/.local (lib + bin wrapper)."
    dependsOn("installDist")
    doLast {
        val home = System.getProperty("user.home")
        val lib = file("$home/.local/lib/kontinuance-api")
        lib.deleteRecursively()
        copy {
            from(layout.buildDirectory.dir("install/kontinuance-api"))
            into(lib)
        }
        val bin = file("$home/.local/bin").apply { mkdirs() }
        val wrapper = file("$bin/kontinuance-api")
        wrapper.writeText("#!/usr/bin/env bash\nexec \"$home/.local/lib/kontinuance-api/bin/kontinuance-api\" \"\$@\"\n")
        wrapper.setExecutable(true)
        println("installed: $wrapper -> $lib/bin/kontinuance-api")
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
