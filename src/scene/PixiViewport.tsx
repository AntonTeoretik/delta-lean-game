import { useEffect, useRef } from 'react'
import { Application, Container, Graphics, Text } from 'pixi.js'
import { createCameraController } from './camera'
import { createGrid } from './grid'
import { NODE_HEIGHT, NODE_WIDTH, type WorldNode, type WorldNodeStatus } from '../world/model'

interface CreateMenuPoint {
  screenX: number
  screenY: number
  worldX: number
  worldY: number
}

interface PixiViewportProps {
  nodes: WorldNode[]
  selectedNodeId: string | null
  onMoveNode: (id: string, x: number, y: number) => void
  onSelectNode: (id: string | null) => void
  onRequestCreateMenu: (point: CreateMenuPoint) => void
}

interface Runtime {
  app: Application
  world: Container
  nodesLayer: Container
  camera: ReturnType<typeof createCameraController>
  canvas: HTMLCanvasElement
  host: HTMLDivElement
}

function PixiViewport({
  nodes,
  selectedNodeId,
  onMoveNode,
  onSelectNode,
  onRequestCreateMenu,
}: PixiViewportProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const runtimeRef = useRef<Runtime | null>(null)
  const nodesRef = useRef(nodes)
  const selectedNodeIdRef = useRef(selectedNodeId)
  const onMoveNodeRef = useRef(onMoveNode)
  const onSelectNodeRef = useRef(onSelectNode)
  const onRequestCreateMenuRef = useRef(onRequestCreateMenu)

  useEffect(() => {
    nodesRef.current = nodes
  }, [nodes])

  useEffect(() => {
    selectedNodeIdRef.current = selectedNodeId
  }, [selectedNodeId])

  useEffect(() => {
    onMoveNodeRef.current = onMoveNode
  }, [onMoveNode])

  useEffect(() => {
    onSelectNodeRef.current = onSelectNode
  }, [onSelectNode])

  useEffect(() => {
    onRequestCreateMenuRef.current = onRequestCreateMenu
  }, [onRequestCreateMenu])

  useEffect(() => {
    const runtime = runtimeRef.current

    if (!runtime) {
      return
    }

    drawNodes(runtime.nodesLayer, nodes, selectedNodeId)
  }, [nodes, selectedNodeId])

  useEffect(() => {
    const host = containerRef.current

    if (!host) {
      return
    }

    const app = new Application()
    const world = new Container()
    const nodesLayer = new Container()

    let destroyed = false
    let initialized = false
    let isCameraDragging = false
    let draggingNodeId: string | null = null
    let dragOffsetX = 0
    let dragOffsetY = 0
    let lastPointerX = 0
    let lastPointerY = 0

    const setCursor = () => {
      host.style.cursor = isCameraDragging || draggingNodeId ? 'grabbing' : 'grab'
    }

    const toCanvasPoint = (clientX: number, clientY: number) => {
      const rect = app.canvas.getBoundingClientRect()

      return {
        x: clientX - rect.left,
        y: clientY - rect.top,
      }
    }

    const toWorldPoint = (clientX: number, clientY: number) => {
      const point = toCanvasPoint(clientX, clientY)
      const camera = runtimeRef.current?.camera

      if (!camera) {
        return { x: 0, y: 0 }
      }

      return {
        x: (point.x - camera.currentX) / camera.currentScale,
        y: (point.y - camera.currentY) / camera.currentScale,
      }
    }

    const onPointerDown = (event: PointerEvent) => {
      if (event.button !== 0) {
        return
      }

      const worldPoint = toWorldPoint(event.clientX, event.clientY)
      const hitNode = hitTestNode(nodesRef.current, worldPoint.x, worldPoint.y)

      if (hitNode) {
        draggingNodeId = hitNode.id
        dragOffsetX = worldPoint.x - hitNode.x
        dragOffsetY = worldPoint.y - hitNode.y
        onSelectNodeRef.current(hitNode.id)
        setCursor()
        event.preventDefault()
        return
      }

      isCameraDragging = true
      lastPointerX = event.clientX
      lastPointerY = event.clientY
      onSelectNodeRef.current(null)
      setCursor()
      event.preventDefault()
    }

    const onPointerMove = (event: PointerEvent) => {
      if (draggingNodeId) {
        const point = toWorldPoint(event.clientX, event.clientY)
        onMoveNodeRef.current(draggingNodeId, point.x - dragOffsetX, point.y - dragOffsetY)
        return
      }

      if (!isCameraDragging) {
        return
      }

      const dx = event.clientX - lastPointerX
      const dy = event.clientY - lastPointerY
      lastPointerX = event.clientX
      lastPointerY = event.clientY
      runtimeRef.current?.camera.panBy(dx, dy)
    }

    const stopDragging = () => {
      isCameraDragging = false
      draggingNodeId = null
      setCursor()
    }

    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const point = toCanvasPoint(event.clientX, event.clientY)
      runtimeRef.current?.camera.zoomAt(point.x, point.y, event.deltaY)
    }

    const onContextMenu = (event: MouseEvent) => {
      event.preventDefault()

      const worldPoint = toWorldPoint(event.clientX, event.clientY)
      const hitNode = hitTestNode(nodesRef.current, worldPoint.x, worldPoint.y)

      if (hitNode) {
        return
      }

      onRequestCreateMenuRef.current({
        screenX: event.clientX,
        screenY: event.clientY,
        worldX: worldPoint.x,
        worldY: worldPoint.y,
      })
    }

    const onResize = () => {
      app.renderer.resize(window.innerWidth, window.innerHeight)
    }

    const camera = createCameraController(world)

    void (async () => {
      await app.init({
        backgroundColor: 0x111111,
        width: window.innerWidth,
        height: window.innerHeight,
        antialias: true,
      })

      if (destroyed) {
        app.destroy(true, true)
        return
      }

      const canvas = app.canvas
      initialized = true

      host.appendChild(canvas)
      host.style.userSelect = 'none'
      host.style.touchAction = 'none'
      setCursor()

      world.addChild(createGrid())
      world.addChild(nodesLayer)
      app.stage.addChild(world)

      runtimeRef.current = {
        app,
        world,
        nodesLayer,
        camera,
        canvas,
        host,
      }

      drawNodes(nodesLayer, nodesRef.current, selectedNodeIdRef.current)

      camera.currentX = app.screen.width * 0.5
      camera.currentY = app.screen.height * 0.5
      camera.targetX = camera.currentX
      camera.targetY = camera.currentY
      camera.update()

      app.ticker.add(() => {
        camera.update()
      })

      canvas.addEventListener('pointerdown', onPointerDown)
      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', stopDragging)
      window.addEventListener('pointercancel', stopDragging)
      canvas.addEventListener('wheel', onWheel, { passive: false })
      canvas.addEventListener('contextmenu', onContextMenu)
      window.addEventListener('resize', onResize)
    })()

    return () => {
      destroyed = true

      const runtime = runtimeRef.current

      if (runtime) {
        runtime.canvas.removeEventListener('pointerdown', onPointerDown)
        runtime.canvas.removeEventListener('wheel', onWheel)
        runtime.canvas.removeEventListener('contextmenu', onContextMenu)
      }

      window.removeEventListener('pointermove', onPointerMove)
      window.removeEventListener('pointerup', stopDragging)
      window.removeEventListener('pointercancel', stopDragging)
      window.removeEventListener('resize', onResize)

      if (initialized) {
        app.destroy(true, true)
      }

      runtimeRef.current = null
    }
  }, [])

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
}

