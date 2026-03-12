package com.deltalean.workspace.session

import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.WorldSnapshot
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceSessionTest {
  @Test
  fun `session stores snapshot and supports item lookup`() {
    val first = item(id = "Main.lean#item-0", filePath = "Main.lean", code = "def a := 1")
    val second = item(id = "Nested.lean#item-0", filePath = "Nested.lean", code = "def b := 2")
    val snapshot = WorldSnapshot(
      files = listOf(
        WorldFile(path = "Main.lean", imports = emptyList(), items = listOf(first)),
        WorldFile(path = "Nested.lean", imports = emptyList(), items = listOf(second)),
      )
    )

    val session = WorkspaceSession(root = Path.of("/tmp/workspace"), snapshot = snapshot)

    assertEquals(snapshot, session.getWorld())
    assertEquals(first, session.getItem(first.id))
    assertEquals(second, session.getItem(second.id))
    assertNull(session.getItem("unknown"))
  }

  private fun item(id: String, filePath: String, code: String): WorldItem = WorldItem(
    id = id,
    filePath = filePath,
    kind = ItemKind.RAW,
    name = null,
    title = "raw",
    code = code,
    range = null,
    status = ItemStatus.UNKNOWN,
    diagnostics = emptyList(),
    layout = null,
  )
}
