package com.deltalean.workspace.importer

import com.deltalean.domain.world.ItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeanFileSplitterTest {
  private val splitter = LeanFileSplitter()

  @Test
  fun `splits file with only one def`() {
    val content = "def addOne (n : Nat) : Nat :=\n  n + 1\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertTrue(result.imports.isEmpty())
    assertEquals(1, result.items.size)
    assertEquals(ItemKind.DEF, result.items[0].kind)
    assertEquals("addOne", result.items[0].name)
    assertEquals("addOne", result.items[0].title)
    assertEquals(content, result.items[0].code)
  }

  @Test
  fun `extracts imports and one definition`() {
    val content =
      "import Mathlib.Data.Nat.Basic\n" +
        "import Mathlib.Algebra.Group.Defs\n" +
        "\n" +
        "def foo : Nat := 1\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertEquals(
      listOf(
        "import Mathlib.Data.Nat.Basic",
        "import Mathlib.Algebra.Group.Defs",
      ),
      result.imports,
    )
    assertEquals(1, result.items.size)
    assertEquals(ItemKind.DEF, result.items[0].kind)
    assertEquals("foo", result.items[0].name)
  }

  @Test
  fun `splits multiple recognized declarations`() {
    val content =
      "def first : Nat := 1\n" +
        "\n" +
        "theorem second : True := by\n" +
        "  trivial\n" +
        "\n" +
        "inductive Thing where\n" +
        "  | mk\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertEquals(3, result.items.size)
    assertEquals(listOf(ItemKind.DEF, ItemKind.THEOREM, ItemKind.INDUCTIVE), result.items.map { it.kind })
    assertEquals("first", result.items[0].name)
    assertEquals("second", result.items[1].name)
    assertEquals("Thing", result.items[2].name)
  }

  @Test
  fun `unknown top-level text becomes raw`() {
    val content =
      "set_option pp.universes true\n" +
        "\n" +
        "def ok : Nat := 1\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertEquals(2, result.items.size)
    assertEquals(ItemKind.RAW, result.items[0].kind)
    assertTrue(result.items[0].code.contains("set_option"))
    assertEquals(ItemKind.DEF, result.items[1].kind)
  }

  @Test
  fun `example declaration has null name and readable title`() {
    val content =
      "example : True := by\n" +
        "  trivial\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertEquals(1, result.items.size)
    assertEquals(ItemKind.EXAMPLE, result.items[0].kind)
    assertNull(result.items[0].name)
    assertEquals("example", result.items[0].title)
  }

  @Test
  fun `content between declarations is preserved as raw`() {
    val content =
      "def a : Nat := 1\n" +
        "\n" +
        "#check a\n" +
        "\n" +
        "theorem t : True := by\n" +
        "  trivial\n"

    val result = splitter.split(path = "Main.lean", content = content)

    assertEquals(3, result.items.size)
    assertEquals(ItemKind.DEF, result.items[0].kind)
    assertEquals(ItemKind.RAW, result.items[1].kind)
    assertTrue(result.items[1].code.contains("#check a"))
    assertEquals(ItemKind.THEOREM, result.items[2].kind)
  }

  @Test
  fun `comments do not cause content loss`() {
    val content =
      "-- intro comment\n" +
        "-- second comment\n" +
        "\n" +
        "def value : Nat := 42\n"

    val result = splitter.split(path = "Main.lean", content = content)
    val reconstructed = result.items.joinToString(separator = "") { it.code }

    assertEquals(content, reconstructed)
    assertEquals(ItemKind.RAW, result.items[0].kind)
    assertEquals(ItemKind.DEF, result.items[1].kind)
  }
}
