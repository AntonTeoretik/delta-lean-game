package com.deltalean.workspace.importer

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem

/**
 * Heuristic Lean splitter for MVP import flow.
 *
 * This is intentionally not a full parser. It extracts leading imports and splits the rest
 * into top-level declaration-like blocks. Unsupported or uncertain chunks are represented as RAW.
 */
class LeanFileSplitter {
  fun split(path: String, content: String): WorldFile {
    val lines = parseLines(content)

    val imports = mutableListOf<String>()
    var bodyStartLine = 0
    while (bodyStartLine < lines.size) {
      val trimmed = lines[bodyStartLine].trimmed
      when {
        trimmed.isBlank() -> bodyStartLine += 1
        isImportLine(trimmed) -> {
          imports += lines[bodyStartLine].text
          bodyStartLine += 1
        }
        else -> break
      }
    }

    val declarationStarts = mutableListOf<Int>()
    for (lineIndex in bodyStartLine until lines.size) {
      if (matchDeclarationKeyword(lines[lineIndex].trimmed) != null) {
        declarationStarts += lineIndex
      }
    }

    val items = mutableListOf<WorldItem>()
    var itemIndex = 0

    if (declarationStarts.isEmpty()) {
      val rawCode = extractChunk(content, lines, bodyStartLine, lines.size)
      if (rawCode.isNotBlank()) {
        items += createItem(
          path = path,
          itemIndex = itemIndex,
          kind = ItemKind.RAW,
          name = null,
          title = "raw",
          code = rawCode,
        )
      }
      return WorldFile(path = path, imports = imports, items = items)
    }

    var cursor = bodyStartLine
    declarationStarts.forEachIndexed { startIndex, declarationStartLine ->
      if (chunkHasContent(content, lines, cursor, declarationStartLine)) {
        val rawCode = extractChunk(content, lines, cursor, declarationStartLine)
        items += createItem(
          path = path,
          itemIndex = itemIndex++,
          kind = ItemKind.RAW,
          name = null,
          title = "raw",
          code = rawCode,
        )
      }

      val nextDeclarationStartLine = declarationStarts.getOrNull(startIndex + 1) ?: lines.size
      val declarationEndLine = findDeclarationEndLine(
        lines = lines,
        declarationStartLine = declarationStartLine,
        nextDeclarationStartLine = nextDeclarationStartLine,
      )
      val declarationCode = extractChunk(content, lines, declarationStartLine, declarationEndLine)
      val keyword = matchDeclarationKeyword(lines[declarationStartLine].trimmed)
      val kind = keyword?.let(::keywordToKind) ?: ItemKind.RAW
      val name = if (keyword == null) null else extractName(lines[declarationStartLine].trimmed, keyword)
      val title = when {
        name != null -> name
        kind == ItemKind.EXAMPLE -> "example"
        kind == ItemKind.RAW -> "raw"
        else -> keyword?.lowercase() ?: "raw"
      }

      items += createItem(
        path = path,
        itemIndex = itemIndex++,
        kind = kind,
        name = name,
        title = title,
        code = declarationCode,
      )

      cursor = declarationEndLine
    }

    if (chunkHasContent(content, lines, cursor, lines.size)) {
      val trailingRawCode = extractChunk(content, lines, cursor, lines.size)
      items += createItem(
        path = path,
        itemIndex = itemIndex,
        kind = ItemKind.RAW,
        name = null,
        title = "raw",
        code = trailingRawCode,
      )
    }

    return WorldFile(path = path, imports = imports, items = items)
  }

  private fun createItem(
    path: String,
    itemIndex: Int,
    kind: ItemKind,
    name: String?,
    title: String,
    code: String,
  ): WorldItem = WorldItem(
    id = "$path#item-$itemIndex",
    filePath = path,
    kind = kind,
    name = name,
    title = title,
    code = code,
    range = null,
    status = ItemStatus.UNKNOWN,
    diagnostics = emptyList<Diagnostic>(),
    layout = null,
  )

