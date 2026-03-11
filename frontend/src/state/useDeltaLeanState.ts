import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { backendClient } from '../api/backendClient'
import type { DiagnosticItem, FileDiagnostics, NodeStatus } from '../model/types'

interface NodePosition {
  x: number
  y: number
}

interface DeltaLeanState {
  workspaceRoot: string
  files: string[]
  activeFilePath: string | null
  activeFileStatus: NodeStatus
  activeNodePosition: NodePosition
  isNodeOpened: boolean
  editorContent: string
  activeDiagnostics: DiagnosticItem[]
  diagnosticsMap: Record<string, DiagnosticItem[]>
  isOpeningWorkspace: boolean
  isLoadingFile: boolean
  isSaving: boolean
  isDirty: boolean
  error: string | null
}

interface DeltaLeanActions {
  setWorkspaceRoot: (root: string) => void
  setWorkspaceRootFromDirectorySelection: (files: FileList | null) => void
  openWorkspace: () => Promise<void>
  selectFile: (path: string) => void
  openActiveNode: () => Promise<void>
  moveActiveNode: (x: number, y: number) => void
  setEditorContent: (value: string) => void
  saveSelectedFile: () => Promise<void>
  clearError: () => void
}

const AUTO_SAVE_DELAY_MS = 700
const DIAGNOSTICS_POLL_MS = 1500
const DEFAULT_NODE_POSITION: NodePosition = { x: 0, y: 0 }

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
  const [files, setFiles] = useState<string[]>([])
  const [activeFilePath, setActiveFilePath] = useState<string | null>(null)
  const [nodePositions, setNodePositions] = useState<Record<string, NodePosition>>({})
  const [isNodeOpened, setIsNodeOpened] = useState(false)
  const [editorContent, setEditorContent] = useState('')
  const [lastSavedContent, setLastSavedContent] = useState('')
  const [diagnosticsMap, setDiagnosticsMap] = useState<Record<string, DiagnosticItem[]>>({})
  const [isOpeningWorkspace, setIsOpeningWorkspace] = useState(false)
  const [isLoadingFile, setIsLoadingFile] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const activeFilePathRef = useRef<string | null>(null)
  const editorContentRef = useRef('')
  const isNodeOpenedRef = useRef(false)
  const isSavingRef = useRef(false)

  useEffect(() => {
    activeFilePathRef.current = activeFilePath
  }, [activeFilePath])

  useEffect(() => {
    editorContentRef.current = editorContent
  }, [editorContent])

  useEffect(() => {
    isNodeOpenedRef.current = isNodeOpened
  }, [isNodeOpened])

  useEffect(() => {
    isSavingRef.current = isSaving
  }, [isSaving])

  const activeDiagnostics = useMemo(
    () => (activeFilePath ? diagnosticsMap[activeFilePath] ?? [] : []),
    [diagnosticsMap, activeFilePath],
  )

  const activeFileStatus = useMemo(
    () => statusFromDiagnostics(activeDiagnostics),
    [activeDiagnostics],
  )

  const activeNodePosition = useMemo(() => {
    if (!activeFilePath) {
      return DEFAULT_NODE_POSITION
    }
    return nodePositions[activeFilePath] ?? DEFAULT_NODE_POSITION
  }, [activeFilePath, nodePositions])

  const isDirty = activeFilePath !== null && isNodeOpened && editorContent !== lastSavedContent

  const clearError = () => setError(null)

  const setWorkspaceRootFromDirectorySelection = (selectedFiles: FileList | null) => {
    if (!selectedFiles || selectedFiles.length === 0) {
      return
    }

    const first = selectedFiles[0] as File & { path?: string }
    const nativePath = first.path
    if (!nativePath) {
      setError('Браузер не дает абсолютный путь. Укажите workspace вручную.')
      return
    }

    const relativePart = first.webkitRelativePath
    const root = relativePart
      ? nativePath.slice(0, Math.max(0, nativePath.length - relativePart.length)).replace(/\\$/, '')
      : nativePath

    setWorkspaceRoot(root.replace(/\\/g, '/'))
  }

  const refreshDiagnostics = useCallback(async (showError: boolean) => {
    try {
      const diagnostics = await backendClient.getDiagnostics()
      setDiagnosticsMap(mapDiagnostics(diagnostics))
    } catch (e) {
      if (showError) {
        setError(e instanceof Error ? e.message : 'Не удалось загрузить diagnostics.')
      }
    }
  }, [])

  const persistActiveFile = useCallback(async () => {
    const path = activeFilePathRef.current
    const content = editorContentRef.current
    if (!path || !isNodeOpenedRef.current || isSavingRef.current) {
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      await backendClient.updateFile(path, content)
      if (activeFilePathRef.current === path) {
        setLastSavedContent(content)
      }
      await refreshDiagnostics(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось сохранить файл.')
    } finally {
      setIsSaving(false)
    }
  }, [refreshDiagnostics])

  const openWorkspace = async () => {
    if (!workspaceRoot.trim()) {
      setError('Введите путь до workspace.')
      return
    }

    setIsOpeningWorkspace(true)
    setError(null)

    try {
      await backendClient.openWorkspace(workspaceRoot.trim())
      const paths = await backendClient.listFiles()
      await refreshDiagnostics(true)
      setFiles(paths)
      setActiveFilePath(paths[0] ?? null)
      setNodePositions((previous) => {
        const next: Record<string, NodePosition> = {}
        paths.forEach((path, index) => {
          next[path] = previous[path] ?? {
            x: (index % 3) * 280 - 280,
            y: Math.floor(index / 3) * 170 - 120,
          }
        })
        return next
      })
      setIsNodeOpened(false)
      setEditorContent('')
      setLastSavedContent('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось открыть workspace.')
    } finally {
      setIsOpeningWorkspace(false)
    }
  }

  const selectFile = useCallback((path: string) => {
    setActiveFilePath(path)
    setIsNodeOpened(false)
    setEditorContent('')
    setLastSavedContent('')
  }, [])

  const openActiveNode = useCallback(async () => {
    if (!activeFilePath) {
      return
    }

    setIsLoadingFile(true)
    setError(null)
    try {
      const file = await backendClient.getFile(activeFilePath)
      setEditorContent(file.content)
      setLastSavedContent(file.content)
      setIsNodeOpened(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось загрузить файл.')
    } finally {
      setIsLoadingFile(false)
    }
  }, [activeFilePath])

  const moveActiveNode = useCallback((x: number, y: number) => {
    const path = activeFilePathRef.current
    if (!path) {
      return
    }

    setNodePositions((previous) => ({
      ...previous,
      [path]: { x, y },
    }))
  }, [])

  const saveSelectedFile = useCallback(async () => {
    await persistActiveFile()
  }, [persistActiveFile])

  useEffect(() => {
    if (!isDirty || !isNodeOpened || !activeFilePath) {
      return
    }

    const timer = window.setTimeout(() => {
      void persistActiveFile()
    }, AUTO_SAVE_DELAY_MS)

    return () => window.clearTimeout(timer)
  }, [activeFilePath, isDirty, isNodeOpened, editorContent, persistActiveFile])

  useEffect(() => {
    if (files.length === 0) {
      return
    }

    const interval = window.setInterval(() => {
      void refreshDiagnostics(false)
    }, DIAGNOSTICS_POLL_MS)

    return () => window.clearInterval(interval)
  }, [files.length, refreshDiagnostics])

  return {
    workspaceRoot,
    files,
    activeFilePath,
    activeFileStatus,
    activeNodePosition,
    isNodeOpened,
    editorContent,
    activeDiagnostics,
    diagnosticsMap,
    isOpeningWorkspace,
    isLoadingFile,
    isSaving,
    isDirty,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectFile,
    openActiveNode,
    moveActiveNode,
    setEditorContent,
    saveSelectedFile,
    clearError,
  }
}
