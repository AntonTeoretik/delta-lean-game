package com.deltalean.app

import com.deltalean.lean.lsp.Diagnostic
import com.deltalean.lean.session.LeanSession
import com.deltalean.transport.api.DiagnosticDto
import com.deltalean.transport.api.DiagnosticsResponse
import com.deltalean.transport.api.FileDiagnosticsDto
import com.deltalean.transport.api.OpenWorkspaceResponse
import com.deltalean.transport.api.PositionDto
import com.deltalean.transport.api.RangeDto
import com.deltalean.workspace.WorkspaceService
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkspaceSessionService {
  private val mutex = Mutex()
  private var active: ActiveWorkspace? = null

  suspend fun openWorkspace(rootPath: String): OpenWorkspaceResponse {
    val workspaceRoot = Path.of(rootPath).toAbsolutePath().normalize()
    require(Files.exists(workspaceRoot)) { "Workspace path does not exist" }
    require(Files.isDirectory(workspaceRoot)) { "Workspace path must be a directory" }

    val workspaceService = WorkspaceService(workspaceRoot)
    val leanFiles = workspaceService.listLeanFiles()
    val leanSession = LeanSession()

    mutex.withLock {
      active?.session?.stop()

      try {
        leanSession.start(workspaceRoot)
        leanFiles.forEach { relativePath ->
          val content = workspaceService.readFile(relativePath)
          leanSession.openFile(workspaceService.resolveRelativePath(relativePath), content)
        }
      } catch (e: Exception) {
        runCatching { leanSession.stop() }
        throw IllegalStateException("Failed to start Lean session", e)
      }

      active = ActiveWorkspace(workspaceService, leanSession)
    }

    return OpenWorkspaceResponse(success = true, fileCount = leanFiles.size)
  }

  suspend fun listFiles(): List<String> = mutex.withLock {
    requireActive().workspace.listLeanFiles()
  }

  suspend fun readFile(path: String): String = mutex.withLock {
    requireActive().workspace.readFile(path)
  }

  suspend fun updateFile(path: String, content: String) = mutex.withLock {
    val current = requireActive()
    current.workspace.writeFile(path, content)
    current.session.updateFile(current.workspace.resolveRelativePath(path), content)
  }

  suspend fun getDiagnostics(path: String?): DiagnosticsResponse = mutex.withLock {
    val current = requireActive()
    if (path.isNullOrBlank()) {
      val files = current.session.getAllDiagnostics().map { file ->
        FileDiagnosticsDto(path = file.path, diagnostics = file.diagnostics.map { it.toApiDto() })
      }
      return@withLock DiagnosticsResponse(files)
    }

    val file = current.session.getDiagnosticsForPath(path)
    DiagnosticsResponse(
      files = listOf(
        FileDiagnosticsDto(path = file.path, diagnostics = file.diagnostics.map { it.toApiDto() })
      )
    )
  }

  private fun requireActive(): ActiveWorkspace =
    checkNotNull(active) { "Workspace is not opened" }

  private fun Diagnostic.toApiDto(): DiagnosticDto = DiagnosticDto(
    severity = severity,
    message = message,
    range = RangeDto(
      start = PositionDto(line = range.start.line, character = range.start.character),
      end = PositionDto(line = range.end.line, character = range.end.character)
    )
  )

  private data class ActiveWorkspace(
    val workspace: WorkspaceService,
    val session: LeanSession
  )
}
