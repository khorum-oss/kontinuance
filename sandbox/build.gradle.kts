// A deliberately tiny, self-contained Gradle application: it uses ONLY core Gradle plugins and has NO
// external dependencies, so it builds and tests fully offline with no artifact downloads — the point is to
// show a Kontinuance pipeline check out real code and run its `build` and `test` tasks, not to exercise a
// dependency graph. Replace it with your own project in practice; the descriptor only drives its tasks.
plugins {
    application
}

group = "com.example.sandbox"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.example.sandbox.App")
}

// The project's "tests": a zero-dependency self-check that asserts the Calculator's behaviour and exits
// non-zero on any failure (so a broken change fails the Kontinuance `test` step). Wired into `check` so a
// plain `gradle build` also runs it. For a direct local run, `FAIL_SANDBOX=true gradle selfTest` forces a
// failing run (the forked JavaExec inherits the environment) — or just break Calculator to see it go red.
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs the sandbox self-checks (a zero-dependency stand-in for a test suite)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.sandbox.SelfTest")
}

tasks.named("check") {
    dependsOn("selfTest")
}
