package org.khorum.oss.kontinuance.server.controller.coverage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoverCoverageReaderTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="kontinuance">
          <package name="org/khorum/oss/kontinuance/engine/model">
            <class name="org/khorum/oss/kontinuance/engine/model/Foo">
              <counter type="LINE" missed="1" covered="9"/>
              <counter type="BRANCH" missed="0" covered="4"/>
            </class>
            <class name="org/khorum/oss/kontinuance/engine/model/Bar">
              <counter type="LINE" missed="9" covered="81"/>
              <counter type="BRANCH" missed="4" covered="12"/>
            </class>
            <counter type="LINE" missed="10" covered="90"/>
            <counter type="BRANCH" missed="4" covered="16"/>
          </package>
          <package name="org/khorum/oss/kontinuance/server">
            <class name="org/khorum/oss/kontinuance/server/Baz">
              <counter type="LINE" missed="20" covered="80"/>
              <counter type="BRANCH" missed="10" covered="10"/>
            </class>
            <counter type="LINE" missed="20" covered="80"/>
            <counter type="BRANCH" missed="10" covered="10"/>
          </package>
          <counter type="LINE" missed="30" covered="170"/>
          <counter type="BRANCH" missed="14" covered="26"/>
        </report>
    """.trimIndent()

    @Test
    fun `parses report totals, class count, and per-module coverage`(@TempDir dir: Path) {
        val file = dir.resolve("report.xml")
        Files.writeString(file, sample)

        val coverage = requireNotNull(KoverCoverageReader.read(file))

        assertEquals("kover", coverage.tool)
        assertEquals("85.0%", coverage.line.pct)
        assertEquals(170, coverage.line.covered)
        assertEquals(200, coverage.line.total)
        assertEquals("65.0%", coverage.branch.pct)
        assertEquals(40, coverage.branch.total)
        assertEquals(3, coverage.classes)

        val engine = coverage.modules.first { it.name == "engine" }
        assertEquals(90, engine.linePct)
        assertEquals(80, engine.branchPct)
        assertEquals(10, engine.missed)

        val server = coverage.modules.first { it.name == "server" }
        assertEquals(80, server.linePct)
        assertEquals(50, server.branchPct)
        assertEquals(20, server.missed)

        // per-class breakdown (031): class display name is the path after the module, dot-joined
        val engineClasses = requireNotNull(engine.classes)
        assertTrue(engineClasses.any { it.name == "model.Foo" }, "engine class Foo listed by display name")
        assertTrue(engineClasses.any { it.name == "model.Bar" }, "engine class Bar listed by display name")
        assertTrue(requireNotNull(server.classes).any { it.name == "Baz" }, "server class Baz is listed")

        val foo = engineClasses.first { it.name == "model.Foo" }
        assertEquals(90, foo.linePct)
        assertEquals(100, foo.branchPct)
        assertEquals(1, foo.missed)
    }

    @Test
    fun `classes within a module are ordered worst-covered first`(@TempDir dir: Path) {
        val file = dir.resolve("report.xml")
        Files.writeString(file, sample)

        val coverage = requireNotNull(KoverCoverageReader.read(file))
        val engineClasses = requireNotNull(coverage.modules.first { it.name == "engine" }.classes).map { it.name }
        // Bar (missed 9) must appear before Foo (missed 1) in the engine module's classes array.
        assertTrue(engineClasses.indexOf("model.Bar") < engineClasses.indexOf("model.Foo"), "most-missed first")
    }

    @Test
    fun `returns null when the report is absent`(@TempDir dir: Path) {
        assertNull(KoverCoverageReader.read(dir.resolve("missing.xml")))
    }
}