function drawNodes(
  layer: Container,
  nodes: WorldNode[],
  selectedNodeId: string | null,
): void {
  for (const child of layer.removeChildren()) {
    child.destroy({ children: true })
  }

  for (const node of nodes) {
    const nodeContainer = new Container()
    nodeContainer.position.set(node.x, node.y)

    const box = new Graphics()
    box.roundRect(0, 0, NODE_WIDTH, NODE_HEIGHT, 10)
    box.fill(statusToColor(node.status))
    box.stroke({
      color: selectedNodeId === node.id ? 0xffffff : 0x3a3a3a,
      width: selectedNodeId === node.id ? 3 : 1,
      alpha: 1,
    })

    const title = new Text({
      text: node.name,
      style: {
        fill: 0xffffff,
        fontSize: 16,
        fontFamily: 'monospace',
      },
    })
    title.position.set(12, 10)

    const subtitle = new Text({
      text: `${node.kind} • ${node.status}`,
      style: {
        fill: 0xcccccc,
        fontSize: 12,
        fontFamily: 'monospace',
      },
    })
    subtitle.position.set(12, 38)

    nodeContainer.addChild(box)
    nodeContainer.addChild(title)
    nodeContainer.addChild(subtitle)
    layer.addChild(nodeContainer)
  }
}

function hitTestNode(nodes: WorldNode[], worldX: number, worldY: number): WorldNode | null {
  for (let i = nodes.length - 1; i >= 0; i -= 1) {
    const node = nodes[i]
    const withinX = worldX >= node.x && worldX <= node.x + NODE_WIDTH
    const withinY = worldY >= node.y && worldY <= node.y + NODE_HEIGHT

    if (withinX && withinY) {
      return node
    }
  }

  return null
}

function statusToColor(status: WorldNodeStatus): number {
  if (status === 'ok') {
    return 0x224d28
  }

  if (status === 'error') {
    return 0x5f1e1e
  }

  if (status === 'sorry') {
    return 0x5e5116
  }

  return 0x2f2f2f
}

export default PixiViewport
