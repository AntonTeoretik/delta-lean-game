package com.deltalean.domain.world

/**
 * Full snapshot of the visual world state.
 */
data class WorldSnapshot(
  val files: List<WorldFile>
)

/**
 * Lean file representation inside the world model.
 */
data class WorldFile(
  val path: String,
  val imports: List<String>,
  val items: List<WorldItem>
)

/**
 * Visual node that corresponds to a top-level Lean declaration.
 */
data class WorldItem(
  val id: String,
  val filePath: String,
  val kind: ItemKind,
  val name: String?,
  val title: String,
  val code: String,
  val range: TextRange?,
  val status: ItemStatus,
  val diagnostics: List<Diagnostic>,
  val layout: ItemLayout?
)

/**
 * Declaration kind for a world item.
 */
enum class ItemKind {
  DEF,
  THEOREM,
  INDUCTIVE,
  STRUCTURE,
  CLASS,
  INSTANCE,
  AXIOM,
  ABBREV,
  OPAQUE,
  EXAMPLE,
  RAW
}

/**
 * Aggregated diagnostic status for a world item.
 */
enum class ItemStatus {
  UNKNOWN,
  OK,
  WARNING,
  ERROR
}

/**
 * Text position using zero-based line and character offsets.
 */
data class TextPosition(
  val line: Int,
  val character: Int
)

/**
 * Text range between start and end positions.
 */
data class TextRange(
  val start: TextPosition,
  val end: TextPosition
)

/**
 * Domain-level diagnostic mapped from Lean diagnostics.
 */
data class Diagnostic(
  val severity: String?,
  val message: String,
  val range: TextRange
)

/**
 * Optional node position metadata in the visual scene.
 */
data class ItemLayout(
  val x: Double,
  val y: Double
)
