package com.deltalean.workspace.assembler

import com.deltalean.domain.world.TextRange

/**
 * Result of deterministic world-file assembly.
 *
 * @property text fully assembled Lean file text using '\n' newlines
 * @property itemRanges exact per-item ranges in the assembled text, keyed by item id
 */
data class FileAssemblyResult(
  val text: String,
  val itemRanges: Map<String, TextRange>
)
