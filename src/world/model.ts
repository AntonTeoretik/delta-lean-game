import type { LeanCheckResponse } from '../api/lean'

export type WorldNodeKind = 'definition' | 'theorem' | 'inductive'
export type WorldNodeStatus = 'unknown' | 'ok' | 'error' | 'sorry'

export interface WorldNode {
  id: string
  kind: WorldNodeKind
  name: string
  x: number
  y: number
  code: string
  status: WorldNodeStatus
}

export const NODE_WIDTH = 220
export const NODE_HEIGHT = 84

export function serializeWorldToLean(nodes: WorldNode[]): string {
  return nodes.map((node) => node.code.trimEnd()).join('\n\n')
}

export function nextNodeName(kind: WorldNodeKind, nodes: WorldNode[]): string {
  const prefix = kind === 'theorem' ? 'theorem' : kind === 'definition' ? 'def' : 'type'
  let maxIndex = 0

  for (const node of nodes) {
    if (!node.name.startsWith(prefix)) {
      continue
    }

    const suffix = Number(node.name.slice(prefix.length))

    if (Number.isInteger(suffix) && suffix > maxIndex) {
      maxIndex = suffix
    }
  }

  return `${prefix}${maxIndex + 1}`
}

export function defaultCodeForNode(kind: WorldNodeKind, name: string): string {
  if (kind === 'theorem') {
    return `theorem ${name} : True := by\n  sorry`
  }

  if (kind === 'definition') {
    return `def ${name} : Nat := by\n  sorry`
  }

  return `inductive ${name} where`
}

export function applyLeanResultToNodes(
  nodes: WorldNode[],
  result: LeanCheckResponse,
): WorldNode[] {
  const hasError = containsLeanError(result)

  return nodes.map((node) => {
    if (hasError) {
      return { ...node, status: 'error' }
    }

    if (node.code.includes('sorry')) {
      return { ...node, status: 'sorry' }
    }

    return { ...node, status: 'ok' }
  })
}

function containsLeanError(result: LeanCheckResponse): boolean {
  const stderr = result.stderr.toLowerCase()
  const stdout = result.stdout.toLowerCase()

  return stderr.includes('error:') || stdout.includes('error:') || stderr.trim().length > 0
}
