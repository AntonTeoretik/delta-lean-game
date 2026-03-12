package com.deltalean.workspace.diagnostics

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemStatus

/**
 * Result of mapping file-level diagnostics onto world items.
 */
data class DiagnosticMappingResult(
  val itemDiagnostics: Map<String, List<Diagnostic>>,
  val unmatchedDiagnostics: List<Diagnostic>,
  val itemStatuses: Map<String, ItemStatus>
)
