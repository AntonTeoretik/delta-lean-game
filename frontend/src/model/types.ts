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
  width: number
  height: number
}

export interface ContainerLayoutDto {
  x: number
  y: number
  width: number
  height: number
}

export interface ContainerContextDto {
  variables: string[]
  opens: string[]
  openScoped: string[]
  universes: string[]
  options: string[]
  attributes: string[]
  notations: string[]
}

export interface WorldContainerDto {
  id: string
  kind: 'FILE' | 'NAMESPACE' | 'SECTION'
  title: string
  filePath: string
  parentContainerId: string | null
  layout: ContainerLayoutDto
  context: ContainerContextDto
}

export interface WorldItemDto {
  id: string
  filePath: string
  parentContainerId: string | null
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
  containers: WorldContainerDto[]
  items: WorldItemDto[]
}

export interface WorldSnapshotDto {
  files: WorldFileDto[]
}

export interface WorldNode {
  id: string
  title: string
  filePath: string
  parentContainerId: string | null
  status: NodeStatus
  x: number
  y: number
  width: number
  height: number
}
