// Minimal, self-contained artifact to demonstrate publishing through a Kontinuance pipeline.
// Uses ONLY core Gradle plugins (java-library + maven-publish) so it builds and publishes offline
// with no external plugin or dependency downloads. Replace this with your own project in practice —
// the Kontinuance descriptor only drives its `build` and `publish` tasks.
plugins {
    `java-library`
    `maven-publish`
}

group = "com.example.kontinuance"
version = System.getenv("ARTIFACT_VERSION")?.takeIf { it.isNotBlank() } ?: "0.1.0"

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "target"
            // Target repository comes from the environment the Kontinuance step injects (as a secret).
            // Falls back to a local build-dir repo so `gradle publish` works even without configuration.
            val configured = System.getenv("PUBLISH_REPO_URL")?.takeIf { it.isNotBlank() }
            url = uri(configured ?: layout.buildDirectory.dir("local-repo").get().asFile.toURI().toString())

            val user = System.getenv("PUBLISH_REPO_USER")
            val pass = System.getenv("PUBLISH_REPO_PASSWORD")
            // http(s) repositories need credentials; a file:// repo does not.
            if (!user.isNullOrBlank()) {
                credentials {
                    username = user
                    password = pass
                }
            }
        }
    }
}
