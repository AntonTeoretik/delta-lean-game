package com.deltalean.transport.api

import com.deltalean.domain.world.ContainerContext
import com.deltalean.domain.world.ContainerLayout
import com.deltalean.domain.world.Diagnostic
import com.deltalean.domain.world.ItemLayout
import com.deltalean.domain.world.TextPosition
import com.deltalean.domain.world.TextRange
import com.deltalean.domain.world.WorldContainer
import com.deltalean.domain.world.WorldFile
import com.deltalean.domain.world.WorldItem
import com.deltalean.domain.world.WorldSnapshot

fun WorldSnapshot.toResponse(): WorldSnapshotResponse = WorldSnapshotResponse(
  files = files.map { it.toResponse() }
)

private fun WorldFile.toResponse(): WorldFileResponse = WorldFileResponse(
  path = path,
  imports = imports,
  containers = containers.map { it.toResponse() },
  items = items.map { it.toResponse() }
)

private fun WorldContainer.toResponse(): WorldContainerResponse = WorldContainerResponse(
  id = id,
  kind = kind.name,
  title = title,
  filePath = filePath,
  parentContainerId = parentContainerId,
  layout = layout.toResponse(),
  context = context.toResponse(),
)

private fun ContainerLayout.toResponse(): ContainerLayoutResponse = ContainerLayoutResponse(
  x = x,
  y = y,
  width = width,
  height = height,
)

private fun ContainerContext.toResponse(): ContainerContextResponse = ContainerContextResponse(
  variables = variables,
  opens = opens,
  openScoped = openScoped,
  universes = universes,
  options = options,
  attributes = attributes,
  notations = notations,
)

private fun WorldItem.toResponse(): WorldItemResponse = WorldItemResponse(
  id = id,
  filePath = filePath,
  parentContainerId = parentContainerId,
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
  width = width,
  height = height,
)
