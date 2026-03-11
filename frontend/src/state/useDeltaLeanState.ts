import { useMemo, useState } from 'react'
import { backendClient } from '../api/backendClient'
import type { DiagnosticItem, FileDiagnostics, FileNode, NodeStatus } from '../model/types'

interface DeltaLeanState {
  workspaceRoot: string
  nodes: FileNode[]
  selectedPath: string | null
  editorContent: string
  selectedDiagnostics: DiagnosticItem[]
  diagnosticsMap: Record<string, DiagnosticItem[]>
  isOpeningWorkspace: boolean
  isLoadingFile: boolean
  isSaving: boolean
  isDirty: boolean
  error: string | null
}

interface DeltaLeanActions {
  setWorkspaceRoot: (root: string) => void
  openWorkspace: () => Promise<void>
  selectNode: (path: string) => Promise<void>
  moveNode: (path: string, x: number, y: number) => void
  setEditorContent: (value: string) => void
  saveSelectedFile: () => Promise<void>
  clearError: () => void
}

const NODE_WIDTH = 190
const NODE_HEIGHT = 88
const NODE_GAP_X = 24
const NODE_GAP_Y = 24
const COLUMNS = 4

function buildGridNodes(paths: string[], diagnosticsMap: Record<string, DiagnosticItem[]>): FileNode[] {
  return paths.map((path, index) => {
    const row = Math.floor(index / COLUMNS)
    const col = index % COLUMNS
    return {
      path,
      x: 24 + col * (NODE_WIDTH + NODE_GAP_X),
      y: 24 + row * (NODE_HEIGHT + NODE_GAP_Y),
      status: statusFromDiagnostics(diagnosticsMap[path] ?? []),
    }
  })
}

function mapDiagnostics(files: FileDiagnostics[]): Record<string, DiagnosticItem[]> {
  return files.reduce<Record<string, DiagnosticItem[]>>((acc, file) => {
    acc[file.path] = file.diagnostics
    return acc
  }, {})
}

function statusFromDiagnostics(diagnostics: DiagnosticItem[]): NodeStatus {
  if (diagnostics.length === 0) {
    return 'neutral'
  }

  if (diagnostics.some((item) => item.severity === null || item.severity === 1)) {
    return 'error'
  }

  if (diagnostics.some((item) => item.severity === 2)) {
    return 'warning'
  }

  return 'neutral'
}

export function useDeltaLeanState(): DeltaLeanState & DeltaLeanActions {
  const [workspaceRoot, setWorkspaceRoot] = useState('')
  const [nodes, setNodes] = useState<FileNode[]>([])
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [editorContent, setEditorContent] = useState('')
  const [lastSavedContent, setLastSavedContent] = useState('')
  const [diagnosticsMap, setDiagnosticsMap] = useState<Record<string, DiagnosticItem[]>>({})
  const [isOpeningWorkspace, setIsOpeningWorkspace] = useState(false)
  const [isLoadingFile, setIsLoadingFile] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectedDiagnostics = useMemo(
    () => (selectedPath ? diagnosticsMap[selectedPath] ?? [] : []),
    [diagnosticsMap, selectedPath],
  )

  const isDirty = selectedPath !== null && editorContent !== lastSavedContent

  const clearError = () => setError(null)

  const openWorkspace = async () => {
    if (!workspaceRoot.trim()) {
      setError('Введите путь до workspace.')
      return
    }

    setIsOpeningWorkspace(true)
    setError(null)

    try {
      await backendClient.openWorkspace(workspaceRoot.trim())
      const [paths, diagnostics] = await Promise.all([
        backendClient.listFiles(),
        backendClient.getDiagnostics(),
      ])

      const mappedDiagnostics = mapDiagnostics(diagnostics)
      setDiagnosticsMap(mappedDiagnostics)
      setNodes(buildGridNodes(paths, mappedDiagnostics))
      setSelectedPath(null)
      setEditorContent('')
      setLastSavedContent('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось открыть workspace.')
    } finally {
      setIsOpeningWorkspace(false)
    }
  }

  const selectNode = async (path: string) => {
    setSelectedPath(path)
    setIsLoadingFile(true)
    setError(null)
    try {
      const file = await backendClient.getFile(path)
      setEditorContent(file.content)
      setLastSavedContent(file.content)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось загрузить файл.')
    } finally {
      setIsLoadingFile(false)
    }
  }

  const moveNode = (path: string, x: number, y: number) => {
    setNodes((previous) =>
      previous.map((node) => (node.path === path ? { ...node, x, y } : node)),
    )
  }

  const saveSelectedFile = async () => {
    if (!selectedPath) {
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      await backendClient.updateFile(selectedPath, editorContent)
      setLastSavedContent(editorContent)

      const diagnostics = await backendClient.getDiagnostics()
      const mappedDiagnostics = mapDiagnostics(diagnostics)
      setDiagnosticsMap(mappedDiagnostics)
      setNodes((previous) =>
        previous.map((node) => ({
          ...node,
          status: statusFromDiagnostics(mappedDiagnostics[node.path] ?? []),
        })),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось сохранить файл.')
    } finally {
      setIsSaving(false)
    }
  }

  return {
    workspaceRoot,
    nodes,
    selectedPath,
    editorContent,
    selectedDiagnostics,
    diagnosticsMap,
    isOpeningWorkspace,
    isLoadingFile,
    isSaving,
    isDirty,
    error,
    setWorkspaceRoot,
    openWorkspace,
    selectNode,
    moveNode,
    setEditorContent,
    saveSelectedFile,
    clearError,
  }
}
