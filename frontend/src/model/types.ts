export type NodeStatus = 'neutral' | 'warning' | 'error'

export interface FileNode {
  path: string
  x: number
  y: number
  status: NodeStatus
}

export interface FileDocument {
  path: string
  content: string
}

export interface Position {
  line: number
  character: number
}

export interface Range {
  start: Position
  end: Position
}

export interface DiagnosticItem {
  severity: number | null
  message: string
  range: Range
}

export interface FileDiagnostics {
  path: string
  diagnostics: DiagnosticItem[]
}
