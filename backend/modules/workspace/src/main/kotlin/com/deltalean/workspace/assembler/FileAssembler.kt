package com.deltalean.workspace.assembler

import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldContainer
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.ContainerKind
import com.deltalean.domain.world.WorldItem

/**
 * Deterministic assembler for a single [WorldFile].
 *
 * Assembly contract:
 * - imports are emitted first, one per line, in order
 * - if imports and items both exist, exactly one blank line is inserted between them
 * - items are emitted in order, with exactly one blank line between neighboring items
 * - output uses '\n' newlines
 */
class FileAssembler {
  fun assemble(file: WorldFile): FileAssemblyResult {
    val builder = StringBuilder()
    val cursor = Cursor()
    val itemRanges = linkedMapOf<String, TextRange>()

    file.imports.forEachIndexed { index, importLine ->
      append(builder, cursor, normalizeImport(importLine))
      if (index < file.imports.lastIndex) {
        append(builder, cursor, "\n")
      }
    }

    if (file.imports.isNotEmpty() && file.items.isNotEmpty()) {
      append(builder, cursor, "\n\n")
    }

    if (file.containers.isEmpty()) {
      renderPlainItems(file.items, builder, cursor, itemRanges)
    } else {
      renderContainerAware(file, builder, cursor, itemRanges)
    }

    return FileAssemblyResult(
      text = builder.toString(),
      itemRanges = itemRanges,
    )
  }

  private fun append(builder: StringBuilder, cursor: Cursor, text: String): TextPosition? {
    if (text.isEmpty()) {
      return null
    }

    var lastPosition: TextPosition? = null
    text.forEach { ch ->
      builder.append(ch)
      lastPosition = cursor.position()
      cursor.advance(ch)
    }
    return lastPosition
  }

  private fun normalizeImport(line: String): String = normalizeNewlines(line).trimEnd('\n')

  private fun normalizeItemCode(code: String): String =
    normalizeNewlines(code)
      .trimStart('\n')
      .trimEnd('\n')

  private fun renderPlainItems(
    items: List<WorldItem>,
    builder: StringBuilder,
    cursor: Cursor,
    itemRanges: MutableMap<String, TextRange>
  ) {
    items.forEachIndexed { index, item ->
      if (index > 0) {
        append(builder, cursor, "\n\n")
      }

      appendItem(item, builder, cursor, itemRanges)
    }
  }

  private fun renderContainerAware(
    file: WorldFile,
    builder: StringBuilder,
    cursor: Cursor,
    itemRanges: MutableMap<String, TextRange>
  ) {
    val root = file.containers.firstOrNull { it.kind == ContainerKind.FILE && it.parentContainerId == null }
      ?: return renderPlainItems(file.items, builder, cursor, itemRanges)

    val containerOrder = file.containers.withIndex().associate { it.value.id to it.index }
    val itemOrder = file.items.withIndex().associate { it.value.id to it.index }
    val childContainers = file.containers
      .filter { it.parentContainerId != null }
      .groupBy { it.parentContainerId!! }
    val childItems = file.items
      .filter { it.parentContainerId != null }
      .groupBy { it.parentContainerId!! }

    fun renderContainer(container: WorldContainer) {
      if (container.kind != ContainerKind.FILE) {
        append(builder, cursor, startLineFor(container))
      }

      val bodyEntries = mutableListOf<BodyEntry>()
      bodyEntries += container.context.variables.map { BodyEntry.ContextLine(it, -1_000_000) }
      bodyEntries += container.context.opens.map { BodyEntry.ContextLine(it, -900_000) }
      bodyEntries += container.context.openScoped.map { BodyEntry.ContextLine(it, -800_000) }
      bodyEntries += container.context.universes.map { BodyEntry.ContextLine(it, -700_000) }
      bodyEntries += container.context.options.map { BodyEntry.ContextLine(it, -600_000) }
      bodyEntries += container.context.attributes.map { BodyEntry.ContextLine(it, -500_000) }
      bodyEntries += container.context.notations.map { BodyEntry.ContextLine(it, -400_000) }

      bodyEntries += childContainers[container.id].orEmpty().map { child ->
        BodyEntry.ChildContainer(child, containerOrder[child.id] ?: Int.MAX_VALUE)
      }
      bodyEntries += childItems[container.id].orEmpty().map { child ->
        BodyEntry.ChildItem(child, itemOrder[child.id] ?: Int.MAX_VALUE)
      }

      val sortedEntries = bodyEntries.sortedBy { it.order }
      if (sortedEntries.isNotEmpty()) {
        if (container.kind != ContainerKind.FILE) {
          append(builder, cursor, "\n\n")
        }

        sortedEntries.forEachIndexed { index, entry ->
          if (index > 0) {
            append(builder, cursor, "\n\n")
          }

          when (entry) {
            is BodyEntry.ContextLine -> append(builder, cursor, normalizeImport(entry.value))
            is BodyEntry.ChildContainer -> renderContainer(entry.value)
            is BodyEntry.ChildItem -> appendItem(entry.value, builder, cursor, itemRanges)
          }
        }
      }

      if (container.kind != ContainerKind.FILE) {
        append(builder, cursor, "\n\nend")
      }
    }

    renderContainer(root)
  }

  private fun appendItem(
    item: WorldItem,
    builder: StringBuilder,
    cursor: Cursor,
    itemRanges: MutableMap<String, TextRange>
  ) {
    val itemText = normalizeItemCode(item.code)
    val start = cursor.position()
    val endInclusive = append(builder, cursor, itemText)
    val end = endInclusive ?: start

    itemRanges[item.id] = TextRange(start = start, end = end)
  }

  private fun startLineFor(container: WorldContainer): String {
    return when (container.kind) {
      ContainerKind.NAMESPACE -> if (container.title.isBlank() || container.title == "namespace") {
        "namespace"
      } else {
        "namespace ${container.title}"
      }

      ContainerKind.SECTION -> if (container.title.isBlank() || container.title == "section") {
        "section"
      } else {
        "section ${container.title}"
      }

      ContainerKind.FILE -> ""
    }
  }

  private sealed class BodyEntry(val order: Int) {
    class ContextLine(val value: String, order: Int) : BodyEntry(order)
    class ChildContainer(val value: WorldContainer, order: Int) : BodyEntry(order)
    class ChildItem(val value: WorldItem, order: Int) : BodyEntry(order)
  }

  private fun normalizeNewlines(text: String): String =
    text.replace("\r\n", "\n").replace("\r", "\n")

  private class Cursor(
    private var line: Int = 0,
    private var character: Int = 0,
  ) {
    fun position(): TextPosition = TextPosition(line = line, character = character)

    fun advance(ch: Char) {
      if (ch == '\n') {
        line += 1
        character = 0
      } else {
        character += 1
      }
    }
  }
}
