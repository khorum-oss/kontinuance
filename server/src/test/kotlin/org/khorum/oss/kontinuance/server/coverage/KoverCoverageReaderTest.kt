package org.khorum.oss.kontinuance.server.coverage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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

        val json = KoverCoverageReader.read(file)
        requireNotNull(json)

        assertTrue(json.contains("\"tool\":\"kover\""))
        assertTrue(json.contains("\"pct\":\"85.0%\"") && json.contains("\"covered\":170") && json.contains("\"total\":200"))
        assertTrue(json.contains("\"pct\":\"65.0%\"") && json.contains("\"total\":40"))
        assertTrue(json.contains("\"classes\":3"))
        // engine: line 90/100 → 90, branch 16/20 → 80, missed 10
        assertTrue(json.contains("\"name\":\"engine\"") && json.contains("\"linePct\":90") && json.contains("\"branchPct\":80"))
        // server: line 80/100 → 80, branch 10/20 → 50, missed 20
        assertTrue(json.contains("\"name\":\"server\"") && json.contains("\"linePct\":80") && json.contains("\"branchPct\":50"))
        // per-class breakdown (031): class display name is the path after the module, dot-joined
        assertTrue(json.contains("\"name\":\"model.Foo\""), "engine class Foo is listed by display name")
        assertTrue(json.contains("\"name\":\"model.Bar\""), "engine class Bar is listed by display name")
        assertTrue(json.contains("\"name\":\"Baz\""), "server class Baz is listed")
        // Foo: line 9/10 → 90; Bar: line 81/90 → 90; both engine classes present with their own coverage
        assertTrue(json.contains("\"name\":\"model.Foo\",\"linePct\":90,\"branchPct\":100,\"missed\":1"))
    }

    @Test
    fun `classes within a module are ordered worst-covered first`(@TempDir dir: Path) {
        val file = dir.resolve("report.xml")
        Files.writeString(file, sample)

        val json = requireNotNull(KoverCoverageReader.read(file))
        // Bar (missed 9) must appear before Foo (missed 1) in the engine module's classes array.
        assertTrue(json.indexOf("model.Bar") < json.indexOf("model.Foo"), "most-missed class comes first")
    }

    @Test
    fun `returns null when the report is absent`(@TempDir dir: Path) {
        assertNull(KoverCoverageReader.read(dir.resolve("missing.xml")))
    }
}
