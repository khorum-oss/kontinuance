package org.khorum.oss.kontinuance.server.domain.project

/**
 * `/api/projects` DTOs (032/033), serialized by Jackson. `repo`/`branch` are the project's optional source
 * (null → omitted on the wire).
 */
data class ProjectsResponse(val active: String?, val projects: List<ProjectDto>)

data class ProjectDto(val name: String, val active: Boolean, val repo: String? = null, val branch: String? = null)

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
