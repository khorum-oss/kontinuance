rootProject.name = "kontinuance"

pluginManagement {
    repositories {
        // dependency.env selects internal vs. public resolution (stage|dev|prod|public); CI passes
        // -Pdependency.env=public. The open-reliquary CDN hosts the khorum plugins + Konstellation and
        // is required in every selection. A non-public selection routes through proxy.location when it
        // is set, otherwise falls back to the public repositories so a build without an internal proxy
        // still resolves.
        val depEnv = providers.gradleProperty("dependency.env").orNull ?: "stage"
        val proxyLocation = providers.gradleProperty("proxy.location").orNull
        maven { url = uri("https://open-reliquary.nyc3.cdn.digitaloceanspaces.com") }
        if (depEnv != "public" && proxyLocation != null) {
            maven { url = uri(proxyLocation) }
        } else {
            gradlePluginPortal()
            mavenCentral()
        }
    }
}

includeModules(
    "core-test",
    "dsl",
    "engine",
    "github",
    "persistence",
    "server",
    "integration-tests"
)

class Module(private val moduleName: String) {
    override fun toString(): String = moduleName
}

fun includeModules(vararg modules: Any) {
    val ids = modules.asSequence().flatMap { it.asStrings() }.toList()

    logger.debug("including: {}", ids)
    include(ids)
}

fun Any.asStrings(): Sequence<String> = when (this) {
    is String -> sequenceOf(this)
    is List<*> -> this.asSequence().map { it.toString() }
    else -> sequenceOf(toString())
}

fun String.subModules(vararg subModulesIds: String): List<Module> {
    val subModules = subModulesIds.map { Module("$this:$it") }

    return listOf(Module(this)) + subModules
}