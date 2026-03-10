package com.deltalean.workspace

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceServiceTest {
  private val roots = mutableListOf<Path>()

  @AfterTest
  fun cleanup() {
    roots.forEach(::deleteRecursively)
    roots.clear()
  }

  @Test
  fun `lists lean files recursively with normalized relative separators`() {
    val root = createWorkspace()
    val service = WorkspaceService(root)

    val files = service.listLeanFiles()

    assertEquals(listOf("Main.lean", "Nested/Sub/Other.lean"), files)
  }

  @Test
  fun `read and write file by workspace relative path`() {
    val root = createWorkspace()
    val service = WorkspaceService(root)

    val original = service.readFile("Main.lean")
    assertEquals("theorem ok : True := by\n  trivial\n", original)

    service.writeFile("Main.lean", "theorem changed : True := by\n  trivial\n")
    val updated = service.readFile("Main.lean")
    assertEquals("theorem changed : True := by\n  trivial\n", updated)
  }

  @Test
  fun `rejects escaping paths outside workspace`() {
    val root = createWorkspace()
    val service = WorkspaceService(root)

    assertFailsWith<IllegalArgumentException> {
      service.readFile("../secret.lean")
    }
    assertFailsWith<IllegalArgumentException> {
      service.writeFile("../secret.lean", "x")
    }
  }

  @Test
  fun `throws on missing file`() {
    val root = createWorkspace()
    val service = WorkspaceService(root)

    assertFailsWith<NoSuchFileException> {
      service.readFile("Missing.lean")
    }
  }

  private fun createWorkspace(): Path {
    val root = createTempDirectory("deltalean-workspace-test-")
    roots.add(root)

    root.resolve("Main.lean").writeText("theorem ok : True := by\n  trivial\n")
    root.resolve("Nested/Sub").createDirectories()
    root.resolve("Nested/Sub/Other.lean").writeText("theorem other : True := by\n  trivial\n")
    root.resolve("Nested/Sub/Ignore.txt").writeText("ignore")

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
