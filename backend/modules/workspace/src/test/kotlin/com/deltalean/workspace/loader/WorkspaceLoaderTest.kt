package com.deltalean.workspace.loader

import com.deltalean.domain.world.ItemKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLoaderTest {
  private val roots = mutableListOf<Path>()
  private val loader = WorkspaceLoader()

  @AfterTest
  fun cleanup() {
    roots.forEach(::deleteRecursively)
    roots.clear()
  }

  @Test
  fun `loads workspace with one lean file`() {
    val root = createTempDirectory("workspace-loader-")
    roots.add(root)
    root.resolve("Main.lean").writeText("def x : Nat := 1\n")

    val snapshot = loader.loadWorkspace(root)

    assertEquals(1, snapshot.files.size)
    assertEquals("Main.lean", snapshot.files.first().path)
    assertEquals(1, snapshot.files.first().items.size)
  }

  @Test
  fun `loads workspace with multiple lean files`() {
    val root = createTempWorkspace()

    val snapshot = loader.loadWorkspace(root)

    assertEquals(3, snapshot.files.size)
  }

  @Test
  fun `loads nested lean files with normalized relative paths`() {
    val root = createTempWorkspace()

    val snapshot = loader.loadWorkspace(root)

    assertTrue(snapshot.files.any { it.path == "Nested/A.lean" })
    assertTrue(snapshot.files.any { it.path == "Nested/Deep/B.lean" })
  }

  @Test
  fun `ignores non-lean files`() {
    val root = createTempWorkspace()

    val snapshot = loader.loadWorkspace(root)

    assertTrue(snapshot.files.none { it.path.endsWith(".txt") })
    assertTrue(snapshot.files.none { it.path.endsWith(".md") })
  }

  @Test
  fun `uses deterministic ordering by relative path`() {
    val root = createTempWorkspace()

    val snapshot = loader.loadWorkspace(root)

    assertEquals(
      listOf("Main.lean", "Nested/A.lean", "Nested/Deep/B.lean"),
      snapshot.files.map { it.path }
    )
  }

  @Test
  fun `loads items from splitter output`() {
    val root = createTempDirectory("workspace-loader-")
    roots.add(root)
    root.resolve("Main.lean").writeText(
      "def first : Nat := 1\n\n" +
        "theorem second : True := by\n  trivial\n"
    )

    val snapshot = loader.loadWorkspace(root)
    val items = snapshot.files.first().items

    assertEquals(2, items.size)
    assertEquals(ItemKind.DEF, items[0].kind)
    assertEquals(ItemKind.THEOREM, items[1].kind)
  }

  @Test
  fun `captures context commands in file container metadata`() {
    val root = createTempDirectory("workspace-loader-")
    roots.add(root)
    root.resolve("Main.lean").writeText(
      "set_option pp.universes true\n\n" +
        "def x : Nat := 1\n"
    )

    val snapshot = loader.loadWorkspace(root)
    val file = snapshot.files.first()
    val items = file.items
    val rootContainer = file.containers.first { it.kind.name == "FILE" }

    assertEquals(1, items.size)
    assertEquals(ItemKind.DEF, items[0].kind)
    assertTrue(rootContainer.context.options.any { it.contains("set_option") })
  }

  private fun createTempWorkspace(): Path {
    val root = createTempDirectory("workspace-loader-")
    roots.add(root)

    root.resolve("Main.lean").writeText("def mainDef : Nat := 1\n")
    root.resolve("Nested").createDirectories()
    root.resolve("Nested/A.lean").writeText("theorem nested : True := by\n  trivial\n")
    root.resolve("Nested/Deep").createDirectories()
    root.resolve("Nested/Deep/B.lean").writeText("example : True := by\n  trivial\n")

    root.resolve("README.md").writeText("ignore")
    root.resolve("Nested/notes.txt").writeText("ignore")

    return root
  }

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) {
      return
    }

    Files.walk(root).use { stream ->
      stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}
