package com.deltalean.workspace.assembler

import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile

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

    file.items.forEachIndexed { index, item ->
      if (index > 0) {
        append(builder, cursor, "\n\n")
      }

      val itemText = normalizeItemCode(item.code)
      val start = cursor.position()
      val endInclusive = append(builder, cursor, itemText)
      val end = endInclusive ?: start

      itemRanges[item.id] = TextRange(
        start = start,
        end = end,
      )
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
