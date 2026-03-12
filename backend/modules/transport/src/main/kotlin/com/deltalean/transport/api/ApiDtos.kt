package com.deltalean.transport.api

import kotlinx.serialization.Serializable

@Serializable
data class OpenWorkspaceRequest(
  val rootPath: String
)

@Serializable
data class OpenWorkspaceResponse(
  val success: Boolean,
  val fileCount: Int
)

@Serializable
data class FilesListResponse(
  val files: List<String>
)

@Serializable
data class FileContentResponse(
  val path: String,
  val content: String
)

@Serializable
data class FileUpdateRequest(
  val content: String
)

@Serializable
data class UpdateItemCodeRequest(
  val code: String
)

@Serializable
data class DiagnosticsResponse(
  val files: List<FileDiagnosticsDto>
)

@Serializable
data class FileDiagnosticsDto(
  val path: String,
  val diagnostics: List<DiagnosticDto>
)

@Serializable
data class DiagnosticDto(
  val severity: Int? = null,
  val message: String,
  val range: RangeDto
)

@Serializable
data class RangeDto(
  val start: PositionDto,
  val end: PositionDto
)

@Serializable
data class PositionDto(
  val line: Int,
  val character: Int
)

@Serializable
data class ErrorResponse(
  val error: String
)

@Serializable
data class WorldSnapshotResponse(
  val files: List<WorldFileResponse>
)

@Serializable
data class WorldFileResponse(
  val path: String,
  val imports: List<String>,
  val items: List<WorldItemResponse>
)

@Serializable
data class WorldItemResponse(
  val id: String,
  val filePath: String,
  val kind: String,
  val name: String?,
  val title: String,
  val code: String,
  val status: String,
  val range: RangeResponse?,
  val diagnostics: List<WorldDiagnosticResponse>,
  val layout: ItemLayoutResponse?
)

@Serializable
data class RangeResponse(
  val start: PositionResponse,
  val end: PositionResponse
)

@Serializable
data class PositionResponse(
  val line: Int,
  val character: Int
)

@Serializable
data class WorldDiagnosticResponse(
  val severity: String?,
  val message: String,
  val range: RangeResponse
)

@Serializable
data class ItemLayoutResponse(
  val x: Double,
  val y: Double
)
