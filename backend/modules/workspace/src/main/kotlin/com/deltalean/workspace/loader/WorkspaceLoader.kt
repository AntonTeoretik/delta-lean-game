package com.deltalean.workspace.loader

import com.deltalean.domain.world.WorldSnapshot
import com.deltalean.workspace.WorkspaceService
import com.deltalean.workspace.importer.LeanFileSplitter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a workspace from filesystem into a world snapshot.
 */
class WorkspaceLoader(
  private val splitter: LeanFileSplitter = LeanFileSplitter()
) {
  fun loadWorkspace(root: Path): WorldSnapshot {
    val normalizedRoot = root.toAbsolutePath().normalize()
    require(Files.exists(normalizedRoot)) { "Workspace path does not exist" }
    require(Files.isDirectory(normalizedRoot)) { "Workspace path must be a directory" }

    val workspace = WorkspaceService(normalizedRoot)
    val files = workspace.listLeanFiles().map { relativePath ->
      val content = workspace.readFile(relativePath)
      splitter.split(path = relativePath, content = content)
    }

    return WorldSnapshot(files = files)
  }
}
