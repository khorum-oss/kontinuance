import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.plugins.JavaApplication

// The read API, now on the platform runtime the constitution names: a Spring Boot 4.1 application
// (WebFlux + actuator) with Kotlin coroutines. Suspend @RestController handlers reuse the transport-
// agnostic read logic (RunApi over the :persistence RunStore) behind a suspend RunReadFacade that
// offloads the blocking file store with withContext(Dispatchers.IO). Versions come from the Spring Boot
// BOM (platform) and the app runs via the `application` plugin's plain `main` (runApplication) rather
// than the Spring Boot Gradle plugin — that plugin drags antlr/jna/opentelemetry/tomlj/httpclient5 onto
// the build classpath, which would widen the verification trust set. Managing versions via the BOM and
// opening classes with kotlin-spring (already under the org.jetbrains trust) keeps dependency
// verification at the empirically-probed group-trust set (gradle/verification-metadata.xml; research
// 008 R2), never disabled (Principle V). A `bootJar` fat-jar is not needed — installDist ships the full
// classpath and a Spring Boot app boots fine from a normal classpath.
plugins {
    id("io.gitlab.arturbosch.detekt")
    alias(libs.plugins.kotlin.spring)
}

group = "org.khorum.oss.kontinuance"

dependencies {
    implementation(project(":persistence"))
    implementation(rootProject.libs.spring.boot.starter.webflux)
    implementation(rootProject.libs.spring.boot.starter.actuator)
    implementation(rootProject.libs.serialization.json)
    implementation(rootProject.libs.coroutines.core)
    // Bridges suspend @RestController handlers to Reactor Mono (Spring's CoroutinesUtils needs it).
    implementation(rootProject.libs.coroutines.reactor)

    testImplementation(rootProject.libs.spring.boot.starter.test)
    testImplementation(rootProject.libs.coroutines.test)
    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
}

// The `application` plugin is applied to every module by the root build; the Spring Boot plugin uses its
// mainClass for `bootRun`/`bootJar`. Point the launcher at the Spring Boot application main.
configure<JavaApplication> {
    applicationName = "kontinuance-api"
    mainClass.set("org.khorum.oss.kontinuance.server.KontinuanceApiApplicationKt")
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
