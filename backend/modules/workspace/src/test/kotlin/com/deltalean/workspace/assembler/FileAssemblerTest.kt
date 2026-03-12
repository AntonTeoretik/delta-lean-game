package com.deltalean.workspace.assembler

import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.Diagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileAssemblerTest {
  private val assembler = FileAssembler()

  @Test
  fun `assemble file with no imports and one item`() {
    val file = worldFile(items = listOf(item("Main.lean#item-0", "def x : Nat := 1")))

    val result = assembler.assemble(file)

    assertEquals("def x : Nat := 1", result.text)
  }

  @Test
  fun `assemble file with imports and one item`() {
    val file = worldFile(
      imports = listOf("import Mathlib.Data.Nat.Basic", "import Mathlib.Algebra.Group.Defs"),
      items = listOf(item("Main.lean#item-0", "def x : Nat := 1")),
    )

    val result = assembler.assemble(file)

    assertEquals(
      "import Mathlib.Data.Nat.Basic\n" +
        "import Mathlib.Algebra.Group.Defs\n" +
        "\n" +
        "def x : Nat := 1",
      result.text,
    )
  }

  @Test
  fun `assemble file with multiple items`() {
    val file = worldFile(
      items = listOf(
        item("Main.lean#item-0", "def a : Nat := 1"),
        item("Main.lean#item-1", "theorem b : True := by\n  trivial"),
        item("Main.lean#item-2", "example : True := by\n  trivial"),
      ),
    )

    val result = assembler.assemble(file)

    assertEquals(
      "def a : Nat := 1\n" +
        "\n" +
        "theorem b : True := by\n" +
        "  trivial\n" +
        "\n" +
        "example : True := by\n" +
        "  trivial",
      result.text,
    )
  }

  @Test
  fun `assemble file with only imports`() {
    val file = worldFile(imports = listOf("import A", "import B"), items = emptyList())

    val result = assembler.assemble(file)

    assertEquals("import A\nimport B", result.text)
    assertTrue(result.itemRanges.isEmpty())
  }

  @Test
  fun `assemble empty file`() {
    val result = assembler.assemble(worldFile(imports = emptyList(), items = emptyList()))

    assertEquals("", result.text)
    assertTrue(result.itemRanges.isEmpty())
  }

  @Test
  fun `compute correct range for single-line item`() {
    val item = item("Main.lean#item-0", "def x : Nat := 1")

    val result = assembler.assemble(worldFile(items = listOf(item)))

    assertRange(result.itemRanges[item.id], 0, 0, 0, 15)
  }

  @Test
  fun `compute correct range for multi-line item`() {
    val item = item("Main.lean#item-0", "theorem t : True := by\n  trivial")

    val result = assembler.assemble(worldFile(items = listOf(item)))

    assertRange(result.itemRanges[item.id], 0, 0, 1, 8)
  }

  @Test
  fun `compute correct ranges for multiple items with one blank separator`() {
    val first = item("Main.lean#item-0", "def a : Nat := 1")
    val second = item("Main.lean#item-1", "def b : Nat := 2")

    val result = assembler.assemble(worldFile(items = listOf(first, second)))

    assertEquals("def a : Nat := 1\n\ndef b : Nat := 2", result.text)
    assertRange(result.itemRanges[first.id], 0, 0, 0, 15)
    assertRange(result.itemRanges[second.id], 2, 0, 2, 15)
  }

  @Test
  fun `preserve item order`() {
    val first = item("Main.lean#item-0", "def first := 1")
    val second = item("Main.lean#item-1", "def second := 2")
    val third = item("Main.lean#item-2", "def third := 3")

    val result = assembler.assemble(worldFile(items = listOf(first, second, third)))

    assertTrue(result.text.indexOf("def first") < result.text.indexOf("def second"))
    assertTrue(result.text.indexOf("def second") < result.text.indexOf("def third"))
  }

  @Test
  fun `itemRanges contain all item ids exactly once`() {
    val first = item("Main.lean#item-0", "def first := 1")
    val second = item("Main.lean#item-1", "def second := 2")
    val third = item("Main.lean#item-2", "def third := 3")

    val result = assembler.assemble(worldFile(items = listOf(first, second, third)))

    assertEquals(setOf(first.id, second.id, third.id), result.itemRanges.keys)
    assertEquals(3, result.itemRanges.size)
  }

  private fun worldFile(
    imports: List<String> = emptyList(),
    items: List<WorldItem> = emptyList(),
  ): WorldFile = WorldFile(
    path = "Main.lean",
    imports = imports,
    items = items,
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
    diagnostics = emptyList<Diagnostic>(),
    layout = null,
  )

  private fun assertRange(range: TextRange?, startLine: Int, startCharacter: Int, endLine: Int, endCharacter: Int) {
    assertEquals(
      TextRange(
        start = TextPosition(startLine, startCharacter),
        end = TextPosition(endLine, endCharacter),
      ),
      range,
    )
  }
}
