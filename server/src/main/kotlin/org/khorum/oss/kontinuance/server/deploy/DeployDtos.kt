package org.khorum.oss.kontinuance.server.deploy

/**
 * `/api/deploy` DTOs (022, run-derived), serialized by Jackson. The artifact list is always empty —
 * Kontinuance runs no artifact registry (publishing is a pipeline step) — and the shape says so honestly.
 */
data class DeployResponse(
    val nodes: List<DeployNode>,
    val artifacts: List<DeployArtifact>,
    val environment: DeployEnvironment,
)

data class DeployNode(val id: String, val label: String, val title: String, val status: String, val meta: String)

data class DeployArtifact(val kind: String, val name: String, val digest: String, val state: String)

data class DeployEnvironment(val podsReady: String, val syncRevision: String, val health: String, val meta: String)
