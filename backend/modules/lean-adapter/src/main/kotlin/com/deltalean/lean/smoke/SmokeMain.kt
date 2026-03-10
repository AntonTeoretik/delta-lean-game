package com.deltalean.lean.smoke

import com.deltalean.lean.lsp.LeanLspClient
import com.deltalean.lean.lsp.PublishDiagnosticsParams
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

fun main() {
  runBlocking {
    val workspaceRoot = Paths.get("..", "sample-workspaces", "tiny").toAbsolutePath().normalize()
    val mainFile = workspaceRoot.resolve("Main.lean")

    check(Files.exists(mainFile)) { "Missing file: $mainFile" }

    val originalText = Files.readString(mainFile)
    val badText = """
theorem bad : Prop := by
  exact
""".trimIndent()

    val uri = mainFile.toUri().toString()
    val diagnosticsQueue = LinkedBlockingQueue<PublishDiagnosticsParams>()
    val client = LeanLspClient().apply {
      onDiagnostics = { diagnostics ->
        diagnosticsQueue.offer(diagnostics)
        printDiagnostics(diagnostics)
      }
    }

    try {
      client.start(workspaceRoot)

      diagnosticsQueue.clear()
      client.openDocument(uri, originalText)
      val initial = awaitLatestDiagnostics(diagnosticsQueue, uri)
      println("Initial diagnostics: ${initial.diagnostics.size}")

      diagnosticsQueue.clear()
      client.changeDocument(uri, badText)
      val afterBadChange = awaitLatestDiagnostics(diagnosticsQueue, uri)
      println("After bad change diagnostics: ${afterBadChange.diagnostics.size}")

//      diagnosticsQueue.clear()
//      client.changeDocument(uri, originalText)
//      val afterRestore = awaitLatestDiagnostics(diagnosticsQueue, uri)
//      println("After restore diagnostics: ${afterRestore.diagnostics.size}")
    } finally {
      client.stop()
    }
  }
}

private fun awaitLatestDiagnostics(
  queue: LinkedBlockingQueue<PublishDiagnosticsParams>,
  uri: String,
  timeoutSeconds: Long = 20,
  settleMillis: Long = 200
): PublishDiagnosticsParams {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
  var latestForUri: PublishDiagnosticsParams? = null

  while (System.nanoTime() < deadline) {
    val remainingNanos = deadline - System.nanoTime()
    val next = queue.poll(remainingNanos, TimeUnit.NANOSECONDS) ?: continue

    if (next.uri != uri) {
      continue
    }

    latestForUri = next
    while (true) {
      val followUp = queue.poll(settleMillis, TimeUnit.MILLISECONDS) ?: return latestForUri!!
      if (followUp.uri == uri) {
        latestForUri = followUp
      }
    }
  }

  latestForUri?.let { return it }

  error("Timed out waiting for diagnostics for $uri")
}

private fun printDiagnostics(params: PublishDiagnosticsParams) {
  println("Diagnostics for ${params.uri} (version=${params.version ?: "n/a"}): ${params.diagnostics.size}")
  params.diagnostics.forEachIndexed { index, diagnostic ->
    val start = diagnostic.range.start
    println("  [$index] (${start.line}:${start.character}) ${diagnostic.message}")
  }
}
