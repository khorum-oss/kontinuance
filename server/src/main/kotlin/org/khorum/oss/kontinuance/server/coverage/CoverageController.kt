package org.khorum.oss.kontinuance.server.coverage

import org.khorum.oss.kontinuance.server.stub.StubFixtures
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Path

/**
 * Serves `/api/coverage` from the real **Kover** report when it is present (parsed by
 * [KoverCoverageReader] into the contract shape), falling back to fixture data otherwise. The report
 * path comes from `kontinuance.coverage.report` (default `build/reports/kover/report.xml`, relative to
 * the server's working directory) — override it to point at an aggregated report elsewhere. Additive
 * and dependency-free: the JDK XML parser does the work, so the supply-chain gate is unaffected.
 */
@RestController
class CoverageController(
    @param:Value("\${kontinuance.coverage.report:build/reports/kover/report.xml}") reportPath: String,
) {
    private val report: Path = Path.of(reportPath)

    @GetMapping("/api/coverage")
    fun coverage(): ResponseEntity<ByteArray> {
        val body = KoverCoverageReader.read(report) ?: StubFixtures.coverage()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body.toByteArray())
    }
}
