package com.deltalean.transport.api

import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemLayout
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.WorldSnapshot

fun WorldSnapshot.toResponse(): WorldSnapshotResponse = WorldSnapshotResponse(
  files = files.map { it.toResponse() }
)

private fun WorldFile.toResponse(): WorldFileResponse = WorldFileResponse(
  path = path,
  imports = imports,
  items = items.map { it.toResponse() }
)

private fun WorldItem.toResponse(): WorldItemResponse = WorldItemResponse(
  id = id,
  filePath = filePath,
  kind = kind.name,
  name = name,
  title = title,
  code = code,
  status = status.name,
  range = range?.toResponse(),
  diagnostics = diagnostics.map { it.toResponse() },
  layout = layout?.toResponse(),
)

private fun TextRange.toResponse(): RangeResponse = RangeResponse(
  start = start.toResponse(),
  end = end.toResponse(),
)

private fun TextPosition.toResponse(): PositionResponse = PositionResponse(
  line = line,
  character = character,
)

private fun Diagnostic.toResponse(): WorldDiagnosticResponse = WorldDiagnosticResponse(
  severity = severity,
  message = message,
  range = range.toResponse(),
)

private fun ItemLayout.toResponse(): ItemLayoutResponse = ItemLayoutResponse(
  x = x,
  y = y,
)
