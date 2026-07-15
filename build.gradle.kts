plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.sonarqube)
    application
    alias(libs.plugins.khorum.pipeline) apply false
    alias(libs.plugins.khorum.secrets) apply false
    alias(libs.plugins.khorum.maven.artifacts) apply false
    alias(libs.plugins.khorum.digital.ocean) apply false
}

group = "org.khorum.oss.kontinuance"

extra["dslVersion"] = file("VERSION").readText().trim()
extra["metaDslVersion"] = libs.versions.konstellation.meta.dsl.get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sharedRepositories()

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.dokka")
        plugin("application")
        plugin("org.jetbrains.kotlinx.kover")
    }

    sharedRepositories()

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(rootProject.libs.kotlin.reflect)
        implementation(rootProject.libs.kotlin.logging)

        testImplementation(kotlin("test"))
        testImplementation(rootProject.libs.junit.jupiter.api)
        testRuntimeOnly(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }
}

fun Project.sharedRepositories() {
    // dependency.env selects internal vs. public resolution (stage|dev|prod|public); CI passes
    // -Pdependency.env=public. The open-reliquary CDN (khorum plugins + Konstellation) is present in
    // every selection. A non-public selection routes through proxy.location when set, otherwise falls
    // back to the public repositories so a build without an internal proxy still resolves.
    val depEnv = providers.gradleProperty("dependency.env").orNull ?: "stage"
    val proxyLocation = providers.gradleProperty("proxy.location").orNull
    repositories {
        mavenLocal()
        maven { url = uri("https://open-reliquary.nyc3.cdn.digitaloceanspaces.com") }
        if (depEnv != "public" && proxyLocation != null) {
            maven { url = uri(proxyLocation) }
        } else {
            mavenCentral()
            google()
            maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
        }
    }
}

// Aggregate coverage from every production module into the single root Kover report that SonarCloud
// reads, so the quality gate measures the engine (where the code actually lives) and not only the
// near-empty dsl stub. core-test is a test-support module and is intentionally left out of the
// aggregate. New coverage-bearing modules (e.g. integration-tests) are added here.
dependencies {
    kover(project(":dsl"))
    kover(project(":engine"))
}

tasks.register("initProject") {
    group = "setup"
    description = "Replaces kontinuance, kontinuance, and Kontinuance across the template"

    doLast {
        val projectName = project.findProperty("projectName") as? String
            ?: error("Missing required property: -PprojectName=<name>")
        val projectPackageName = project.findProperty("projectPackageName") as? String
            ?: projectName
        val projectCapitalName = project.findProperty("projectCapitalName") as? String
            ?: projectPackageName.replaceFirstChar { it.uppercaseChar() }

        val targetFiles = rootProject.projectDir.walkTopDown()
            .filter { it.isFile }
            .filter {
                it.extension in listOf("kts", "kt", "md", "xml", "yaml", "yml", "properties", "toml")
            }
            .filter { ".gradle/" !in it.path && "/build/" !in it.path }
            .toList()

        targetFiles.forEach { file ->
            val original = file.readText()
            val updated = original
                .replace("Kontinuance", projectCapitalName)
                .replace("kontinuance", projectPackageName)
                .replace("kontinuance", projectName)

            if (updated != original) {
                file.writeText(updated)
                logger.lifecycle("Updated: ${file.relativeTo(rootProject.projectDir)}")
            }
        }

        // Rename files and directories containing placeholders
        rootProject.projectDir.walkBottomUp()
            .filter { "kontinuance" in it.name }
            .filter { ".gradle/" !in it.path && "/build/" !in it.path }
            .forEach { file ->
                val newName = file.name
                    .replace("Kontinuance", projectCapitalName)
                    .replace("kontinuance", projectPackageName)
                    .replace("kontinuance", projectName)
                val target = file.parentFile.resolve(newName)
                file.renameTo(target)
                logger.lifecycle("Renamed: ${file.relativeTo(rootProject.projectDir)} -> $newName")
            }

        logger.lifecycle("Project initialized: name=$projectName, package=$projectPackageName, capitalName=$projectCapitalName")
    }
}

sonar {
    properties {
        property("sonar.projectKey", "khorum-oss_kontinuance")
        property("sonar.organization", "khorum-oss")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/kover/report.xml"
        )
    }
}