import { useMemo, useState } from 'react'
import { backendClient } from '../api/backendClient'
import type { DiagnosticDto, WorldContainerDto, WorldItemDto, WorldNode, WorldSnapshotDto } from '../model/types'

interface DeltaLeanState {
  workspaceRoot: string
  world: WorldSnapshotDto | null
  files: string[]
  containers: WorldContainerDto[]
  selectedFilePath: string | null
  selectedItemId: string | null
  selectedItem: WorldItemDto | null
  nodes: WorldNode[]
  isOpeningWorkspace: boolean
  error: string | null
}

interface DeltaLeanActions {
  setWorkspaceRoot: (root: string) => void
  setWorkspaceRootFromDirectorySelection: (files: FileList | null) => void
  openWorkspace: () => Promise<void>
  selectFile: (path: string) => void
  selectItem: (id: string) => void
  clearError: () => void
}

function flattenItems(world: WorldSnapshotDto | null): WorldItemDto[] {
  if (!world) {
    return []
  }
  return world.files.flatMap((file) => file.items)
}

export function useDeltaLeanState(): DeltaLeanState & DeltaLeanActions {
  const [workspaceRoot, setWorkspaceRoot] = useState('')
  const [world, setWorld] = useState<WorldSnapshotDto | null>(null)
  const [selectedFilePath, setSelectedFilePath] = useState<string | null>(null)
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [isOpeningWorkspace, setIsOpeningWorkspace] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const files = useMemo(() => world?.files.map((file) => file.path) ?? [], [world])
  const containers = useMemo(() => {
    if (!world || !selectedFilePath) {
      return []
    }
    return world.files.find((file) => file.path === selectedFilePath)?.containers ?? []
  }, [selectedFilePath, world])

  const visibleItems = useMemo(() => {
    if (!world || !selectedFilePath) {
      return []
    }
    return world.files.find((file) => file.path === selectedFilePath)?.items ?? []
  }, [selectedFilePath, world])

  const selectedItem = useMemo(() => {
    const allItems = flattenItems(world)
    return allItems.find((item) => item.id === selectedItemId) ?? null
  }, [selectedItemId, world])

  const nodes = useMemo<WorldNode[]>(
    () =>
      visibleItems.map((item, index) => ({
        id: item.id,
        title: item.title,
        filePath: item.filePath,
        parentContainerId: item.parentContainerId,
        status: item.status,
        x: 60,
        y: 70 + index * 130,
        width: 220,
        height: 90,
      })),
    [visibleItems],
  )

  const clearError = () => setError(null)

  const setWorkspaceRootFromDirectorySelection = (selectedFiles: FileList | null) => {
    if (!selectedFiles || selectedFiles.length === 0) {
      return
    }

    const first = selectedFiles[0] as File & { path?: string }
    const nativePath = first.path
    if (!nativePath) {
      setError('Browser does not expose absolute path. Please paste it manually.')
      return
    }

    const relativePart = first.webkitRelativePath
    const root = relativePart
      ? nativePath.slice(0, Math.max(0, nativePath.length - relativePart.length)).replace(/\\$/, '')
      : nativePath

    setWorkspaceRoot(root.replace(/\\/g, '/'))
  }

  const openWorkspace = async () => {
    if (!workspaceRoot.trim()) {
      setError('Enter workspace path first.')
      return
    }

    setIsOpeningWorkspace(true)
    setError(null)
    try {
      await backendClient.openWorkspace(workspaceRoot.trim())
      const snapshot = await backendClient.getWorld()
      setWorld(snapshot)
      const firstFile = snapshot.files[0]?.path ?? null
      setSelectedFilePath(firstFile)
      setSelectedItemId(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to open workspace.')
    } finally {
      setIsOpeningWorkspace(false)
    }
  }

  const selectFile = (path: string) => {
    setSelectedFilePath(path)
    setSelectedItemId(null)
  }

  const selectItem = (id: string) => {
    setSelectedItemId(id)
  }

  return {
    workspaceRoot,
    world,
    files,
    containers,
    selectedFilePath,
    selectedItemId,
    selectedItem,
    nodes,
    isOpeningWorkspace,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectFile,
    selectItem,
    clearError,
  }
}

export function diagnosticsForItem(item: WorldItemDto | null): DiagnosticDto[] {
  return item?.diagnostics ?? []
}
