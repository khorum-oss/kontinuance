package org.khorum.oss.kontinuance.server.coverage

/**
 * `/api/coverage` DTOs (Kover projection, 031), serialized by Jackson. A module's per-class breakdown is
 * `null` for fixture data (omitted on the wire); the real reader always supplies it.
 */
data class CoverageResponse(
    val tool: String,
    val line: CoverageMetric,
    val branch: CoverageMetric,
    val classes: Int,
    val modules: List<CoverageModule>,
)

data class CoverageMetric(val pct: String, val covered: Int, val total: Int)

data class CoverageModule(
    val name: String,
    val kind: String,
    val linePct: Int,
    val branchPct: Int,
    val missed: Int,
    val classes: List<CoverageClass>? = null,
)

data class CoverageClass(val name: String, val linePct: Int, val branchPct: Int, val missed: Int)
