package com.deltalean.workspace.session

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.WorldSnapshot
import java.nio.file.Path

/**
 * In-memory workspace session with an immutable world snapshot.
 */
class WorkspaceSession(
  val root: Path,
  snapshot: WorldSnapshot
) {
  private var snapshotState: WorldSnapshot = snapshot
  private var itemsById: Map<String, WorldItem> = buildItemIndex(snapshot)
  private var unmatchedDiagnosticsByFile: Map<String, List<Diagnostic>> = emptyMap()

  fun getWorld(): WorldSnapshot = snapshotState

  fun getItem(id: String): WorldItem? = itemsById[id]

  fun updateSnapshot(newSnapshot: WorldSnapshot) {
    snapshotState = newSnapshot
    itemsById = buildItemIndex(newSnapshot)
  }

  fun updateUnmatchedDiagnostics(filePath: String, diagnostics: List<Diagnostic>) {
    unmatchedDiagnosticsByFile = unmatchedDiagnosticsByFile.toMutableMap().apply {
      put(filePath, diagnostics)
    }
  }

  fun getUnmatchedDiagnostics(filePath: String): List<Diagnostic> =
    unmatchedDiagnosticsByFile[filePath].orEmpty()

  private fun buildItemIndex(snapshot: WorldSnapshot): Map<String, WorldItem> = buildMap {
    snapshot.files.forEach { file ->
      file.items.forEach { item ->
        put(item.id, item)
      }
    }
  }
}
