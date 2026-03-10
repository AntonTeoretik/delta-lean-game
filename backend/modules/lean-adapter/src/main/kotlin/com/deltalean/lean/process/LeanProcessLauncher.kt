package com.deltalean.lean.process

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LeanProcessLauncher {
  private var process: Process? = null
  private var stderrThread: Thread? = null

  val stdin: OutputStream
    get() = requireProcess().outputStream

  val stdout: InputStream
    get() = requireProcess().inputStream

  val stderr: InputStream
    get() = requireProcess().errorStream

  fun start(workspaceRoot: Path) {
    if (process != null) {
      return
    }

    val launched = ProcessBuilder("lake", "env", "lean", "--server")
      .directory(workspaceRoot.toFile())
      .redirectErrorStream(false)
      .start()

    process = launched
    stderrThread = Thread {
      launched.errorStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
          System.err.println("[lean-stderr] $line")
        }
      }
    }.apply {
      isDaemon = true
      name = "lean-stderr-logger"
      start()
    }
  }

  fun stop() {
    val running = process ?: return

    running.destroy()
    if (!running.waitFor(3, TimeUnit.SECONDS)) {
      running.destroyForcibly()
      running.waitFor(3, TimeUnit.SECONDS)
    }

    stderrThread?.interrupt()
    stderrThread = null
    process = null
  }

  private fun requireProcess(): Process =
    checkNotNull(process) { "Lean process is not started." }
}
