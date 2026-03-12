package com.deltalean.app

import com.deltalean.lean.session.LeanSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class WorkspaceSessionServiceTest {
  private val roots = mutableListOf<Path>()

  @AfterTest
  fun cleanup() {
    roots.forEach(::deleteRecursively)
    roots.clear()
  }

  @Test
  fun `getSession throws when workspace not opened`() = runBlocking {
    val service = WorkspaceSessionService(sessionFactory = { mockk(relaxed = true) })

    assertFailsWith<IllegalStateException> {
      service.getSession()
    }
  }

  @Test
  fun `openWorkspace creates workspace session`() = runBlocking {
    val root = createTempDirectory("workspace-session-service-")
    roots.add(root)
    root.resolve("Main.lean").writeText("def x : Nat := 1\n")

    val leanSession = mockk<LeanSession>()
    coEvery { leanSession.start(any()) } returns Unit
    every { leanSession.openFile(any(), any()) } returns Unit
    every { leanSession.updateFile(any(), any()) } returns Unit
    every { leanSession.getAllDiagnostics() } returns emptyList()
    every { leanSession.getDiagnosticsForPath(any()) } answers {
      throw IllegalStateException("not used")
    }
    coEvery { leanSession.stop() } returns Unit

    val service = WorkspaceSessionService(sessionFactory = { leanSession })
    service.openWorkspace(root.toString())

    val session = service.getSession()
    val snapshot = session.getWorld()

    assertEquals(1, snapshot.files.size)
    assertEquals("Main.lean", snapshot.files.first().path)
    assertNotNull(session.getItem(snapshot.files.first().items.first().id))
  }

  @Test
  fun `updating item code updates snapshot and rewrites file`() = runBlocking {
    val root = createTempDirectory("workspace-session-service-")
    roots.add(root)
    val filePath = root.resolve("Main.lean")
    filePath.writeText(
      "def first : Nat := 1\n\n" +
        "def second : Nat := 2\n"
    )

    val service = WorkspaceSessionService(sessionFactory = { relaxedLeanSession() })
    service.openWorkspace(root.toString())

    val sessionBefore = service.getSession()
    val worldBefore = sessionBefore.getWorld()
    val targetItem = worldBefore.files.first().items.first { it.code.contains("def first") }

    service.updateItemCode(targetItem.id, "def first : Nat := 42")

    val worldAfter = service.getSession().getWorld()
    val updated = worldAfter.files.first().items.first { it.id == targetItem.id }
    val untouched = worldAfter.files.first().items.first { it.id != targetItem.id }

    assertTrue(updated.code.contains("42"))
    assertTrue(untouched.code.contains("def second"))
    assertTrue(service.getSession().getItem(targetItem.id)?.code?.contains("42") == true)

    val persisted = Files.readString(filePath)
    assertTrue(persisted.contains("def first : Nat := 42"))
    assertTrue(persisted.contains("def second : Nat := 2"))
  }

  @Test
  fun `updating unknown item code fails`() = runBlocking {
    val root = createTempDirectory("workspace-session-service-")
    roots.add(root)
    root.resolve("Main.lean").writeText("def x : Nat := 1\n")

    val service = WorkspaceSessionService(sessionFactory = { relaxedLeanSession() })
    service.openWorkspace(root.toString())

    assertFailsWith<NoSuchElementException> {
      service.updateItemCode("missing-item", "def x : Nat := 2")
    }
  }

  private fun relaxedLeanSession(): LeanSession {
    val leanSession = mockk<LeanSession>()
    coEvery { leanSession.start(any()) } returns Unit
    every { leanSession.openFile(any(), any()) } returns Unit
    every { leanSession.updateFile(any(), any()) } returns Unit
    every { leanSession.getAllDiagnostics() } returns emptyList()
    every { leanSession.getDiagnosticsForPath(any()) } answers {
      throw IllegalStateException("not used")
    }
    coEvery { leanSession.stop() } returns Unit
    return leanSession
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
