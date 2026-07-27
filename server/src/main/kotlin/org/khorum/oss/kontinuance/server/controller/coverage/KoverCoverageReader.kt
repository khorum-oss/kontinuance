package org.khorum.oss.kontinuance.server.controller.coverage

import org.khorum.oss.kontinuance.server.domain.CoverageClass
import org.khorum.oss.kontinuance.server.domain.CoverageMetric
import org.khorum.oss.kontinuance.server.domain.CoverageModule
import org.khorum.oss.kontinuance.server.domain.CoverageResponse
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * Parses a Kover coverage report (JaCoCo XML format, e.g. `build/reports/kover/report.xml`) into the
 * `/api/coverage` contract shape ([CoverageResponse]). Modules are derived from the package name segment
 * after `kontinuance` (the repo's module packages are `…kontinuance.<module>`), so the aggregated report
 * yields per-module line/branch coverage plus a per-class breakdown. Returns `null` when the report is
 * absent or unparseable, so the controller can fall back to fixture data.
 *
 * No new dependency: the JDK DOM parser is used with external entities disabled (XXE-safe).
 */
object KoverCoverageReader {

    private const val PERCENT = 100.0

    private data class Counter(val missed: Int, val covered: Int) {
        val total: Int get() = missed + covered
        fun pct(): Double = if (total == 0) 0.0 else covered * PERCENT / total
    }

    private class ClassCov(val name: String, val line: Counter, val branch: Counter)

    private class Mod {
        var line = Counter(0, 0)
        var branch = Counter(0, 0)
        val classes = ArrayList<ClassCov>()
        fun add(l: Counter?, b: Counter?) {
            if (l != null) line = Counter(line.missed + l.missed, line.covered + l.covered)
            if (b != null) branch = Counter(branch.missed + b.missed, branch.covered + b.covered)
        }
    }

    fun read(path: Path): CoverageResponse? {
        if (!Files.isRegularFile(path)) return null
        return runCatching { parse(path) }.getOrNull()
    }

    private fun parse(path: Path): CoverageResponse {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val report = Files.newInputStream(path).use { factory.newDocumentBuilder().parse(it) }.documentElement

        var totalLine = Counter(0, 0)
        var totalBranch = Counter(0, 0)
        var classes = 0
        val modules = LinkedHashMap<String, Mod>()

        for (pkg in report.children("package")) {
            val moduleName = moduleOf(pkg.getAttribute("name"))
            val mod = modules.getOrPut(moduleName) { Mod() }
            mod.add(pkg.counter("LINE"), pkg.counter("BRANCH"))
            for (cls in pkg.children("class")) {
                classes += 1
                mod.classes.add(
                    ClassCov(
                        name = classDisplayName(cls.getAttribute("name"), moduleName),
                        line = cls.counter("LINE") ?: Counter(0, 0),
                        branch = cls.counter("BRANCH") ?: Counter(0, 0),
                    ),
                )
            }
        }
        report.counter("LINE")?.let { totalLine = it }
        report.counter("BRANCH")?.let { totalBranch = it }

        return CoverageResponse(
            tool = "kover",
            line = metric(totalLine),
            branch = metric(totalBranch),
            classes = classes,
            modules = modules.entries
                .sortedByDescending { it.value.line.total }
                .map { (name, m) -> module(name, m) },
        )
    }

    private fun metric(c: Counter): CoverageMetric = CoverageMetric(fmt(c.pct()), c.covered, c.total)

    private fun module(name: String, m: Mod): CoverageModule = CoverageModule(
        name = name,
        kind = "module",
        linePct = m.line.pct().roundToInt(),
        branchPct = m.branch.pct().roundToInt(),
        missed = m.line.missed,
        // Per-class breakdown, worst-covered first (most missed lines), for the UI drilldown (031).
        classes = m.classes.sortedByDescending { it.line.missed }.map { c ->
            CoverageClass(
                name = c.name,
                linePct = c.line.pct().roundToInt(),
                branchPct = c.branch.pct().roundToInt(),
                missed = c.line.missed,
            )
        },
    )

    /** A class's display name: the path after `kontinuance/<module>/`, dot-joined (e.g. `model.Step`). */
    private fun classDisplayName(fullName: String, module: String): String {
        val marker = "kontinuance/$module/"
        val i = fullName.indexOf(marker)
        val tail = if (i >= 0) fullName.substring(i + marker.length) else fullName
        return tail.replace('/', '.')
    }

    /** The module segment after `kontinuance` in a JaCoCo package name (`org/khorum/oss/kontinuance/<mod>/…`). */
    private fun moduleOf(pkgName: String): String {
        val parts = pkgName.split('/')
        val i = parts.indexOf("kontinuance")
        return if (i >= 0 && i + 1 < parts.size) parts[i + 1] else parts.firstOrNull().orEmpty().ifEmpty { "root" }
    }

    private fun fmt(pct: Double): String = "%.1f%%".format(pct)

    private fun Element.counter(type: String): Counter? =
        children("counter").firstOrNull { it.getAttribute("type") == type }
            ?.let { Counter(it.getAttribute("missed").toInt(), it.getAttribute("covered").toInt()) }

    /** Direct child elements with the given tag name. */
    private fun Element.children(tag: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName == tag) out.add(n)
        }
        return out
    }
}
