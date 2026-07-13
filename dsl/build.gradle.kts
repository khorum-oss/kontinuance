import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

val dslVersion: String by rootProject.extra

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
}

group = "org.khorum.oss.kontinuance"
version = dslVersion

dependencies {
    implementation(kotlin("stdlib"))
    implementation(rootProject.libs.kotlin.reflect)
    implementation(rootProject.libs.konstellation.meta.dsl)
    ksp(rootProject.libs.konstellation.dsl)

    testImplementation(project(":core-test"))
    testImplementation(rootProject.libs.mockk)
    testImplementation(rootProject.libs.coroutines.test)
}

ksp {
    arg("projectRootClasspath", "org.khorum.oss.kontinuance.dsl")
    arg("dslBuilderClasspath", "org.khorum.oss.kontinuance.dsl.common")
    arg("dslMarkerClass", "org.khorum.oss.kontinuance.dsl.common.KontinuanceDsl")
    arg("rootDslFileClasspath", "org.khorum.oss.kontinuance.dsl.common")
}

tasks.jar {
    archiveBaseName.set("kontinuance-dsl")
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
