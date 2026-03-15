package com.deltalean.app

import com.deltalean.lean.lsp.Diagnostic
import com.deltalean.lean.session.LeanSession
import com.deltalean.transport.api.DiagnosticDto
import com.deltalean.transport.api.DiagnosticsResponse
import com.deltalean.transport.api.FileDiagnosticsDto
import com.deltalean.transport.api.OpenWorkspaceResponse
import com.deltalean.transport.api.PositionDto
import com.deltalean.transport.api.RangeDto
import com.deltalean.transport.api.WorldSnapshotResponse
import com.deltalean.transport.api.toResponse
import com.deltalean.workspace.assembler.FileAssembler
import com.deltalean.workspace.diagnostics.DiagnosticService
import com.deltalean.workspace.WorkspaceService
import com.deltalean.workspace.loader.WorkspaceLoader
import com.deltalean.workspace.session.WorkspaceSession
import com.deltalean.domain.world.Diagnostic as WorldDiagnostic
import com.deltalean.domain.world.TextPosition as WorldTextPosition
import com.deltalean.domain.world.TextRange as WorldTextRange
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkspaceSessionService(
  private val sessionFactory: () -> LeanSession = { LeanSession() },
  private val workspaceLoader: WorkspaceLoader = WorkspaceLoader(),
  private val fileAssembler: FileAssembler = FileAssembler(),
  private val diagnosticService: DiagnosticService = DiagnosticService(),
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val mutex = Mutex()
  private var active: ActiveWorkspace? = null

  suspend fun openWorkspace(rootPath: String): OpenWorkspaceResponse {
    val workspaceRoot = Path.of(rootPath).toAbsolutePath().normalize()
    require(Files.exists(workspaceRoot)) { "Workspace path does not exist" }
    require(Files.isDirectory(workspaceRoot)) { "Workspace path must be a directory" }

    val workspaceService = WorkspaceService(workspaceRoot)
    val worldSnapshot = workspaceLoader.loadWorkspace(workspaceRoot)
    val worldSession = WorkspaceSession(workspaceRoot, worldSnapshot)
    val leanFiles = worldSnapshot.files.map { it.path }
    val leanSession = sessionFactory()

    leanSession.onDiagnostics = { filePath, diagnostics ->
      scope.launch {
        applyDiagnosticsFromLean(filePath, diagnostics.map { it.toDomainDiagnostic() })
      }
    }

    mutex.withLock {
      active?.leanSession?.stop()

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

      active = ActiveWorkspace(workspaceService, leanSession, worldSession)
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
    current.leanSession.updateFile(current.workspace.resolveRelativePath(path), content)
  }

  suspend fun getDiagnostics(path: String?): DiagnosticsResponse = mutex.withLock {
    val current = requireActive()
    if (path.isNullOrBlank()) {
      val files = current.leanSession.getAllDiagnostics().map { file ->
        FileDiagnosticsDto(path = file.path, diagnostics = file.diagnostics.map { it.toApiDto() })
      }
      return@withLock DiagnosticsResponse(files)
    }

    val file = current.leanSession.getDiagnosticsForPath(path)
    DiagnosticsResponse(
      files = listOf(
        FileDiagnosticsDto(path = file.path, diagnostics = file.diagnostics.map { it.toApiDto() })
      )
    )
  }

  suspend fun getSession(): WorkspaceSession = mutex.withLock {
    requireActive().worldSession
  }

  suspend fun getWorld(): WorldSnapshotResponse = mutex.withLock {
    requireActive().worldSession.getWorld().toResponse()
  }

  private suspend fun applyDiagnosticsFromLean(filePath: String, diagnostics: List<WorldDiagnostic>) {
    mutex.withLock {
      val current = requireActive()
      diagnosticService.applyDiagnostics(current.worldSession, filePath, diagnostics)
    }
  }

  suspend fun updateItemCode(itemId: String, code: String) = mutex.withLock {
    val current = requireActive()
    val worldSession = current.worldSession
    val item = worldSession.getItem(itemId)
      ?: throw NoSuchElementException("Item is not found: $itemId")

    val snapshot = worldSession.getWorld()
    val sourceFile = snapshot.files.firstOrNull { it.path == item.filePath }
      ?: throw IllegalStateException("Source file not found for item: ${item.filePath}")

    val updatedFile = sourceFile.copy(
      items = sourceFile.items.map { existing ->
        if (existing.id == itemId) {
          existing.copy(code = code)
        } else {
          existing
        }
      }
    )

    val assembly = fileAssembler.assemble(updatedFile)
    val filePath = current.workspace.resolveRelativePath(updatedFile.path)
    Files.writeString(filePath, assembly.text, StandardCharsets.UTF_8)
    current.leanSession.updateFile(filePath, assembly.text)

    val updatedSnapshot = snapshot.copy(
      files = snapshot.files.map { file ->
        if (file.path == updatedFile.path) updatedFile else file
      }
    )
    worldSession.updateSnapshot(updatedSnapshot)
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

  private fun com.deltalean.lean.lsp.Diagnostic.toDomainDiagnostic(): WorldDiagnostic = WorldDiagnostic(
    severity = when (severity) {
      1 -> "error"
      2 -> "warning"
      3 -> "information"
      4 -> "hint"
      else -> null
    },
    message = message,
    range = WorldTextRange(
      start = WorldTextPosition(line = range.start.line, character = range.start.character),
      end = WorldTextPosition(line = range.end.line, character = range.end.character),
    )
  )

  private data class ActiveWorkspace(
    val workspace: WorkspaceService,
    val leanSession: LeanSession,
    val worldSession: WorkspaceSession,
  )
}
