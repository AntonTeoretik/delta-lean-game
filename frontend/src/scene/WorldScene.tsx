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
import type { NodeStatus } from '../model/types'
import { createCameraController } from './camera'
import { createGrid } from './grid'

interface WorldSceneProps {
  activePath: string | null
  status: NodeStatus
  nodePosition: { x: number; y: number }
  isOpened: boolean
  onOpenNode: () => void
  onMoveNode: (x: number, y: number) => void
}

const NODE_WIDTH = 230
const NODE_HEIGHT = 96

const statusColors = {
  neutral: 0x2e3848,
  warning: 0x8a6d1a,
  error: 0x8f2c2c,
}

const textStyle = new TextStyle({
  fontFamily: 'monospace',
  fontSize: 13,
  fill: 0xf5f7ff,
  wordWrap: true,
  wordWrapWidth: NODE_WIDTH - 18,
})

function truncatePath(path: string): string {
  if (path.length <= 34) {
    return path
  }
  return `${path.slice(0, 14)}...${path.slice(-17)}`
}

export function WorldScene({
  activePath,
  status,
  nodePosition,
  isOpened,
  onOpenNode,
  onMoveNode,
}: WorldSceneProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const appRef = useRef<Application | null>(null)
  const worldRef = useRef<Container | null>(null)
  const nodeRef = useRef<Container | null>(null)

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

        const node = nodeRef.current
        if (node) {
          const canvasPoint = toLocalCanvasPoint(event.clientX, event.clientY)
          const worldPoint = world.toLocal(new Point(canvasPoint.x, canvasPoint.y))
          const isOnNode =
            worldPoint.x >= node.x &&
            worldPoint.x <= node.x + NODE_WIDTH &&
            worldPoint.y >= node.y &&
            worldPoint.y <= node.y + NODE_HEIGHT

          if (isOnNode) {
            return
          }
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

      app.ticker.add(() => {
        camera.update()
      })

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
      nodeRef.current = null
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

    const existingNode = nodeRef.current
    if (existingNode) {
      world.removeChild(existingNode)
      existingNode.destroy({ children: true })
      nodeRef.current = null
    }

    if (!activePath) {
      return
    }

    const container = new Container()
    container.x = nodePosition.x
    container.y = nodePosition.y
    container.eventMode = 'static'
    container.cursor = 'default'

    const background = new Graphics()
    background.beginFill(statusColors[status])
    background.lineStyle(isOpened ? 3 : 1, isOpened ? 0xe7f0ff : 0x4c596d)
    background.drawRoundedRect(0, 0, NODE_WIDTH, NODE_HEIGHT, 12)
    background.endFill()
    container.addChild(background)

    const label = new Text(truncatePath(activePath), textStyle)
    label.x = 10
    label.y = 10
    container.addChild(label)

    const hint = new Text(isOpened ? 'opened | drag me' : 'click to open | drag me', {
      ...textStyle,
      fontSize: 11,
      fill: 0xbfd5f4,
      wordWrap: false,
    })
    hint.x = 10
    hint.y = NODE_HEIGHT - 22
    container.addChild(hint)

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

    const finishDrag = () => {
      if (dragging && moved) {
        onMoveNode(pendingX, pendingY)
      }
      dragging = false
      window.removeEventListener('pointermove', onWindowPointerMove)
      window.removeEventListener('pointerup', finishDrag)
      window.removeEventListener('pointercancel', finishDrag)
    }
    container.on('pointerup', finishDrag)
    container.on('pointerupoutside', finishDrag)

    container.on('pointertap', () => {
      if (!moved) {
        onOpenNode()
      }
    })

    world.addChild(container)
    nodeRef.current = container

    return () => {
      window.removeEventListener('pointermove', onWindowPointerMove)
      window.removeEventListener('pointerup', finishDrag)
      window.removeEventListener('pointercancel', finishDrag)
    }
  }, [activePath, isOpened, nodePosition.x, nodePosition.y, onMoveNode, onOpenNode, status])

  return <div className="world-scene" ref={hostRef} />
}
