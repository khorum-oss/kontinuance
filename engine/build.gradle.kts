import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.plugins.JavaApplication

val dslVersion: String by rootProject.extra

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
}

group = "org.khorum.oss.kontinuance"
version = dslVersion

// The `application` plugin is applied to every module by the root build; point the engine's runner at
// the CLI entry point so `./gradlew :engine:run --args="<descriptor.yaml>"` (and installDist) work.
// applicationName drives the launcher + installDist dir name (`kontinuance`, not `engine`).
configure<JavaApplication> {
    applicationName = "kontinuance"
    mainClass.set("org.khorum.oss.kontinuance.engine.cli.RunnerKt")
}

// `./gradlew :engine:install` installs the `kontinuance` CLI into ~/.local: the app distribution under
// ~/.local/lib/kontinuance + a wrapper on ~/.local/bin (ensure ~/.local/bin is on your PATH). Mirrors the
// logos install convention.
tasks.register("install") {
    group = "distribution"
    description = "Install the `kontinuance` CLI into ~/.local (lib + bin wrapper)."
    dependsOn("installDist")
    doLast {
        val home = System.getProperty("user.home")
        val lib = file("$home/.local/lib/kontinuance")
        lib.deleteRecursively()
        copy {
            from(layout.buildDirectory.dir("install/kontinuance"))
            into(lib)
        }
        val bin = file("$home/.local/bin").apply { mkdirs() }
        val wrapper = file("$bin/kontinuance")
        wrapper.writeText("#!/usr/bin/env bash\nexec \"$home/.local/lib/kontinuance/bin/kontinuance\" \"\$@\"\n")
        wrapper.setExecutable(true)
        println("installed: $wrapper -> $lib/bin/kontinuance")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(rootProject.libs.kotlin.reflect)
    implementation(rootProject.libs.coroutines.core)
    implementation(rootProject.libs.konstellation.meta.dsl)
    ksp(rootProject.libs.konstellation.dsl)
    implementation(rootProject.libs.snakeyaml.engine)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
}

ksp {
    arg("projectRootClasspath", "org.khorum.oss.kontinuance.engine")
    arg("dslBuilderClasspath", "org.khorum.oss.kontinuance.engine.dsl")
    arg("dslMarkerClass", "org.khorum.oss.kontinuance.engine.dsl.KontinuanceDsl")
    arg("rootDslFileClasspath", "org.khorum.oss.kontinuance.engine.dsl")
}

tasks.jar {
    archiveBaseName.set("engine")
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
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
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
