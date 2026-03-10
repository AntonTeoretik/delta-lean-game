package com.deltalean.workspace

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.extension

class WorkspaceService(
  root: Path
) {
  val root: Path = root.toAbsolutePath().normalize()

  fun listLeanFiles(): List<String> {
    return Files.walk(root).use { stream ->
      stream
        .filter { Files.isRegularFile(it) && it.extension == "lean" }
        .map { toRelativePath(it) }
        .sorted()
        .toList()
    }
  }

  fun readFile(relativePath: String): String {
    val resolved = resolveRelativePath(relativePath)
    if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
      throw NoSuchFileException(relativePath)
    }
    return Files.readString(resolved)
  }

  fun writeFile(relativePath: String, content: String) {
    val resolved = resolveRelativePath(relativePath)
    if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
      throw NoSuchFileException(relativePath)
    }
    Files.writeString(resolved, content)
  }

  fun resolveRelativePath(relativePath: String): Path {
    val candidate = Path.of(relativePath)
    require(!candidate.isAbsolute) { "Path must be workspace-relative" }

    val normalized = root.resolve(candidate).normalize()
    require(normalized.startsWith(root)) { "Path escapes workspace root" }
    return normalized
  }

  fun toRelativePath(path: Path): String {
    val normalized = path.toAbsolutePath().normalize()
    require(normalized.startsWith(root)) { "Path is outside workspace root" }
    return root.relativize(normalized).toString().replace('\\', '/')
  }
}
