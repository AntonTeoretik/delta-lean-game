package com.deltalean.lean.session

import com.deltalean.lean.lsp.Diagnostic
import com.deltalean.lean.lsp.LeanLspClient
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

data class LeanFileDiagnostics(
  val path: String,
  val diagnostics: List<Diagnostic>
)

class LeanSession(
  private val client: LeanLspClient = LeanLspClient()
) {
  private var workspaceRoot: Path? = null
  private val openedUris = ConcurrentHashMap.newKeySet<String>()
  private val diagnosticsByPath = ConcurrentHashMap<String, LeanFileDiagnostics>()

  var onDiagnostics: ((String, List<Diagnostic>) -> Unit)? = null

  suspend fun start(workspaceRoot: Path) {
    val normalizedRoot = workspaceRoot.toAbsolutePath().normalize()
    this.workspaceRoot = normalizedRoot
    client.onDiagnostics = { params ->
      val relativePath = uriToRelativePath(params.uri)
      if (relativePath != null) {
        val fileDiagnostics = LeanFileDiagnostics(path = relativePath, diagnostics = params.diagnostics)
        diagnosticsByPath[relativePath] = fileDiagnostics
        onDiagnostics?.invoke(relativePath, fileDiagnostics.diagnostics)
      }
    }
    client.start(normalizedRoot)
  }

  fun openFile(path: Path, content: String) {
    val normalized = normalizePathInWorkspace(path)
    val uri = normalized.toUri().toString()
    openedUris.add(uri)
    client.openDocument(uri = uri, text = content)
  }

  fun updateFile(path: Path, content: String) {
    val normalized = normalizePathInWorkspace(path)
    val uri = normalized.toUri().toString()
    if (openedUris.add(uri)) {
      client.openDocument(uri = uri, text = content)
      return
    }
    client.changeDocument(uri = uri, newText = content)
  }

  fun getDiagnosticsForPath(relativePath: String): LeanFileDiagnostics {
    val normalized = normalizeRelativePath(relativePath)
    return diagnosticsByPath[normalized] ?: LeanFileDiagnostics(normalized, emptyList())
  }

  fun getAllDiagnostics(): List<LeanFileDiagnostics> =
    diagnosticsByPath.values.sortedBy { it.path }

  suspend fun stop() {
    runCatching { client.stop() }
    openedUris.clear()
    diagnosticsByPath.clear()
    workspaceRoot = null
  }

  private fun normalizePathInWorkspace(path: Path): Path {
    val root = requireWorkspaceRoot()
    val normalized = if (path.isAbsolute) {
      path.toAbsolutePath().normalize()
    } else {
      root.resolve(path).normalize()
    }
    require(normalized.startsWith(root)) { "Path is outside workspace root" }
    return normalized
  }

  private fun normalizeRelativePath(relativePath: String): String {
    val root = requireWorkspaceRoot()
    val candidate = Path.of(relativePath)
    require(!candidate.isAbsolute) { "Path must be relative" }
    val normalized = root.resolve(candidate).normalize()
    require(normalized.startsWith(root)) { "Path escapes workspace root" }
    return root.relativize(normalized).toString().replace('\\', '/')
  }

  private fun uriToRelativePath(uri: String): String? {
    val root = workspaceRoot ?: return null
    val absolute = runCatching { Paths.get(URI(uri)).toAbsolutePath().normalize() }.getOrNull() ?: return null
    if (!absolute.startsWith(root)) {
      return null
    }
    return root.relativize(absolute).toString().replace('\\', '/')
  }

  private fun requireWorkspaceRoot(): Path =
    checkNotNull(workspaceRoot) { "Lean session is not started" }
}
