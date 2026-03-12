package com.deltalean.workspace.diagnostics

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticMapperTest {
  private val mapper = DiagnosticMapper()

  @Test
  fun `maps diagnostic inside a single item`() {
    val file = worldFile(items = listOf(item("i0")))
    val ranges = mapOf("i0" to range(2, 0, 4, 10))
    val diagnostics = listOf(diagnostic("error", "boom", 3, 2, 3, 5))

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertEquals(1, result.itemDiagnostics.getValue("i0").size)
    assertTrue(result.unmatchedDiagnostics.isEmpty())
  }

  @Test
  fun `maps diagnostic inside second item`() {
    val file = worldFile(items = listOf(item("i0"), item("i1")))
    val ranges = mapOf(
      "i0" to range(0, 0, 0, 8),
      "i1" to range(2, 0, 3, 12),
    )
    val diagnostics = listOf(diagnostic("warning", "w", 2, 3, 2, 6))

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertTrue(result.itemDiagnostics.getValue("i0").isEmpty())
    assertEquals(1, result.itemDiagnostics.getValue("i1").size)
  }

  @Test
  fun `maps multiple diagnostics to same item`() {
    val file = worldFile(items = listOf(item("i0")))
    val ranges = mapOf("i0" to range(0, 0, 5, 0))
    val diagnostics = listOf(
      diagnostic("warning", "w1", 1, 0, 1, 2),
      diagnostic("error", "e1", 4, 1, 4, 4),
    )

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertEquals(2, result.itemDiagnostics.getValue("i0").size)
  }

  @Test
  fun `maps diagnostics across multiple items`() {
    val file = worldFile(items = listOf(item("i0"), item("i1"), item("i2")))
    val ranges = mapOf(
      "i0" to range(0, 0, 0, 10),
      "i1" to range(2, 0, 2, 10),
      "i2" to range(4, 0, 4, 10),
    )
    val diagnostics = listOf(
      diagnostic("warning", "w", 0, 2, 0, 3),
      diagnostic("error", "e", 2, 1, 2, 9),
      diagnostic("warning", "w2", 4, 0, 4, 1),
    )

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertEquals(1, result.itemDiagnostics.getValue("i0").size)
    assertEquals(1, result.itemDiagnostics.getValue("i1").size)
    assertEquals(1, result.itemDiagnostics.getValue("i2").size)
    assertTrue(result.unmatchedDiagnostics.isEmpty())
  }

  @Test
  fun `diagnostic outside all items is unmatched`() {
    val file = worldFile(items = listOf(item("i0")))
    val ranges = mapOf("i0" to range(10, 0, 12, 0))
    val diagnostics = listOf(diagnostic("error", "outside", 2, 0, 2, 1))

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertTrue(result.itemDiagnostics.getValue("i0").isEmpty())
    assertEquals(1, result.unmatchedDiagnostics.size)
  }

  @Test
  fun `overlap at boundary still maps`() {
    val file = worldFile(items = listOf(item("i0")))
    val ranges = mapOf("i0" to range(2, 0, 4, 10))
    val diagnostics = listOf(diagnostic("warning", "touch", 4, 10, 5, 0))

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertEquals(1, result.itemDiagnostics.getValue("i0").size)
    assertTrue(result.unmatchedDiagnostics.isEmpty())
  }

  @Test
  fun `maps severities to item statuses`() {
    val file = worldFile(items = listOf(item("i0"), item("i1"), item("i2"), item("i3")))
    val ranges = mapOf(
      "i0" to range(0, 0, 0, 10),
      "i1" to range(2, 0, 2, 10),
      "i2" to range(4, 0, 4, 10),
      "i3" to range(6, 0, 6, 10),
    )
    val diagnostics = listOf(
      diagnostic("warning", "w", 0, 1, 0, 2),
      diagnostic("error", "e", 2, 1, 2, 2),
      diagnostic("info", "unknown treated as error", 4, 1, 4, 2),
    )

    val result = mapper.mapDiagnostics(file, ranges, diagnostics)

    assertEquals(ItemStatus.WARNING, result.itemStatuses.getValue("i0"))
    assertEquals(ItemStatus.ERROR, result.itemStatuses.getValue("i1"))
    assertEquals(ItemStatus.ERROR, result.itemStatuses.getValue("i2"))
    assertEquals(ItemStatus.OK, result.itemStatuses.getValue("i3"))
  }

  @Test
  fun `file with no diagnostics has all items ok`() {
    val file = worldFile(items = listOf(item("i0"), item("i1")))
    val ranges = mapOf(
      "i0" to range(0, 0, 0, 10),
      "i1" to range(2, 0, 2, 10),
    )

    val result = mapper.mapDiagnostics(file, ranges, emptyList())

    assertEquals(ItemStatus.OK, result.itemStatuses.getValue("i0"))
    assertEquals(ItemStatus.OK, result.itemStatuses.getValue("i1"))
    assertTrue(result.unmatchedDiagnostics.isEmpty())
    assertTrue(result.itemDiagnostics.getValue("i0").isEmpty())
    assertTrue(result.itemDiagnostics.getValue("i1").isEmpty())
  }

  private fun worldFile(items: List<WorldItem>): WorldFile = WorldFile(
    path = "Main.lean",
    imports = emptyList(),
    items = items,
  )

  private fun item(id: String): WorldItem = WorldItem(
    id = id,
    filePath = "Main.lean",
    kind = ItemKind.RAW,
    name = null,
    title = "raw",
    code = "",
    range = null,
    status = ItemStatus.UNKNOWN,
    diagnostics = emptyList(),
    layout = null,
  )

  private fun diagnostic(severity: String?, message: String, sl: Int, sc: Int, el: Int, ec: Int): Diagnostic =
    Diagnostic(
      severity = severity,
      message = message,
      range = range(sl, sc, el, ec),
    )

  private fun range(sl: Int, sc: Int, el: Int, ec: Int): TextRange = TextRange(
    start = TextPosition(line = sl, character = sc),
    end = TextPosition(line = el, character = ec),
  )
}
