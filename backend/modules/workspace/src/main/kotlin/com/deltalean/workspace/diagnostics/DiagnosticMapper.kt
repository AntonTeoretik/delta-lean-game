package com.deltalean.workspace.diagnostics

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile

/**
 * Maps diagnostics onto items using assembled item ranges.
 *
 * A diagnostic is assigned to the first item (in file order) whose range intersects with
 * the diagnostic range. Diagnostics with no match are returned as unmatched.
 */
class DiagnosticMapper {
  fun mapDiagnostics(
    file: WorldFile,
    itemRanges: Map<String, TextRange>,
    diagnostics: List<Diagnostic>
  ): DiagnosticMappingResult {
    val itemDiagnostics = linkedMapOf<String, MutableList<Diagnostic>>()
    file.items.forEach { item ->
      itemDiagnostics[item.id] = mutableListOf()
    }

    val unmatched = mutableListOf<Diagnostic>()

    diagnostics.forEach { diagnostic ->
      val targetItem = file.items.firstOrNull { item ->
        val range = itemRanges[item.id] ?: return@firstOrNull false
        intersects(diagnostic.range, range)
      }

      if (targetItem == null) {
        unmatched += diagnostic
      } else {
        itemDiagnostics.getValue(targetItem.id) += diagnostic
      }
    }

    val statuses = linkedMapOf<String, ItemStatus>()
    file.items.forEach { item ->
      val mappedDiagnostics = itemDiagnostics[item.id].orEmpty()
      statuses[item.id] = statusOf(mappedDiagnostics)
    }

    return DiagnosticMappingResult(
      itemDiagnostics = itemDiagnostics.mapValues { it.value.toList() },
      unmatchedDiagnostics = unmatched,
      itemStatuses = statuses,
    )
  }

  private fun intersects(a: TextRange, b: TextRange): Boolean {
    return lessOrEqual(a.start, b.end) && greaterOrEqual(a.end, b.start)
  }

  private fun lessOrEqual(left: TextPosition, right: TextPosition): Boolean {
    return left.line < right.line || (left.line == right.line && left.character <= right.character)
  }

  private fun greaterOrEqual(left: TextPosition, right: TextPosition): Boolean {
    return left.line > right.line || (left.line == right.line && left.character >= right.character)
  }

  private fun statusOf(diagnostics: List<Diagnostic>): ItemStatus {
    if (diagnostics.isEmpty()) {
      return ItemStatus.OK
    }

    val hasError = diagnostics.any { severityKind(it.severity) == SeverityKind.ERROR }
    if (hasError) {
      return ItemStatus.ERROR
    }

    val hasWarning = diagnostics.any { severityKind(it.severity) == SeverityKind.WARNING }
    if (hasWarning) {
      return ItemStatus.WARNING
    }

    return ItemStatus.ERROR
  }

  private fun severityKind(severity: String?): SeverityKind {
    return when (severity?.trim()?.lowercase()) {
      "warning" -> SeverityKind.WARNING
      "error" -> SeverityKind.ERROR
      else -> SeverityKind.ERROR
    }
  }

  private enum class SeverityKind {
    WARNING,
    ERROR,
  }
}
