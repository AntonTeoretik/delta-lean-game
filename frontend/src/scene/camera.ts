import type { Container } from 'pixi.js'

const ZOOM_MIN = 0.25
const ZOOM_MAX = 3.5
const LERP = 0.14
const ZOOM_SENSITIVITY = 0.0015

export interface CameraController {
  currentX: number
  currentY: number
  targetX: number
  targetY: number
  currentScale: number
  targetScale: number
  panBy(dx: number, dy: number): void
  zoomAt(screenX: number, screenY: number, wheelDeltaY: number): void
  update(): void
}

export function createCameraController(world: Container): CameraController {
  const camera: CameraController = {
    currentX: 0,
    currentY: 0,
    targetX: 0,
    targetY: 0,
    currentScale: 1,
    targetScale: 1,
    panBy(dx: number, dy: number) {
      camera.targetX += dx
      camera.targetY += dy
    },
    zoomAt(screenX: number, screenY: number, wheelDeltaY: number) {
      const previousScale = camera.targetScale
      const zoomFactor = Math.exp(-wheelDeltaY * ZOOM_SENSITIVITY)
      const nextScale = clamp(previousScale * zoomFactor, ZOOM_MIN, ZOOM_MAX)

      if (nextScale === previousScale) {
        return
      }

      const worldX = (screenX - camera.targetX) / previousScale
      const worldY = (screenY - camera.targetY) / previousScale

      camera.targetScale = nextScale
      camera.targetX = screenX - worldX * nextScale
      camera.targetY = screenY - worldY * nextScale
    },
    update() {
      camera.currentX += (camera.targetX - camera.currentX) * LERP
      camera.currentY += (camera.targetY - camera.currentY) * LERP
      camera.currentScale += (camera.targetScale - camera.currentScale) * LERP

      world.position.set(camera.currentX, camera.currentY)
      world.scale.set(camera.currentScale)
    },
  }

  return camera
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}
