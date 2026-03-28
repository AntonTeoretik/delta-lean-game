import { Application, Container, Graphics, Point, Text, TextStyle } from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { WorldContainerDto, WorldNode } from '../model/types'
import {
  buildPackedLayout,
  getContainerBodyRect,
} from './containerBounds'
import { createCameraController } from './camera'
import { createGrid } from './grid'

interface WorldSceneProps {
  nodes: WorldNode[]
  containers: WorldContainerDto[]
  selectedItemId: string | null
  onSelectItem: (id: string) => void
}

const NODE_WIDTH = 220
const NODE_HEIGHT = 90

const nodeStatusColors = {
  UNKNOWN: 0x2e3848,
  OK: 0x1f5f3d,
  WARNING: 0x8a6d1a,
  ERROR: 0x8f2c2c,
}

const containerColors = {
  FILE: { fill: 0x1a2638, line: 0x3a4d6a },
  NAMESPACE: { fill: 0x253747, line: 0x5f87a8 },
  SECTION: { fill: 0x3f3a2b, line: 0x9b8a59 },
}

const titleStyle = new TextStyle({
  fontFamily: 'monospace',
  fontSize: 12,
  fill: 0xf0f5ff,
})

const nodeStyle = new TextStyle({
  fontFamily: 'monospace',
  fontSize: 12,
  fill: 0xf5f7ff,
  wordWrap: true,
  wordWrapWidth: NODE_WIDTH - 14,
})

function truncate(text: string, max = 36): string {
  if (text.length <= max) {
    return text
  }
  return `${text.slice(0, max - 3)}...`
}

export function WorldScene({ nodes, containers, selectedItemId, onSelectItem }: WorldSceneProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const appRef = useRef<Application | null>(null)
  const worldRef = useRef<Container | null>(null)
  const nodesRef = useRef<WorldNode[]>([])
  const containersRef = useRef<WorldContainerDto[]>([])

  useEffect(() => {
    nodesRef.current = nodes
  }, [nodes])

  useEffect(() => {
    containersRef.current = containers
  }, [containers])

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

      const onPanStart = (event: PointerEvent) => {
        if (event.button !== 0) {
          return
        }

        const rect = app.canvas.getBoundingClientRect()
        const canvasPoint = new Point(event.clientX - rect.left, event.clientY - rect.top)
        const worldPoint = world.toLocal(canvasPoint)
        const packed = buildPackedLayout(containersRef.current, nodesRef.current)
        const onNode = nodesRef.current.some((node) => {
          const nodePos = packed.nodeWorldPositions.get(node.id)
          const nodeWorldX = nodePos?.x ?? node.x
          const nodeWorldY = nodePos?.y ?? node.y
          return (
            worldPoint.x >= nodeWorldX &&
            worldPoint.x <= nodeWorldX + NODE_WIDTH &&
            worldPoint.y >= nodeWorldY &&
            worldPoint.y <= nodeWorldY + NODE_HEIGHT
          )
        })

        if (onNode) {
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

    const packed = buildPackedLayout(containers, nodes)
    const containerRects = packed.containerRects
    const nodeWorldPositions = packed.nodeWorldPositions
    const sortedContainers = [...containers].sort((a, b) => {
      const depthA = (a.parentContainerId?.split('#').length ?? 0)
      const depthB = (b.parentContainerId?.split('#').length ?? 0)
      if (depthA != depthB) {
        return depthA - depthB
      }
      return a.id.localeCompare(b.id)
    })

    sortedContainers.forEach((containerModel) => {
      const rect = containerRects.get(containerModel.id)
      if (!rect) {
        return
      }
      const colors = containerColors[containerModel.kind]

      const g = new Graphics()
      g.beginFill(colors.fill, containerModel.kind === 'FILE' ? 0.08 : 0.18)
      g.lineStyle(2, colors.line, containerModel.kind === 'FILE' ? 0.3 : 0.7)
      g.drawRoundedRect(rect.x, rect.y, rect.width, rect.height, 12)
      g.endFill()

      const body = getContainerBodyRect(rect)
      g.beginFill(0x0c131f, 0.28)
      g.drawRoundedRect(body.x, body.y, body.width, body.height, 8)
      g.endFill()

      const title = new Text(containerModel.title, titleStyle)
      title.resolution = window.devicePixelRatio || 1
      title.x = rect.x + 8
      title.y = rect.y + 6

      world.addChild(g)
      world.addChild(title)
    })

    nodes.forEach((node) => {
      const container = new Container()
      const position = nodeWorldPositions.get(node.id) ?? { x: node.x, y: node.y }
      container.x = position.x
      container.y = position.y
      container.eventMode = 'static'
      container.cursor = 'default'

      const background = new Graphics()
      background.beginFill(nodeStatusColors[node.status])
      const selected = node.id === selectedItemId
      background.lineStyle(selected ? 3 : 1, selected ? 0xe7f0ff : 0x4c596d)
      background.drawRoundedRect(0, 0, NODE_WIDTH, NODE_HEIGHT, 10)
      background.endFill()
      container.addChild(background)

      const label = new Text(truncate(`${node.title} (${node.filePath})`), nodeStyle)
      label.resolution = window.devicePixelRatio || 1
      label.x = 8
      label.y = 8
      container.addChild(label)

      container.on('pointertap', () => {
        onSelectItem(node.id)
      })

      world.addChild(container)
    })
  }, [containers, nodes, onSelectItem, selectedItemId])

  return <div className="world-scene" ref={hostRef} />
}
