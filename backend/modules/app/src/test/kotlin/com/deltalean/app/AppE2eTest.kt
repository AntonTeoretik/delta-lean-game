package com.deltalean.app

import com.deltalean.lean.lsp.Diagnostic
import com.deltalean.lean.lsp.Position
import com.deltalean.lean.lsp.Range
import com.deltalean.lean.session.LeanFileDiagnostics
import com.deltalean.lean.session.LeanSession
import com.deltalean.transport.api.DiagnosticsResponse
import com.deltalean.transport.api.ErrorResponse
import com.deltalean.transport.api.FileContentResponse
import com.deltalean.transport.api.FilesListResponse
import com.deltalean.transport.api.OpenWorkspaceResponse
import com.deltalean.transport.api.WorldSnapshotResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class AppE2eTest {
  private val json = Json { ignoreUnknownKeys = true }
  private val tempRoots = mutableListOf<Path>()

  @AfterTest
  fun cleanup() {
    tempRoots.forEach(::deleteRecursively)
    tempRoots.clear()
  }

  @Test
  fun `workspace flow works through HTTP routes`() = testApplication {
    val root = createWorkspace()
    val mainPath = root.resolve("Main.lean")
    val badContent = "theorem bad : Prop := by\n  exact\n"

    val diagnostics = listOf(
      Diagnostic(
        range = Range(start = Position(0, 0), end = Position(0, 5)),
        severity = 1,
        message = "type mismatch"
      )
    )
    val leanSession = mockk<LeanSession>()
    coEvery { leanSession.start(any()) } returns Unit
    every { leanSession.openFile(any(), any()) } returns Unit
    every { leanSession.updateFile(any(), any()) } returns Unit
    every { leanSession.getAllDiagnostics() } returns listOf(
      LeanFileDiagnostics(path = "Main.lean", diagnostics = diagnostics)
    )
    every { leanSession.getDiagnosticsForPath(any()) } answers {
      LeanFileDiagnostics(firstArg(), diagnostics)
    }
    coEvery { leanSession.stop() } returns Unit

    application {
      deltaLeanApplication(WorkspaceSessionService(sessionFactory = { leanSession }))
    }

    val openResponse = client.post("/api/workspace/open") {
      contentType(ContentType.Application.Json)
      setBody("{\"rootPath\":\"${root.toString().replace("\\", "\\\\")}\"}")
    }
    assertEquals(HttpStatusCode.OK, openResponse.status)
    val openDto = json.decodeFromString<OpenWorkspaceResponse>(openResponse.bodyAsText())
    assertEquals(true, openDto.success)
    assertEquals(2, openDto.fileCount)

    val filesResponse = client.get("/api/files")
    assertEquals(HttpStatusCode.OK, filesResponse.status)
    val filesDto = json.decodeFromString<FilesListResponse>(filesResponse.bodyAsText())
    assertEquals(listOf("Main.lean", "Nested/Other.lean"), filesDto.files)

    val readResponse = client.get("/api/files/Main.lean")
    assertEquals(HttpStatusCode.OK, readResponse.status)
    val fileDto = json.decodeFromString<FileContentResponse>(readResponse.bodyAsText())
    assertEquals("Main.lean", fileDto.path)
    assertEquals("theorem ok : True := by\n  trivial\n", fileDto.content)

    val putResponse = client.put("/api/files/Main.lean") {
      contentType(ContentType.Application.Json)
      setBody("{\"content\":\"theorem bad : Prop := by\\n  exact\\n\"}")
    }
    assertEquals(HttpStatusCode.OK, putResponse.status)
    assertEquals(badContent, Files.readString(mainPath))

    val diagnosticsByPath = client.get("/api/diagnostics?path=Main.lean")
    assertEquals(HttpStatusCode.OK, diagnosticsByPath.status)
    val diagnosticsByPathDto = json.decodeFromString<DiagnosticsResponse>(diagnosticsByPath.bodyAsText())
    assertEquals(1, diagnosticsByPathDto.files.size)
    assertEquals("Main.lean", diagnosticsByPathDto.files.first().path)
    assertEquals(1, diagnosticsByPathDto.files.first().diagnostics.size)

    val diagnosticsAll = client.get("/api/diagnostics")
    assertEquals(HttpStatusCode.OK, diagnosticsAll.status)
    val diagnosticsAllDto = json.decodeFromString<DiagnosticsResponse>(diagnosticsAll.bodyAsText())
    assertEquals(1, diagnosticsAllDto.files.size)

    Files.writeString(mainPath, "def replacedOnDisk : Nat := 2\n")

    val worldResponse = client.get("/api/world")
    assertEquals(HttpStatusCode.OK, worldResponse.status)
    val worldDto = json.decodeFromString<WorldSnapshotResponse>(worldResponse.bodyAsText())
    assertEquals(2, worldDto.files.size)
    assertEquals(listOf("Main.lean", "Nested/Other.lean"), worldDto.files.map { it.path })
    assertTrue(worldDto.files.all { it.items.isNotEmpty() })
    val mainWorldFile = worldDto.files.first { it.path == "Main.lean" }
    assertTrue(mainWorldFile.items.any { it.code.contains("theorem ok") })

    val targetItemId = mainWorldFile.items.first().id
    val encodedItemId = URLEncoder.encode(targetItemId, StandardCharsets.UTF_8).replace("+", "%20")
    val updateItemResponse = client.put("/api/items/$encodedItemId/code") {
      contentType(ContentType.Application.Json)
      setBody("{\"code\":\"theorem changed : True := by\\n  trivial\"}")
    }
    assertEquals(HttpStatusCode.NoContent, updateItemResponse.status)

    val updatedWorld = client.get("/api/world")
    val updatedWorldDto = json.decodeFromString<WorldSnapshotResponse>(updatedWorld.bodyAsText())
    val updatedMain = updatedWorldDto.files.first { it.path == "Main.lean" }
    assertTrue(updatedMain.items.any { it.code.contains("theorem changed") })
    assertTrue(Files.readString(mainPath).contains("theorem changed"))

    coVerify(exactly = 1) { leanSession.start(any()) }
    verify(exactly = 2) { leanSession.openFile(any(), any()) }
    verify(exactly = 1) { leanSession.updateFile(any(), badContent) }
  }

  @Test
  fun `routes return bad request when workspace is not opened`() = testApplication {
    application {
      deltaLeanApplication(WorkspaceSessionService(sessionFactory = { mockk(relaxed = true) }))
    }

    val filesResponse = client.get("/api/files")
    assertEquals(HttpStatusCode.BadRequest, filesResponse.status)
    val filesError = json.decodeFromString<ErrorResponse>(filesResponse.bodyAsText())
    assertEquals("Workspace is not opened", filesError.error)

    val diagnosticsResponse = client.get("/api/diagnostics")
    assertEquals(HttpStatusCode.BadRequest, diagnosticsResponse.status)
    val diagnosticsError = json.decodeFromString<ErrorResponse>(diagnosticsResponse.bodyAsText())
    assertEquals("Workspace is not opened", diagnosticsError.error)

    val worldResponse = client.get("/api/world")
    assertEquals(HttpStatusCode.BadRequest, worldResponse.status)
    val worldError = json.decodeFromString<ErrorResponse>(worldResponse.bodyAsText())
    assertEquals("Workspace is not opened", worldError.error)
  }

  @Test
  fun `updating unknown item returns not found`() = testApplication {
    val root = createWorkspace()
    val leanSession = mockk<LeanSession>()
    coEvery { leanSession.start(any()) } returns Unit
    every { leanSession.openFile(any(), any()) } returns Unit
    every { leanSession.updateFile(any(), any()) } returns Unit
    every { leanSession.getAllDiagnostics() } returns emptyList()
    every { leanSession.getDiagnosticsForPath(any()) } answers {
      throw IllegalStateException("not used")
    }
    coEvery { leanSession.stop() } returns Unit

    application {
      deltaLeanApplication(WorkspaceSessionService(sessionFactory = { leanSession }))
    }

    val openResponse = client.post("/api/workspace/open") {
      contentType(ContentType.Application.Json)
      setBody("{\"rootPath\":\"${root.toString().replace("\\", "\\\\")}\"}")
    }
    assertEquals(HttpStatusCode.OK, openResponse.status)

    val response = client.put("/api/items/missing-id/code") {
      contentType(ContentType.Application.Json)
      setBody("{\"code\":\"def x := 1\"}")
    }

    assertEquals(HttpStatusCode.NotFound, response.status)
    val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())
    assertTrue(error.error.contains("Item is not found"))
  }

  private fun createWorkspace(): Path {
    val root = createTempDirectory("deltalean-app-test-")
    tempRoots.add(root)

    root.resolve("Main.lean").writeText("theorem ok : True := by\n  trivial\n")
    root.resolve("Nested").createDirectories()
    root.resolve("Nested/Other.lean").writeText("theorem other : True := by\n  trivial\n")
    root.resolve("README.md").writeText("ignore me")

    return root
  }

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) {
      return
    }
    Files.walk(root).use { stream ->
      stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}
