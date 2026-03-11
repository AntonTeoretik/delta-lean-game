import { Application, Container, Graphics, Text, TextStyle } from 'pixi.js'
import { useEffect, useRef } from 'react'
import type { NodeStatus } from '../model/types'

interface WorldSceneProps {
  activePath: string | null
  status: NodeStatus
  isOpened: boolean
  onOpenNode: () => void
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

export function WorldScene({ activePath, status, isOpened, onOpenNode }: WorldSceneProps) {
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

    if (!activePath) {
      return
    }

    const canvasWidth = app.renderer.width
    const canvasHeight = app.renderer.height

    const container = new Container()
    container.x = Math.max(20, (canvasWidth - NODE_WIDTH) / 2)
    container.y = Math.max(20, (canvasHeight - NODE_HEIGHT) / 2)
    container.eventMode = 'static'
    container.cursor = 'pointer'
    container.on('pointerdown', () => onOpenNode())

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

    const hint = new Text(isOpened ? 'opened' : 'click to open', {
      ...textStyle,
      fontSize: 11,
      fill: 0xbfd5f4,
      wordWrap: false,
    })
    hint.x = 10
    hint.y = NODE_HEIGHT - 22
    container.addChild(hint)

    app.stage.addChild(container)
  }, [activePath, status, isOpened, onOpenNode])

  return <div className="world-scene" ref={hostRef} />
}
