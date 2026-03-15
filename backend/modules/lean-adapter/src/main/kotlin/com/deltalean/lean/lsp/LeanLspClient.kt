package com.deltalean.lean.lsp

import com.deltalean.lean.process.LeanProcessLauncher
import com.deltalean.lean.rpc.JsonRpcTransport
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

class LeanLspClient(
  private val launcher: LeanProcessLauncher = LeanProcessLauncher(),
  private val json: Json = Json { ignoreUnknownKeys = true }
) {
  private var transport: JsonRpcTransport? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var notificationsJob: Job? = null
  private val versions = ConcurrentHashMap<String, Int>()

  var onDiagnostics: ((PublishDiagnosticsParams) -> Unit)? = null

  suspend fun start(workspaceRoot: Path) {
    launcher.start(workspaceRoot)
    val rpc = JsonRpcTransport(
      input = launcher.stdout,
      output = launcher.stdin,
      json = json
    )
    transport = rpc

    notificationsJob = scope.launch {
      rpc.notifications.collect { notification ->
        if (notification.method != "textDocument/publishDiagnostics") {
          return@collect
        }

        val params = notification.params ?: return@collect
        val diagnostics = parsePublishDiagnostics(params)
        onDiagnostics?.invoke(diagnostics)
      }
    }

    val initializeParams = InitializeParams(
      processId = ProcessHandle.current().pid().toInt(),
      rootUri = workspaceRoot.toUri().toString()
    )

    rpc.sendRequest(
      method = "initialize",
      params = initializeParams.toJson()
    )

    rpc.sendNotification("initialized", buildJsonObject {})
  }

  fun openDocument(uri: String, text: String) {
    val rpc = requireTransport()
    val version = 1
    versions[uri] = version

    val params = DidOpenTextDocumentParams(
      textDocument = TextDocumentItem(
        uri = uri,
        languageId = "lean",
        version = version,
        text = text
      )
    )

    rpc.sendNotification("textDocument/didOpen", params.toJson())
  }

  fun changeDocument(uri: String, newText: String) {
    val rpc = requireTransport()
    val version = versions.compute(uri) { _, current -> (current ?: 0) + 1 } ?: 1

    val params = DidChangeTextDocumentParams(
      textDocument = VersionedTextDocumentIdentifier(uri = uri, version = version),
      contentChanges = listOf(TextDocumentContentChangeEvent(text = newText))
    )

    rpc.sendNotification("textDocument/didChange", params.toJson())
  }

  suspend fun stop() {
    notificationsJob?.cancelAndJoin()
    notificationsJob = null

    val rpc = transport
    if (rpc != null) {
      runCatching { rpc.sendRequest("shutdown") }
      runCatching {
        rpc.sendNotification("exit")
      }
      rpc.close()
      transport = null
    }

    launcher.stop()
  }

  private fun requireTransport(): JsonRpcTransport =
    checkNotNull(transport) { "LSP client is not started." }
}
