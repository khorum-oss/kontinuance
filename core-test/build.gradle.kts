
plugins {
    kotlin("plugin.serialization") version "2.0.20"
}

dependencies {
    implementation(rootProject.libs.serialization.json)
    implementation(rootProject.libs.coroutines.core)
    implementation(kotlin("test"))
    implementation(rootProject.libs.junit.jupiter.api.core.test)
    implementation(rootProject.libs.mockk)
}

tasks.withType<Test> {
    kover {
        isEnabled = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
