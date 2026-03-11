import { Graphics } from 'pixi.js'

const GRID_MIN = -5000
const GRID_MAX = 5000
const GRID_STEP = 100

export function createGrid(): Graphics {
  const grid = new Graphics()

  for (let x = GRID_MIN; x <= GRID_MAX; x += GRID_STEP) {
    grid.moveTo(x, GRID_MIN)
    grid.lineTo(x, GRID_MAX)
  }

  for (let y = GRID_MIN; y <= GRID_MAX; y += GRID_STEP) {
    grid.moveTo(GRID_MIN, y)
    grid.lineTo(GRID_MAX, y)
  }

  grid.stroke({
    color: 0x78859b,
    width: 1,
    alpha: 0.23,
  })

  grid.moveTo(0, GRID_MIN)
  grid.lineTo(0, GRID_MAX)
  grid.moveTo(GRID_MIN, 0)
  grid.lineTo(GRID_MAX, 0)
  grid.stroke({
    color: 0xa4b6d2,
    width: 2,
    alpha: 0.45,
  })

  return grid
}
