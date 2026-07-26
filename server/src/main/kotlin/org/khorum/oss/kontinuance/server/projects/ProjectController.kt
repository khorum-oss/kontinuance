package org.khorum.oss.kontinuance.server.projects

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.khorum.oss.kontinuance.engine.descriptor.PipelineDescriptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the named pipeline descriptors ("projects", 032) and which one is active:
 *
 * - `GET /api/projects` — list the projects and the active one (seeding a `default` project from the
 *   current descriptor on first use, so there is always one to select).
 * - `POST /api/projects` — register `{name, text}`; the name must be a safe slug and the text must parse
 *   with the engine's strict parser, or it is rejected `400` (and `409` if the name already exists).
 * - `POST /api/projects/{name}/activate` — make a project active: write its descriptor to the server's live
 *   descriptor file (so the trigger and Config screen use it) and record it as active; `404` if unknown.
 *
 * Bodies are pre-serialized JSON `ResponseEntity<ByteArray>`, matching the other controllers.
 */
@RestController
class ProjectController(
    private val store: ProjectStore,
    @Value("\${kontinuance.config.descriptor:kontinuance.yml}") descriptorPath: String,
) {
    private val descriptor: Path = Path.of(descriptorPath)

    @GetMapping("/api/projects")
    suspend fun list(): ResponseEntity<ByteArray> {
        withContext(Dispatchers.IO) { seedIfEmpty() }
        val active = store.activeName()
        val json = buildJsonObject {
            put("active", active)
            putJsonArray("projects") {
                store.list().forEach { name ->
                    addJsonObject {
                        put("name", name)
                        put("active", name == active)
                    }
                }
            }
        }.toString()
        return ok(json)
    }

    @PostMapping("/api/projects")
    suspend fun create(@RequestBody(required = false) requestBody: String?): ResponseEntity<ByteArray> {
        val request = parse(requestBody)
            ?: return error(HttpStatus.BAD_REQUEST, "malformed request body — expected {\"name\": …, \"text\": …}")
        if (!ProjectStore.isValidName(request.name)) {
            return error(HttpStatus.BAD_REQUEST, "invalid project name (use letters, digits, and . _ -)")
        }
        if (store.exists(request.name)) {
            return error(HttpStatus.CONFLICT, "project already exists: ${request.name}")
        }
        runCatching { PipelineDescriptor.parse(request.text) }
            .getOrElse { return error(HttpStatus.BAD_REQUEST, it.message ?: "invalid descriptor") }
        withContext(Dispatchers.IO) { store.save(request.name, request.text) }
        return ok(buildJsonObject { put("name", request.name) }.toString())
    }

    @PostMapping("/api/projects/{name}/activate")
    suspend fun activate(@PathVariable name: String): ResponseEntity<ByteArray> {
        val text = withContext(Dispatchers.IO) { store.get(name) }
            ?: return error(HttpStatus.NOT_FOUND, "no such project: $name")
        withContext(Dispatchers.IO) {
            descriptor.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            Files.writeString(descriptor, text)
            store.setActive(name)
        }
        return ok(buildJsonObject { put("active", name) }.toString())
    }

    /** On first use, register the current descriptor file as `default` and activate it. */
    private fun seedIfEmpty() {
        if (store.list().isNotEmpty()) return
        if (!Files.isRegularFile(descriptor)) return
        val text = Files.readString(descriptor)
        runCatching { PipelineDescriptor.parse(text) }.getOrNull() ?: return
        store.save(DEFAULT, text)
        store.setActive(DEFAULT)
    }

    private fun parse(requestBody: String?): CreateRequest? {
        if (requestBody.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(requestBody).jsonObject
            val name = obj["name"]?.jsonPrimitive?.content
            val text = obj["text"]?.jsonPrimitive?.content
            if (name != null && text != null) CreateRequest(name, text) else null
        }.getOrNull()
    }

    private fun ok(json: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.toByteArray())

    private fun error(status: HttpStatus, message: String): ResponseEntity<ByteArray> {
        val body = buildJsonObject { put("error", message) }.toString().toByteArray()
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)
    }

    private data class CreateRequest(val name: String, val text: String)

    private companion object {
        const val DEFAULT = "default"
    }
}
