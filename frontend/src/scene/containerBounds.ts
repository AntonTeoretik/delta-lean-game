import type { WorldContainerDto, WorldNode } from '../model/types'

export interface Rect {
  x: number
  y: number
  width: number
  height: number
}

export interface PackedLayout {
  containerRects: Map<string, Rect>
  nodeWorldPositions: Map<string, { x: number; y: number }>
}

const HEADER_HEIGHT = 26
const PADDING = 10
const GAP = 10
const ROOT_START_X = 40
const ROOT_START_Y = 40
const ROOT_GAP_Y = 22

interface Entry {
  id: string
  type: 'container' | 'node'
  width: number
  height: number
  order: number
}

interface ContainerMeasure {
  width: number
  height: number
  entries: Array<Entry & { x: number; y: number }>
}

export function getContainerBodyRect(containerRect: Rect): Rect {
  return {
    x: containerRect.x + PADDING,
    y: containerRect.y + HEADER_HEIGHT + PADDING,
    width: Math.max(40, containerRect.width - PADDING * 2),
    height: Math.max(40, containerRect.height - HEADER_HEIGHT - PADDING * 2),
  }
}

export function buildPackedLayout(containers: WorldContainerDto[], nodes: WorldNode[]): PackedLayout {
  const nodeById = new Map(nodes.map((node) => [node.id, node]))

  const containerOrder = new Map(containers.map((container, index) => [container.id, index]))
  const nodeOrder = new Map(nodes.map((node, index) => [node.id, index]))

  const childContainers = new Map<string, string[]>()
  containers.forEach((container) => {
    if (!container.parentContainerId) {
      return
    }
    const list = childContainers.get(container.parentContainerId) ?? []
    list.push(container.id)
    childContainers.set(container.parentContainerId, list)
  })

  const childNodes = new Map<string, string[]>()
  nodes.forEach((node) => {
    const parentId = node.parentContainerId
    if (!parentId) {
      return
    }
    const list = childNodes.get(parentId) ?? []
    list.push(node.id)
    childNodes.set(parentId, list)
  })

  const measured = new Map<string, ContainerMeasure>()

  const measureContainer = (containerId: string): ContainerMeasure => {
    const cached = measured.get(containerId)
    if (cached) {
      return cached
    }

    const childContainerIds = [...(childContainers.get(containerId) ?? [])].sort(
      (a, b) => (containerOrder.get(a) ?? 0) - (containerOrder.get(b) ?? 0),
    )
    const childNodeIds = [...(childNodes.get(containerId) ?? [])].sort(
      (a, b) => (nodeOrder.get(a) ?? 0) - (nodeOrder.get(b) ?? 0),
    )

    const entries: Entry[] = []
    childContainerIds.forEach((childId) => {
      const childSize = measureContainer(childId)
      entries.push({
        id: childId,
        type: 'container',
        width: childSize.width,
        height: childSize.height,
        order: containerOrder.get(childId) ?? 0,
      })
    })
    childNodeIds.forEach((nodeId) => {
      const node = nodeById.get(nodeId)
      if (!node) {
        return
      }
      entries.push({
        id: nodeId,
        type: 'node',
        width: node.width,
        height: node.height,
        order: nodeOrder.get(nodeId) ?? 0,
      })
    })

    entries.sort((a, b) => a.order - b.order)

    const maxChildWidth = entries.reduce((acc, entry) => Math.max(acc, entry.width), 220)
    const contentHeight = entries.reduce(
      (acc, entry, index) => acc + entry.height + (index > 0 ? GAP : 0),
      0,
    )

    let cursorY = HEADER_HEIGHT + PADDING
    const positionedEntries = entries.map((entry, index) => {
      const result = {
        ...entry,
        x: PADDING,
        y: cursorY,
      }
      cursorY += entry.height + (index < entries.length - 1 ? GAP : 0)
      return result
    })

    const size: ContainerMeasure = {
      width: maxChildWidth + PADDING * 2,
      height: HEADER_HEIGHT + PADDING * 2 + contentHeight,
      entries: positionedEntries,
    }
    measured.set(containerId, size)
    return size
  }

  const containerRects = new Map<string, Rect>()
  const nodeWorldPositions = new Map<string, { x: number; y: number }>()

  const placeContainer = (containerId: string, baseX: number, baseY: number) => {
    const size = measureContainer(containerId)
    containerRects.set(containerId, {
      x: baseX,
      y: baseY,
      width: size.width,
      height: size.height,
    })

    size.entries.forEach((entry) => {
      const worldX = baseX + entry.x
      const worldY = baseY + entry.y
      if (entry.type === 'container') {
        placeContainer(entry.id, worldX, worldY)
      } else {
        nodeWorldPositions.set(entry.id, { x: worldX, y: worldY })
      }
    })
  }

  const rootContainers = containers
    .filter((container) => container.parentContainerId == null)
    .sort((a, b) => a.filePath.localeCompare(b.filePath))

  let rootY = ROOT_START_Y
  rootContainers.forEach((root) => {
    const size = measureContainer(root.id)
    placeContainer(root.id, ROOT_START_X, rootY)
    rootY += size.height + ROOT_GAP_Y
  })

  return {
    containerRects,
    nodeWorldPositions,
  }
}
