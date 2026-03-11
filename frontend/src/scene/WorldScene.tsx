import { Application, Container, FederatedPointerEvent, Graphics, Text, TextStyle } from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { FileNode } from '../model/types'

interface WorldSceneProps {
  nodes: FileNode[]
  selectedPath: string | null
  onSelectNode: (path: string) => void
  onMoveNode: (path: string, x: number, y: number) => void
}

const NODE_WIDTH = 190
const NODE_HEIGHT = 88

const statusColors = {
  neutral: 0x2e3848,
  warning: 0x8a6d1a,
  error: 0x8f2c2c,
}

const textStyle = new TextStyle({
  fontFamily: 'monospace',
  fontSize: 12,
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

export function WorldScene({ nodes, selectedPath, onSelectNode, onMoveNode }: WorldSceneProps) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const appRef = useRef<Application | null>(null)

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
      appRef.current = app
    }

    void initPixi()

    return () => {
      isMounted = false
      const app = appRef.current
      appRef.current = null
      if (app) {
        app.destroy(true)
      }
    }
  }, [])

  useEffect(() => {
    const app = appRef.current
    if (!app) {
      return
    }

    app.stage.removeChildren()

    nodes.forEach((node) => {
      const container = new Container()
      container.x = node.x
      container.y = node.y
      container.eventMode = 'static'
      container.cursor = 'pointer'

      const background = new Graphics()
      background.beginFill(statusColors[node.status])
      background.lineStyle(
        selectedPath === node.path ? 3 : 1,
        selectedPath === node.path ? 0xe7f0ff : 0x4c596d,
      )
      background.drawRoundedRect(0, 0, NODE_WIDTH, NODE_HEIGHT, 12)
      background.endFill()
      container.addChild(background)

      const label = new Text(truncatePath(node.path), textStyle)
      label.x = 10
      label.y = 10
      container.addChild(label)

      let dragging = false
      let dragOffsetX = 0
      let dragOffsetY = 0

      container.on('pointerdown', (event: FederatedPointerEvent) => {
        dragging = true
        dragOffsetX = event.global.x - container.x
        dragOffsetY = event.global.y - container.y
        onSelectNode(node.path)
      })

      container.on('pointerup', () => {
        dragging = false
      })

      container.on('pointerupoutside', () => {
        dragging = false
      })

      container.on('pointermove', (event: FederatedPointerEvent) => {
        if (!dragging) {
          return
        }

        const nextX = Math.max(8, event.global.x - dragOffsetX)
        const nextY = Math.max(8, event.global.y - dragOffsetY)
        container.x = nextX
        container.y = nextY
        onMoveNode(node.path, nextX, nextY)
      })

      app.stage.addChild(container)
    })
  }, [nodes, selectedPath, onMoveNode, onSelectNode])

  return <div className="world-scene" ref={hostRef} />
}
