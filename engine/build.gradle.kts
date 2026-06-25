import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

val dslVersion: String by rootProject.extra

plugins {
    id("io.gitlab.arturbosch.detekt")
}

group = "org.khorum.oss.kontinuance"
version = dslVersion

dependencies {
    implementation(kotlin("stdlib"))
    implementation(rootProject.libs.kotlin.reflect)
    implementation(rootProject.libs.coroutines.core)
    implementation(rootProject.libs.konstellation.meta.dsl)
    implementation(rootProject.libs.snakeyaml.engine)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
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
