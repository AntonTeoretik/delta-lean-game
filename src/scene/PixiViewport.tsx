import { useEffect, useRef } from 'react'
import { Application, Container } from 'pixi.js'
import { createCameraController } from './camera'
import { createGrid } from './grid'

function PixiViewport() {
  const containerRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const host = containerRef.current

    if (!host) {
      return
    }

    const app = new Application()
    const world = new Container()

    let destroyed = false
    let initialized = false
    let canvas: HTMLCanvasElement | null = null
    let isDragging = false
    let lastPointerX = 0
    let lastPointerY = 0

    const setCursor = () => {
      host.style.cursor = isDragging ? 'grabbing' : 'grab'
    }

    const toLocalCanvasPoint = (clientX: number, clientY: number) => {
      const rect = app.canvas.getBoundingClientRect()

      return {
        x: clientX - rect.left,
        y: clientY - rect.top,
      }
    }

    const onPointerDown = (event: PointerEvent) => {
      if (event.button !== 0) {
        return
      }

      isDragging = true
      lastPointerX = event.clientX
      lastPointerY = event.clientY
      setCursor()
      event.preventDefault()
    }

    const onPointerMove = (event: PointerEvent) => {
      if (!isDragging) {
        return
      }

      const dx = event.clientX - lastPointerX
      const dy = event.clientY - lastPointerY
      lastPointerX = event.clientX
      lastPointerY = event.clientY
      camera.panBy(dx, dy)
    }

    const stopDragging = () => {
      if (!isDragging) {
        return
      }

      isDragging = false
      setCursor()
    }

    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const point = toLocalCanvasPoint(event.clientX, event.clientY)
      camera.zoomAt(point.x, point.y, event.deltaY)
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

      canvas = app.canvas
      initialized = true

      host.appendChild(canvas)
      host.style.userSelect = 'none'
      host.style.touchAction = 'none'
      setCursor()

      world.addChild(createGrid())
      app.stage.addChild(world)

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
      window.addEventListener('resize', onResize)
    })()

    return () => {
      destroyed = true

      if (canvas) {
        canvas.removeEventListener('pointerdown', onPointerDown)
        canvas.removeEventListener('wheel', onWheel)
      }

      window.removeEventListener('pointermove', onPointerMove)
      window.removeEventListener('pointerup', stopDragging)
      window.removeEventListener('pointercancel', stopDragging)
      window.removeEventListener('resize', onResize)

      if (initialized) {
        app.destroy(true, true)
      }
    }
  }, [])

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
}

export default PixiViewport
