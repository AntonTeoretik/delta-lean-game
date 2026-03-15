package com.deltalean.workspace.diagnostics

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.WorldSnapshot
import com.deltalean.workspace.assembler.FileAssembler
import com.deltalean.workspace.session.WorkspaceSession

/**
 * Applies file diagnostics to session world snapshot using item range mapping.
 */
class DiagnosticService(
  private val diagnosticMapper: DiagnosticMapper = DiagnosticMapper(),
  private val fileAssembler: FileAssembler = FileAssembler(),
) {
  fun applyDiagnostics(
    session: WorkspaceSession,
    filePath: String,
    diagnostics: List<Diagnostic>
  ) {
    val snapshot = session.getWorld()
    val targetFile = snapshot.files.firstOrNull { it.path == filePath } ?: return

    val assembly = fileAssembler.assemble(targetFile)
    val mapping = diagnosticMapper.mapDiagnostics(
      file = targetFile,
      itemRanges = assembly.itemRanges,
      diagnostics = diagnostics,
    )

    val updatedFile = targetFile.copy(
      items = targetFile.items.map { item ->
        item.copy(
          diagnostics = mapping.itemDiagnostics[item.id].orEmpty(),
          status = mapping.itemStatuses[item.id] ?: item.status,
        )
      }
    )

    val updatedSnapshot = snapshot.replaceFile(updatedFile)
    session.updateSnapshot(updatedSnapshot)
    session.updateUnmatchedDiagnostics(filePath, mapping.unmatchedDiagnostics)
  }

  private fun WorldSnapshot.replaceFile(updatedFile: com.deltalean.domain.world.WorldFile): WorldSnapshot = copy(
    files = files.map { existing ->
      if (existing.path == updatedFile.path) updatedFile else existing
    }
  )
}
