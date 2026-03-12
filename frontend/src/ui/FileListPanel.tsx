import type { WorldItemDto } from '../model/types'

interface FileListPanelProps {
  items: WorldItemDto[]
  selectedItemId: string | null
  onSelectItem: (id: string) => void
}

export function FileListPanel({ items, selectedItemId, onSelectItem }: FileListPanelProps) {
  return (
    <aside className="file-list-panel">
      <div className="file-list-title">World items</div>
      <ul className="file-list">
        {items.map((item) => {
          const count = item.diagnostics.length
          const hasError = item.status === 'ERROR'
          return (
            <li key={item.id}>
              <button
                className={`file-item ${selectedItemId === item.id ? 'active' : ''}`}
                onClick={() => onSelectItem(item.id)}
              >
                <span className="file-path">{item.title}</span>
                {count > 0 && <span className={hasError ? 'diag-badge error' : 'diag-badge'}>{count}</span>}
              </button>
            </li>
          )
        })}
      </ul>
    </aside>
  )
}
