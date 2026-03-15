package com.deltalean.workspace.diagnostics

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.WorldSnapshot
import com.deltalean.workspace.session.WorkspaceSession
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticServiceTest {
  private val service = DiagnosticService()

  @Test
  fun `diagnostic inside item sets error status`() {
    val session = sessionWithFile(
      filePath = "Main.lean",
      items = listOf(item("i0", "def x : Nat := 1"))
    )

    service.applyDiagnostics(
      session,
      "Main.lean",
      listOf(diagnostic("error", "boom", 0, 4, 0, 6))
    )

    val updated = session.getItem("i0")!!
    assertEquals(ItemStatus.ERROR, updated.status)
    assertEquals(1, updated.diagnostics.size)
  }

  @Test
  fun `warning diagnostic sets warning status`() {
    val session = sessionWithFile(
      filePath = "Main.lean",
      items = listOf(item("i0", "def x : Nat := 1"))
    )

    service.applyDiagnostics(
      session,
      "Main.lean",
      listOf(diagnostic("warning", "careful", 0, 0, 0, 3))
    )

    val updated = session.getItem("i0")!!
    assertEquals(ItemStatus.WARNING, updated.status)
    assertEquals(1, updated.diagnostics.size)
  }

  @Test
  fun `multiple diagnostics are mapped to matching items`() {
    val session = sessionWithFile(
      filePath = "Main.lean",
      items = listOf(
        item("i0", "def first : Nat := 1"),
        item("i1", "def second : Nat := 2")
      )
    )

    service.applyDiagnostics(
      session,
      "Main.lean",
      listOf(
        diagnostic("warning", "w1", 0, 1, 0, 2),
        diagnostic("error", "e2", 2, 4, 2, 8),
      )
    )

    assertEquals(1, session.getItem("i0")!!.diagnostics.size)
    assertEquals(1, session.getItem("i1")!!.diagnostics.size)
    assertEquals(ItemStatus.WARNING, session.getItem("i0")!!.status)
    assertEquals(ItemStatus.ERROR, session.getItem("i1")!!.status)
  }

  @Test
  fun `unmatched diagnostics are preserved in session`() {
    val session = sessionWithFile(
      filePath = "Main.lean",
      items = listOf(item("i0", "def x : Nat := 1"))
    )

    service.applyDiagnostics(
      session,
      "Main.lean",
      listOf(diagnostic("error", "outside", 10, 0, 10, 3))
    )

    assertTrue(session.getItem("i0")!!.diagnostics.isEmpty())
    assertEquals(1, session.getUnmatchedDiagnostics("Main.lean").size)
  }

  @Test
  fun `applying diagnostics mutates snapshot in session`() {
    val session = sessionWithFile(
      filePath = "Main.lean",
      items = listOf(item("i0", "def x : Nat := 1"))
    )

    service.applyDiagnostics(session, "Main.lean", listOf(diagnostic("error", "boom", 0, 0, 0, 3)))

    val fromWorld = session.getWorld().files.first().items.first()
    assertEquals(ItemStatus.ERROR, fromWorld.status)
    assertEquals(1, fromWorld.diagnostics.size)
  }

  @Test
  fun `only target file is updated`() {
    val firstFile = WorldFile(
      path = "Main.lean",
      imports = emptyList(),
      items = listOf(item("i0", "def x : Nat := 1"))
    )
    val secondFile = WorldFile(
      path = "Other.lean",
      imports = emptyList(),
      items = listOf(item("j0", "def y : Nat := 2"))
    )
    val session = WorkspaceSession(
      root = Path.of("/tmp/workspace"),
      snapshot = WorldSnapshot(files = listOf(firstFile, secondFile))
    )

    service.applyDiagnostics(session, "Main.lean", listOf(diagnostic("error", "boom", 0, 0, 0, 3)))

    assertEquals(ItemStatus.ERROR, session.getItem("i0")!!.status)
    assertEquals(ItemStatus.UNKNOWN, session.getItem("j0")!!.status)
    assertTrue(session.getItem("j0")!!.diagnostics.isEmpty())
  }

  private fun sessionWithFile(filePath: String, items: List<WorldItem>): WorkspaceSession =
    WorkspaceSession(
      root = Path.of("/tmp/workspace"),
      snapshot = WorldSnapshot(
        files = listOf(
          WorldFile(path = filePath, imports = emptyList(), items = items)
        )
      )
    )

  private fun item(id: String, code: String): WorldItem = WorldItem(
    id = id,
    filePath = "Main.lean",
    kind = ItemKind.RAW,
    name = null,
    title = "raw",
    code = code,
    range = null,
    status = ItemStatus.UNKNOWN,
    diagnostics = emptyList(),
    layout = null,
  )

  private fun diagnostic(severity: String?, message: String, sl: Int, sc: Int, el: Int, ec: Int): Diagnostic =
    Diagnostic(
      severity = severity,
      message = message,
      range = TextRange(
        start = TextPosition(sl, sc),
        end = TextPosition(el, ec),
      )
    )
}
