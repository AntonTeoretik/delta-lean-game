import { useMemo, useState } from 'react'
import { backendClient } from '../api/backendClient'
import type { DiagnosticDto, NodeStatus, WorldItemDto, WorldNode, WorldSnapshotDto } from '../model/types'

interface NodePosition {
  x: number
  y: number
}

interface DeltaLeanState {
  workspaceRoot: string
  world: WorldSnapshotDto | null
  nodes: WorldNode[]
  selectedItemId: string | null
  selectedItem: WorldItemDto | null
  editorContent: string
  isOpeningWorkspace: boolean
  isSaving: boolean
  isDirty: boolean
  error: string | null
}

interface DeltaLeanActions {
  setWorkspaceRoot: (root: string) => void
  setWorkspaceRootFromDirectorySelection: (files: FileList | null) => void
  openWorkspace: () => Promise<void>
  selectItem: (id: string) => void
  moveNode: (id: string, x: number, y: number) => void
  setEditorContent: (value: string) => void
  saveSelectedItem: () => Promise<void>
  clearError: () => void
}

const DEFAULT_GRID_COLS = 4

function flattenItems(world: WorldSnapshotDto | null): WorldItemDto[] {
  if (!world) {
    return []
  }
  return world.files.flatMap((file) => file.items)
}

function toNodePosition(item: WorldItemDto, index: number, positions: Record<string, NodePosition>): NodePosition {
  const remembered = positions[item.id]
  if (remembered) {
    return remembered
  }

  if (item.layout) {
    return { x: item.layout.x, y: item.layout.y }
  }

  return {
    x: (index % DEFAULT_GRID_COLS) * 280 - 280,
    y: Math.floor(index / DEFAULT_GRID_COLS) * 180 - 140,
  }
}

export function useDeltaLeanState(): DeltaLeanState & DeltaLeanActions {
  const [workspaceRoot, setWorkspaceRoot] = useState('')
  const [world, setWorld] = useState<WorldSnapshotDto | null>(null)
  const [positions, setPositions] = useState<Record<string, NodePosition>>({})
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [editorContent, setEditorContent] = useState('')
  const [lastSavedContent, setLastSavedContent] = useState('')
  const [isOpeningWorkspace, setIsOpeningWorkspace] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const items = useMemo(() => flattenItems(world), [world])

  const selectedItem = useMemo(
    () => items.find((item) => item.id == selectedItemId) ?? null,
    [items, selectedItemId],
  )

  const nodes = useMemo(
    () =>
      items.map((item, index) => {
        const position = toNodePosition(item, index, positions)
        return {
          id: item.id,
          title: item.title,
          filePath: item.filePath,
          status: item.status,
          x: position.x,
          y: position.y,
        }
      }),
    [items, positions],
  )

  const isDirty = selectedItem !== null && editorContent != lastSavedContent

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

  const loadWorld = async (keepSelectionId: string | null = null) => {
    const nextWorld = await backendClient.getWorld()
    setWorld(nextWorld)

    const nextItems = flattenItems(nextWorld)
    const nextSelection =
      (keepSelectionId && nextItems.find((item) => item.id === keepSelectionId)?.id) ??
      nextItems[0]?.id ??
      null

    setSelectedItemId(nextSelection)
    if (nextSelection) {
      const item = nextItems.find((it) => it.id === nextSelection)
      setEditorContent(item?.code ?? '')
      setLastSavedContent(item?.code ?? '')
    } else {
      setEditorContent('')
      setLastSavedContent('')
    }
  }

  const openWorkspace = async () => {
    if (!workspaceRoot.trim()) {
      setError('Введите путь до workspace.')
      return
    }

    setIsOpeningWorkspace(true)
    setError(null)
    try {
      await backendClient.openWorkspace(workspaceRoot.trim())
      await loadWorld(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось открыть workspace.')
    } finally {
      setIsOpeningWorkspace(false)
    }
  }

  const selectItem = (id: string) => {
    setSelectedItemId(id)
    const item = items.find((it) => it.id === id)
    if (item) {
      setEditorContent(item.code)
      setLastSavedContent(item.code)
    }
  }

  const moveNode = (id: string, x: number, y: number) => {
    setPositions((previous) => ({
      ...previous,
      [id]: { x, y },
    }))
  }

  const saveSelectedItem = async () => {
    if (!selectedItemId) {
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      await backendClient.updateItemCode(selectedItemId, editorContent)
      await loadWorld(selectedItemId)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось сохранить item.')
    } finally {
      setIsSaving(false)
    }
  }

  return {
    workspaceRoot,
    world,
    nodes,
    selectedItemId,
    selectedItem,
    editorContent,
    isOpeningWorkspace,
    isSaving,
    isDirty,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectItem,
    moveNode,
    setEditorContent,
    saveSelectedItem,
    clearError,
  }
}

export function nodeStatusToUi(status: NodeStatus): 'neutral' | 'warning' | 'error' {
  if (status === 'ERROR') {
    return 'error'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  return 'neutral'
}

export function diagnosticsForItem(item: WorldItemDto | null): DiagnosticDto[] {
  return item?.diagnostics ?? []
}
