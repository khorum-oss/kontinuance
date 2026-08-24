package org.khorum.oss.kontinuance.server.domain.project

/**
 * `/api/projects` DTOs (032/033), serialized by Jackson. `repo`/`branch` are the project's optional source
 * (null → omitted on the wire).
 */
data class ProjectsResponse(val active: String?, val projects: List<ProjectDto>)

/**
 * A project on the wire. `derived` marks an entry computed from run history rather than a registered
 * descriptor (039); `runnable` is false exactly when there is no stored descriptor to run. The run
 * statistics come from the derivation window and are `0`/`null` for a project with no recorded runs.
 */
data class ProjectDto(
    val name: String,
    val active: Boolean,
    val repo: String? = null,
    val branch: String? = null,
    val derived: Boolean = false,
    val runnable: Boolean = true,
    val runCount: Int = 0,
    val lastStatus: String? = null,
    val lastRunAt: String? = null,
)

/** `POST /api/projects` body: a new project's name + descriptor + optional source. */
data class CreateProjectRequest(
    val name: String? = null,
    val text: String? = null,
    val repo: String? = null,
    val branch: String? = null,
)

/** `POST /api/projects/{name}/source` body. */
data class SourceRequest(val repo: String? = null, val branch: String? = null)

/** `POST /api/projects` response: the created project's name. */
data class CreatedProject(val name: String)

/** `POST /api/projects/{name}/activate` response: the now-active project's name. */
data class ActiveProject(val active: String)

/** `POST /api/projects/{name}/source` response: the project's stored source. */
data class ProjectSourceResponse(val name: String, val repo: String? = null, val branch: String? = null)
