package com.deltalean.lean.lsp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class InitializeParams(
  val processId: Int? = null,
  val rootUri: String? = null,
  val capabilities: JsonObject = buildJsonObject {}
)

data class InitializeResult(
  val capabilities: JsonObject = buildJsonObject {}
)

data class TextDocumentItem(
  val uri: String,
  val languageId: String,
  val version: Int,
  val text: String
)

data class DidOpenTextDocumentParams(
  val textDocument: TextDocumentItem
)

data class VersionedTextDocumentIdentifier(
  val uri: String,
  val version: Int
)

data class TextDocumentContentChangeEvent(
  val text: String
)

data class DidChangeTextDocumentParams(
  val textDocument: VersionedTextDocumentIdentifier,
  val contentChanges: List<TextDocumentContentChangeEvent>
)

data class PublishDiagnosticsParams(
  val uri: String,
  val version: Int? = null,
  val diagnostics: List<Diagnostic>
)

data class Diagnostic(
  val range: Range,
  val severity: Int? = null,
  val message: String
)

data class Range(
  val start: Position,
  val end: Position
)

data class Position(
  val line: Int,
  val character: Int
)

fun InitializeParams.toJson(): JsonObject = buildJsonObject {
  processId?.let { put("processId", JsonPrimitive(it)) }
  rootUri?.let { put("rootUri", JsonPrimitive(it)) }
  put("capabilities", capabilities)
}

fun DidOpenTextDocumentParams.toJson(): JsonObject = buildJsonObject {
  put("textDocument", buildJsonObject {
    put("uri", JsonPrimitive(textDocument.uri))
    put("languageId", JsonPrimitive(textDocument.languageId))
    put("version", JsonPrimitive(textDocument.version))
    put("text", JsonPrimitive(textDocument.text))
  })
}

fun DidChangeTextDocumentParams.toJson(): JsonObject = buildJsonObject {
  put("textDocument", buildJsonObject {
    put("uri", JsonPrimitive(textDocument.uri))
    put("version", JsonPrimitive(textDocument.version))
  })
  put(
    "contentChanges",
    JsonArray(contentChanges.map { change ->
      buildJsonObject {
        put("text", JsonPrimitive(change.text))
      }
    })
  )
}

fun parsePublishDiagnostics(params: JsonElement): PublishDiagnosticsParams {
  val obj = params.jsonObject
  val uri = obj["uri"]?.jsonPrimitive?.content
    ?: error("publishDiagnostics is missing 'uri'")
  val diagnostics = obj["diagnostics"]?.jsonArray?.map { diagElement ->
    val diag = diagElement.jsonObject
    val rangeObj = diag["range"]?.jsonObject
      ?: error("diagnostic is missing 'range'")
    val startObj = rangeObj["start"]?.jsonObject
      ?: error("range is missing 'start'")
    val endObj = rangeObj["end"]?.jsonObject
      ?: error("range is missing 'end'")

    Diagnostic(
      range = Range(
        start = Position(
          line = startObj["line"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: error("position is missing 'line'"),
          character = startObj["character"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: error("position is missing 'character'")
        ),
        end = Position(
          line = endObj["line"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: error("position is missing 'line'"),
          character = endObj["character"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: error("position is missing 'character'")
        )
      ),
      severity = diag["severity"]?.jsonPrimitive?.intOrNull,
      message = diag["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )
  } ?: emptyList()
  val version = obj["version"]?.jsonPrimitive?.intOrNull

  return PublishDiagnosticsParams(uri = uri, version = version, diagnostics = diagnostics)
}
