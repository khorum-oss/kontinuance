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

group = "org.khorum.oss.REPLACE_ME"

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
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
        maven { url = uri("https://open-reliquary.nyc3.cdn.digitaloceanspaces.com") }
    }
}

tasks.register("koverMergedReport") {
    group = "verification"
    description = "Generates coverage report for the dsl module"

    dependsOn(project(":dsl").tasks.named("koverXmlReport"))
}

tasks.register("initProject") {
    group = "setup"
    description = "Replaces REPLACE_ME, REPLACE_ME_PACKAGE, and REPLACE_ME_CAPITAL across the template"

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
                .replace("REPLACE_ME_CAPITAL", projectCapitalName)
                .replace("REPLACE_ME_PACKAGE", projectPackageName)
                .replace("REPLACE_ME", projectName)

            if (updated != original) {
                file.writeText(updated)
                logger.lifecycle("Updated: ${file.relativeTo(rootProject.projectDir)}")
            }
        }

        // Rename files and directories containing placeholders
        rootProject.projectDir.walkBottomUp()
            .filter { "REPLACE_ME" in it.name }
            .filter { ".gradle/" !in it.path && "/build/" !in it.path }
            .forEach { file ->
                val newName = file.name
                    .replace("REPLACE_ME_CAPITAL", projectCapitalName)
                    .replace("REPLACE_ME_PACKAGE", projectPackageName)
                    .replace("REPLACE_ME", projectName)
                val target = file.parentFile.resolve(newName)
                file.renameTo(target)
                logger.lifecycle("Renamed: ${file.relativeTo(rootProject.projectDir)} -> $newName")
            }

        logger.lifecycle("Project initialized: name=$projectName, package=$projectPackageName, capitalName=$projectCapitalName")
    }
}

sonar {
    properties {
        property("sonar.projectKey", "khorum-oss_REPLACE_ME")
        property("sonar.organization", "khorum-oss")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${project(":dsl").layout.buildDirectory.get()}/reports/kover/report.xml"
        )
    }
}