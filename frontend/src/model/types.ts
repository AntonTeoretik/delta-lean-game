export type NodeStatus = 'UNKNOWN' | 'OK' | 'WARNING' | 'ERROR'

export interface PositionDto {
  line: number
  character: number
}

export interface RangeDto {
  start: PositionDto
  end: PositionDto
}

export interface DiagnosticDto {
  severity: string | null
  message: string
  range: RangeDto
}

export interface ItemLayoutDto {
  x: number
  y: number
}

export interface WorldItemDto {
  id: string
  filePath: string
  kind: string
  name: string | null
  title: string
  code: string
  status: NodeStatus
  range: RangeDto | null
  diagnostics: DiagnosticDto[]
  layout: ItemLayoutDto | null
}

export interface WorldFileDto {
  path: string
  imports: string[]
  items: WorldItemDto[]
}

export interface WorldSnapshotDto {
  files: WorldFileDto[]
}

export interface WorldNode {
  id: string
  title: string
  filePath: string
  status: NodeStatus
  x: number
  y: number
}
