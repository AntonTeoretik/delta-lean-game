package com.deltalean.workspace.importer

import com.deltalean.domain.world.ContainerContext
import com.deltalean.domain.world.ContainerKind
import com.deltalean.domain.world.ContainerLayout
import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemKind
import com.deltalean.domain.world.ItemStatus
import com.deltalean.domain.world.WorldContainer
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem

/**
 * Heuristic Lean splitter for MVP import flow with container awareness.
 *
 * This is intentionally not a full parser. It extracts leading imports, captures `namespace`/`section`
 * as containers, stores contextual commands as container metadata, and emits declaration items.
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

    val rootContainerId = "$path#container-root"
    val containers = mutableListOf(
      MutableContainer(
        id = rootContainerId,
        kind = ContainerKind.FILE,
        title = path,
        filePath = path,
        parentContainerId = null,
        layout = ContainerLayout(x = 0.0, y = 0.0, width = 1400.0, height = 1000.0),
      )
    )
    val containerById = hashMapOf(rootContainerId to containers.first())
    val containerStack = ArrayDeque<String>().apply { addLast(rootContainerId) }

    val items = mutableListOf<WorldItem>()
    var itemIndex = 0
    var containerIndex = 0
    var chunkStartLine: Int? = null
    var chunkKind: ChunkKind = ChunkKind.RAW
    var chunkKeyword: String? = null
    var chunkParentContainerId = rootContainerId

    fun currentContainer(): MutableContainer = containerById.getValue(containerStack.last())

    fun startChunkIfNeeded(lineIndex: Int, kind: ChunkKind, keyword: String? = null) {
      if (chunkStartLine == null) {
        chunkStartLine = lineIndex
        chunkKind = kind
        chunkKeyword = keyword
        chunkParentContainerId = containerStack.last()
      }
    }

    fun flushChunk(endLineExclusive: Int) {
      val startLine = chunkStartLine ?: return
      val chunkCode = extractChunk(content, lines, startLine, endLineExclusive)
      if (chunkCode.isBlank()) {
        chunkStartLine = null
        chunkKeyword = null
        chunkKind = ChunkKind.RAW
        return
      }

      val (kind, name, title) = when {
        chunkKind == ChunkKind.DECLARATION && chunkKeyword != null -> {
          val keyword = chunkKeyword!!
          val firstTrimmed = lines[startLine].trimmed
          val itemKind = keywordToKind(keyword)
          val itemName = extractName(firstTrimmed, keyword)
          val itemTitle = when {
            itemName != null -> itemName
            itemKind == ItemKind.EXAMPLE -> "example"
            else -> keyword.lowercase()
          }
          Triple(itemKind, itemName, itemTitle)
        }

        else -> Triple(ItemKind.RAW, null, "raw")
      }

      items += WorldItem(
        id = "$path#item-$itemIndex",
        filePath = path,
        parentContainerId = chunkParentContainerId,
        kind = kind,
        name = name,
        title = title,
        code = chunkCode,
        range = null,
        status = ItemStatus.UNKNOWN,
        diagnostics = emptyList<Diagnostic>(),
        layout = null,
      )
      itemIndex += 1

      chunkStartLine = null
      chunkKeyword = null
      chunkKind = ChunkKind.RAW
    }

    for (lineIndex in bodyStartLine until lines.size) {
      val line = lines[lineIndex]

      if (chunkStartLine != null && chunkKind == ChunkKind.DECLARATION && isLooseTopLevelLine(line)) {
        flushChunk(lineIndex)
      }

      val namespaceName = namespaceStartName(line.trimmed)
      if (namespaceName != null) {
        flushChunk(lineIndex)

        val parentId = containerStack.last()
        val childCount = containers.count { it.parentContainerId == parentId }
        val id = "$path#container-${containerIndex++}"
        val container = MutableContainer(
          id = id,
          kind = ContainerKind.NAMESPACE,
          title = namespaceName.ifBlank { "namespace" },
          filePath = path,
          parentContainerId = parentId,
          layout = ContainerLayout(
            x = 30.0 + childCount * 20.0,
            y = 40.0 + childCount * 24.0,
            width = 980.0,
            height = 680.0,
          ),
        )
        containers += container
        containerById[id] = container
        containerStack.addLast(id)
        continue
      }

      val sectionName = sectionStartName(line.trimmed)
      if (sectionName != null) {
        flushChunk(lineIndex)

        val parentId = containerStack.last()
        val childCount = containers.count { it.parentContainerId == parentId }
        val id = "$path#container-${containerIndex++}"
        val container = MutableContainer(
          id = id,
          kind = ContainerKind.SECTION,
          title = sectionName.ifBlank { "section" },
          filePath = path,
          parentContainerId = parentId,
          layout = ContainerLayout(
            x = 40.0 + childCount * 18.0,
            y = 52.0 + childCount * 22.0,
            width = 920.0,
            height = 620.0,
          ),
        )
        containers += container
        containerById[id] = container
        containerStack.addLast(id)
        continue
      }

      if (isEndLine(line.trimmed)) {
        flushChunk(lineIndex)
        if (containerStack.size > 1) {
          containerStack.removeLast()
        }
        continue
      }

      val contextKind = contextKind(line.trimmed)
      if (contextKind != null) {
        flushChunk(lineIndex)
        currentContainer().addContext(contextKind, line.text)
        continue
      }

      val keyword = matchDeclarationKeyword(line.trimmed)
      if (keyword != null) {
        flushChunk(lineIndex)
        startChunkIfNeeded(lineIndex, ChunkKind.DECLARATION, keyword)
        continue
      }

      if (chunkStartLine == null && !line.trimmed.isBlank()) {
        startChunkIfNeeded(lineIndex, ChunkKind.RAW)
      }
    }

    flushChunk(lines.size)

    return WorldFile(
      path = path,
      imports = imports,
      items = items,
      containers = containers.map { it.toWorldContainer() },
    )
  }

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

  private fun namespaceStartName(trimmedLine: String): String? {
    val match = NAMESPACE_START_REGEX.find(trimmedLine) ?: return null
    return match.groupValues.getOrNull(1)?.trim().orEmpty()
  }

  private fun sectionStartName(trimmedLine: String): String? {
    val match = SECTION_START_REGEX.find(trimmedLine) ?: return null
    return match.groupValues.getOrNull(1)?.trim().orEmpty()
  }

  private fun isEndLine(trimmedLine: String): Boolean = END_REGEX.containsMatchIn(trimmedLine)

  private fun contextKind(trimmedLine: String): ContextKind? {
    return when {
      trimmedLine.startsWith("variable ") || trimmedLine.startsWith("variables ") -> ContextKind.VARIABLE
      trimmedLine.startsWith("open scoped ") -> ContextKind.OPEN_SCOPED
      trimmedLine.startsWith("open ") -> ContextKind.OPEN
      trimmedLine.startsWith("universe ") || trimmedLine.startsWith("universes ") -> ContextKind.UNIVERSE
      trimmedLine.startsWith("set_option ") -> ContextKind.OPTION
      trimmedLine.startsWith("attribute ") || trimmedLine.startsWith("local attribute ") -> ContextKind.ATTRIBUTE
      trimmedLine.startsWith("notation ") || trimmedLine.startsWith("infix") || trimmedLine.startsWith("prefix") -> ContextKind.NOTATION
      else -> null
    }
  }

  private fun isLooseTopLevelLine(line: LineInfo): Boolean {
    if (line.trimmed.isBlank()) {
      return false
    }
    val hasLeadingWhitespace = line.text.firstOrNull()?.isWhitespace() == true
    if (hasLeadingWhitespace) {
      return false
    }
    if (line.trimmed.startsWith("--")) {
      return false
    }
    if (matchDeclarationKeyword(line.trimmed) != null) {
      return false
    }
    if (namespaceStartName(line.trimmed) != null || sectionStartName(line.trimmed) != null || isEndLine(line.trimmed)) {
      return false
    }
    if (contextKind(line.trimmed) != null) {
      return false
    }
    return true
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
      )
    }

    return lines
  }

  private data class LineInfo(
    val text: String,
    val trimmed: String,
    val startOffset: Int,
  )

  private data class MutableContainer(
    val id: String,
    val kind: ContainerKind,
    val title: String,
    val filePath: String,
    val parentContainerId: String?,
    val layout: ContainerLayout,
    val variables: MutableList<String> = mutableListOf(),
    val opens: MutableList<String> = mutableListOf(),
    val openScoped: MutableList<String> = mutableListOf(),
    val universes: MutableList<String> = mutableListOf(),
    val options: MutableList<String> = mutableListOf(),
    val attributes: MutableList<String> = mutableListOf(),
    val notations: MutableList<String> = mutableListOf(),
  ) {
    fun addContext(kind: ContextKind, line: String) {
      when (kind) {
        ContextKind.VARIABLE -> variables += line
        ContextKind.OPEN -> opens += line
        ContextKind.OPEN_SCOPED -> openScoped += line
        ContextKind.UNIVERSE -> universes += line
        ContextKind.OPTION -> options += line
        ContextKind.ATTRIBUTE -> attributes += line
        ContextKind.NOTATION -> notations += line
      }
    }

    fun toWorldContainer(): WorldContainer = WorldContainer(
      id = id,
      kind = kind,
      title = title,
      filePath = filePath,
      parentContainerId = parentContainerId,
      layout = layout,
      context = ContainerContext(
        variables = variables.toList(),
        opens = opens.toList(),
        openScoped = openScoped.toList(),
        universes = universes.toList(),
        options = options.toList(),
        attributes = attributes.toList(),
        notations = notations.toList(),
      )
    )
  }

  private enum class ChunkKind {
    DECLARATION,
    RAW,
  }

  private enum class ContextKind {
    VARIABLE,
    OPEN,
    OPEN_SCOPED,
    UNIVERSE,
    OPTION,
    ATTRIBUTE,
    NOTATION,
  }

  private companion object {
    val DECLARATION_START_REGEX =
      Regex("""^(def|theorem|inductive|structure|class|instance|axiom|abbrev|opaque|example)\b""")

    val NAMESPACE_START_REGEX = Regex("""^namespace(?:\s+(.+))?$""")
    val SECTION_START_REGEX = Regex("""^section(?:\s+(.+))?$""")
    val END_REGEX = Regex("""^end\b""")

    val NAME_REGEX = Regex("""^([A-Za-z_][A-Za-z0-9_'.]*)""")
  }
}