  private fun keywordToKind(keyword: String): ItemKind = when (keyword.lowercase()) {
    "def" -> ItemKind.DEF
    "theorem" -> ItemKind.THEOREM
    "inductive" -> ItemKind.INDUCTIVE
    "structure" -> ItemKind.STRUCTURE
    "class" -> ItemKind.CLASS
    "instance" -> ItemKind.INSTANCE
    "axiom" -> ItemKind.AXIOM
    "abbrev" -> ItemKind.ABBREV
    "opaque" -> ItemKind.OPAQUE
    "example" -> ItemKind.EXAMPLE
    else -> ItemKind.RAW
  }

  private fun extractName(trimmedLine: String, keyword: String): String? {
    if (keyword.equals("example", ignoreCase = true)) {
      return null
    }

    val rest = trimmedLine.removePrefix(keyword).trimStart()
    val match = NAME_REGEX.find(rest) ?: return null
    return match.groupValues[1]
  }

  private fun isImportLine(trimmedLine: String): Boolean = trimmedLine.startsWith("import ")

  private fun matchDeclarationKeyword(trimmedLine: String): String? {
    val match = DECLARATION_START_REGEX.find(trimmedLine) ?: return null
    return match.groupValues[1]
  }

  private fun chunkHasContent(content: String, lines: List<LineInfo>, startLine: Int, endLine: Int): Boolean {
    val chunk = extractChunk(content, lines, startLine, endLine)
    return chunk.isNotBlank()
  }

  private fun findDeclarationEndLine(
    lines: List<LineInfo>,
    declarationStartLine: Int,
    nextDeclarationStartLine: Int,
  ): Int {
    for (lineIndex in (declarationStartLine + 1) until nextDeclarationStartLine) {
      val line = lines[lineIndex]
      if (isLooseTopLevelLine(line)) {
        return lineIndex
      }
    }

    return nextDeclarationStartLine
  }

  private fun isLooseTopLevelLine(line: LineInfo): Boolean {
    if (line.trimmed.isBlank()) {
      return false
    }
    if (line.hasLeadingWhitespace) {
      return false
    }
    if (line.trimmed.startsWith("--")) {
      return true
    }

    return matchDeclarationKeyword(line.trimmed) == null
  }

  private fun extractChunk(content: String, lines: List<LineInfo>, startLine: Int, endLine: Int): String {
    if (startLine >= endLine || startLine >= lines.size) {
      return ""
    }

    val startOffset = lines[startLine].startOffset
    val endOffset = if (endLine >= lines.size) content.length else lines[endLine].startOffset
    return content.substring(startOffset, endOffset)
  }

  private fun parseLines(content: String): List<LineInfo> {
    if (content.isEmpty()) {
      return emptyList()
    }

    val lines = mutableListOf<LineInfo>()
    var cursor = 0
    while (cursor < content.length) {
      val start = cursor
      while (cursor < content.length && content[cursor] != '\n') {
        cursor += 1
      }
      val endWithoutNewline = cursor
      if (cursor < content.length && content[cursor] == '\n') {
        cursor += 1
      }

      val lineText = content.substring(start, endWithoutNewline).removeSuffix("\r")
      lines += LineInfo(
        text = lineText,
        trimmed = lineText.trimStart(),
        startOffset = start,
        hasLeadingWhitespace = lineText.firstOrNull()?.isWhitespace() == true,
      )
    }

    return lines
  }

  private data class LineInfo(
    val text: String,
    val trimmed: String,
    val startOffset: Int,
    val hasLeadingWhitespace: Boolean,
  )

  private companion object {
    val DECLARATION_START_REGEX =
      Regex("""^(def|theorem|inductive|structure|class|instance|axiom|abbrev|opaque|example)\b""")

    val NAME_REGEX = Regex("""^([A-Za-z_][A-Za-z0-9_'.]*)""")
  }
}
