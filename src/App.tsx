import { useEffect, useMemo, useRef, useState } from 'react'
import { checkLean, getLeanFile, writeLeanFile } from './api/lean'
import PixiViewport from './scene/PixiViewport'
import {
  applyLeanResultToNodes,
  defaultCodeForNode,
  nextNodeName,
  serializeWorldToLean,
  type WorldNode,
  type WorldNodeKind,
} from './world/model'

interface CreateMenuState {
  screenX: number
  screenY: number
  worldX: number
  worldY: number
}

function App() {
  const hasRunRef = useRef(false)
  const [nodes, setNodes] = useState<WorldNode[]>([])
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [createMenu, setCreateMenu] = useState<CreateMenuState | null>(null)
  const [draftName, setDraftName] = useState('')
  const [draftCode, setDraftCode] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedNodeId) ?? null,
    [nodes, selectedNodeId],
  )

  useEffect(() => {
    if (hasRunRef.current) {
      return
    }

    hasRunRef.current = true

    const logLeanFile = async () => {
      try {
        const file = await getLeanFile()
        console.log('Lean file content from backend:', file.content)
      } catch (error) {
        console.error('Failed to fetch Lean file:', error)
      }
    }

    void logLeanFile()
  }, [])

  useEffect(() => {
    if (!selectedNode) {
      setDraftName('')
      setDraftCode('')
      return
    }

    setDraftName(selectedNode.name)
    setDraftCode(selectedNode.code)
  }, [selectedNode])

  const handleCreateNode = (kind: WorldNodeKind) => {
    if (!createMenu) {
      return
    }

    const name = nextNodeName(kind, nodes)
    const node: WorldNode = {
      id: createNodeId(),
      kind,
      name,
      x: createMenu.worldX,
      y: createMenu.worldY,
      code: defaultCodeForNode(kind, name),
      status: 'unknown',
    }

    setNodes((prev) => [...prev, node])
    setSelectedNodeId(node.id)
    setCreateMenu(null)
  }

  const handleMoveNode = (id: string, x: number, y: number) => {
    setNodes((prev) => prev.map((node) => (node.id === id ? { ...node, x, y } : node)))
  }

  const handleSaveNode = async () => {
    if (!selectedNodeId) {
      return
    }

    setIsSaving(true)

    const nextNodes: WorldNode[] = nodes.map((node) =>
      node.id === selectedNodeId
        ? {
            ...node,
            name: draftName.trim().length > 0 ? draftName.trim() : node.name,
            code: draftCode,
            status: 'unknown' as const,
          }
        : node,
    )

    setNodes(nextNodes)

    const leanProgram = serializeWorldToLean(nextNodes)

    try {
      await writeLeanFile(leanProgram)
      const check = await checkLean()
      console.log('Lean check stdout:', check.stdout)
      console.log('Lean check stderr:', check.stderr)

      setNodes((prev) => applyLeanResultToNodes(prev, check))
    } catch (error) {
      console.error('Failed to save and check Lean code:', error)
      setNodes((prev) => prev.map((node) => ({ ...node, status: 'error' })))
    } finally {
      setIsSaving(false)
    }
  }

  const closeCreateMenu = () => {
    setCreateMenu(null)
  }

  return (
    <div
      style={{ width: '100%', height: '100%', position: 'relative' }}
      onMouseDown={(event) => {
        if (event.button !== 2) {
          closeCreateMenu()
        }
      }}
    >
      <PixiViewport
        nodes={nodes}
        selectedNodeId={selectedNodeId}
        onMoveNode={handleMoveNode}
        onSelectNode={(id) => {
          setSelectedNodeId(id)
          closeCreateMenu()
        }}
        onRequestCreateMenu={(point) => {
          setCreateMenu(point)
        }}
      />

      {createMenu ? (
        <div
          style={{
            position: 'fixed',
            left: createMenu.screenX,
            top: createMenu.screenY,
            background: '#1e1e1e',
            border: '1px solid #3a3a3a',
            borderRadius: 6,
            padding: 6,
            zIndex: 20,
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            minWidth: 160,
          }}
          onMouseDown={(event) => event.stopPropagation()}
        >
          <MenuButton label="Create theorem" onClick={() => handleCreateNode('theorem')} />
          <MenuButton label="Create definition" onClick={() => handleCreateNode('definition')} />
          <MenuButton label="Create inductive" onClick={() => handleCreateNode('inductive')} />
        </div>
      ) : null}

      {selectedNode ? (
        <div
          style={{
            position: 'fixed',
            right: 0,
            top: 0,
            width: 360,
            height: '100%',
            background: '#171717',
            borderLeft: '1px solid #333',
            color: '#f2f2f2',
            zIndex: 10,
            display: 'flex',
            flexDirection: 'column',
            padding: 12,
            boxSizing: 'border-box',
            gap: 8,
          }}
        >
          <strong style={{ fontFamily: 'monospace' }}>Node Editor</strong>
          <label style={{ fontFamily: 'monospace', fontSize: 12 }}>Name</label>
          <input
            value={draftName}
            onChange={(event) => setDraftName(event.target.value)}
            style={{
              background: '#232323',
              color: '#f2f2f2',
              border: '1px solid #3a3a3a',
              padding: 8,
              fontFamily: 'monospace',
            }}
          />
          <label style={{ fontFamily: 'monospace', fontSize: 12 }}>Lean code</label>
          <textarea
            value={draftCode}
            onChange={(event) => setDraftCode(event.target.value)}
            spellCheck={false}
            style={{
              flex: 1,
              minHeight: 240,
              resize: 'none',
              background: '#232323',
              color: '#f2f2f2',
              border: '1px solid #3a3a3a',
              padding: 8,
              fontFamily: 'monospace',
              lineHeight: 1.4,
            }}
          />
          <button
            onClick={() => {
              void handleSaveNode()
            }}
            disabled={isSaving}
            style={{
              border: '1px solid #585858',
              background: isSaving ? '#303030' : '#2a3b23',
              color: '#f2f2f2',
              padding: '8px 10px',
              cursor: isSaving ? 'default' : 'pointer',
              fontFamily: 'monospace',
            }}
          >
            {isSaving ? 'Saving...' : 'Save + Check Lean'}
          </button>
        </div>
      ) : null}
    </div>
  )
}

function MenuButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{
        border: '1px solid #3f3f3f',
        background: '#2a2a2a',
        color: '#f2f2f2',
        fontFamily: 'monospace',
        padding: '6px 8px',
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      {label}
    </button>
  )
}

function createNodeId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `node-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
}

export default App
