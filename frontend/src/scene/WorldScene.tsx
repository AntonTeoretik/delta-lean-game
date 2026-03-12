import {
  Application,
  Container,
  FederatedPointerEvent,
  Graphics,
  Point,
  Text,
  TextStyle,
} from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { WorldNode } from '../model/types'
import { createCameraController } from './camera'
import { createGrid } from './grid'

interface WorldSceneProps {
  nodes: WorldNode[]
  selectedItemId: string | null
  onSelectItem: (id: string) => void
  onMoveNode: (id: string, x: number, y: number) => void
}

const NODE_WIDTH = 230
const NODE_HEIGHT = 96

const statusColors = {
  UNKNOWN: 0x2e3848,
  OK: 0x1f5f3d,
  WARNING: 0x8a6d1a,
  ERROR: 0x8f2c2c,
}

const textStyle = new TextStyle({
  fontFamily: 'monospace',
  fontSize: 13,
  fill: 0xf5f7ff,
  wordWrap: true,
  wordWrapWidth: NODE_WIDTH - 18,
})

function truncateLabel(title: string, filePath: string): string {
  const base = `${title} (${filePath})`
  if (base.length <= 36) {
    return base
  }
  return `${base.slice(0, 16)}...${base.slice(-17)}`
}

export function WorldScene({ nodes, selectedItemId, onSelectItem, onMoveNode }: WorldSceneProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const appRef = useRef<Application | null>(null)
  const worldRef = useRef<Container | null>(null)
  const nodesRef = useRef<WorldNode[]>([])

  useEffect(() => {
    nodesRef.current = nodes
  }, [nodes])

  useEffect(() => {
    let isMounted = true

    const initPixi = async () => {
      const host = hostRef.current
      if (!host) {
        return
      }

      const app = new Application()
      await app.init({
        background: '#0d121a',
        antialias: true,
        resizeTo: host,
        resolution: window.devicePixelRatio || 1,
        autoDensity: true,
      })

      if (!isMounted) {
        app.destroy(true)
        return
      }

      host.appendChild(app.canvas)
      host.style.cursor = 'default'

      const world = new Container()
      world.addChild(createGrid())
      app.stage.addChild(world)

      const camera = createCameraController(world)
      camera.currentX = app.renderer.width * 0.5
      camera.currentY = app.renderer.height * 0.5
      camera.targetX = camera.currentX
      camera.targetY = camera.currentY
      camera.update()

      let isPanning = false
      let lastPointer = new Point(0, 0)

      const toLocalCanvasPoint = (clientX: number, clientY: number) => {
        const rect = app.canvas.getBoundingClientRect()
        return {
          x: clientX - rect.left,
          y: clientY - rect.top,
        }
      }

      const isPointOnAnyNode = (clientX: number, clientY: number): boolean => {
        const canvasPoint = toLocalCanvasPoint(clientX, clientY)
        const worldPoint = world.toLocal(new Point(canvasPoint.x, canvasPoint.y))
        return nodesRef.current.some((node) => {
          return (
            worldPoint.x >= node.x &&
            worldPoint.x <= node.x + NODE_WIDTH &&
            worldPoint.y >= node.y &&
            worldPoint.y <= node.y + NODE_HEIGHT
          )
        })
      }

      const onPanStart = (event: PointerEvent) => {
        if (event.button !== 0 || isPointOnAnyNode(event.clientX, event.clientY)) {
          return
        }

        isPanning = true
        lastPointer.set(event.clientX, event.clientY)
      }

      const onPanMove = (event: PointerEvent) => {
        if (!isPanning) {
          return
        }

        const dx = event.clientX - lastPointer.x
        const dy = event.clientY - lastPointer.y
        lastPointer.set(event.clientX, event.clientY)
        camera.panBy(dx, dy)
      }

      const onPanStop = () => {
        isPanning = false
      }

      const onWheel = (event: WheelEvent) => {
        event.preventDefault()
        const point = toLocalCanvasPoint(event.clientX, event.clientY)
        camera.zoomAt(point.x, point.y, event.deltaY)
      }

      app.canvas.addEventListener('pointerdown', onPanStart)
      window.addEventListener('pointermove', onPanMove)
      window.addEventListener('pointerup', onPanStop)
      window.addEventListener('pointercancel', onPanStop)
      app.canvas.addEventListener('wheel', onWheel, { passive: false })

      app.ticker.add(() => camera.update())

      appRef.current = app
      worldRef.current = world

      return () => {
        app.canvas.removeEventListener('pointerdown', onPanStart)
        window.removeEventListener('pointermove', onPanMove)
        window.removeEventListener('pointerup', onPanStop)
        window.removeEventListener('pointercancel', onPanStop)
        app.canvas.removeEventListener('wheel', onWheel)
      }
    }

    let cleanupInteraction: (() => void) | undefined
    void initPixi().then((cleanup) => {
      cleanupInteraction = cleanup
    })

    return () => {
      isMounted = false
      cleanupInteraction?.()
      const app = appRef.current
      appRef.current = null
      worldRef.current = null
      if (app) {
        app.destroy(true)
      }
    }
  }, [])

  useEffect(() => {
    const app = appRef.current
    const world = worldRef.current
    if (!app || !world) {
      return
    }

    world.removeChildren()
    world.addChild(createGrid())

    const cleanupCallbacks: Array<() => void> = []

    nodes.forEach((node) => {
      const container = new Container()
      container.x = node.x
      container.y = node.y
      container.eventMode = 'static'
      container.cursor = 'default'

      const background = new Graphics()
      background.beginFill(statusColors[node.status])
      const selected = node.id === selectedItemId
      background.lineStyle(selected ? 3 : 1, selected ? 0xe7f0ff : 0x4c596d)
      background.drawRoundedRect(0, 0, NODE_WIDTH, NODE_HEIGHT, 12)
      background.endFill()
      container.addChild(background)

      const label = new Text(truncateLabel(node.title, node.filePath), textStyle)
      label.resolution = window.devicePixelRatio || 1
      label.x = 10
      label.y = 10
      container.addChild(label)

      let dragging = false
      let moved = false
      let dragOffsetX = 0
      let dragOffsetY = 0
      let pendingX = container.x
      let pendingY = container.y

      const toWorldFromClient = (clientX: number, clientY: number) => {
        const rect = app.canvas.getBoundingClientRect()
        const canvasPoint = new Point(clientX - rect.left, clientY - rect.top)
        return world.toLocal(canvasPoint)
      }

      const onWindowPointerMove = (event: PointerEvent) => {
        if (!dragging) {
          return
        }

        const local = toWorldFromClient(event.clientX, event.clientY)
        const nextX = local.x - dragOffsetX
        const nextY = local.y - dragOffsetY
        if (Math.abs(nextX - container.x) > 1 || Math.abs(nextY - container.y) > 1) {
          moved = true
        }
        container.x = nextX
        container.y = nextY
        pendingX = nextX
        pendingY = nextY
      }

      const finishDrag = () => {
        if (dragging && moved) {
          onMoveNode(node.id, pendingX, pendingY)
        }
        dragging = false
        window.removeEventListener('pointermove', onWindowPointerMove)
        window.removeEventListener('pointerup', finishDrag)
        window.removeEventListener('pointercancel', finishDrag)
      }

      container.on('pointerdown', (event: FederatedPointerEvent) => {
        event.stopPropagation()
        dragging = true
        moved = false
        const local = world.toLocal(event.global)
        dragOffsetX = local.x - container.x
        dragOffsetY = local.y - container.y
        window.addEventListener('pointermove', onWindowPointerMove)
        window.addEventListener('pointerup', finishDrag)
        window.addEventListener('pointercancel', finishDrag)
      })

      container.on('pointertap', () => {
        if (!moved) {
          onSelectItem(node.id)
        }
      })

      world.addChild(container)

      cleanupCallbacks.push(() => {
        window.removeEventListener('pointermove', onWindowPointerMove)
        window.removeEventListener('pointerup', finishDrag)
        window.removeEventListener('pointercancel', finishDrag)
      })
    })

    return () => {
      cleanupCallbacks.forEach((cleanup) => cleanup())
    }
  }, [nodes, onMoveNode, onSelectItem, selectedItemId])

  return <div className="world-scene" ref={hostRef} />
}
