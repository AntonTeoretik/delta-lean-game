import type { DiagnosticItem } from '../model/types'

interface FileListPanelProps {
  files: string[]
  activeFilePath: string | null
  diagnosticsMap: Record<string, DiagnosticItem[]>
  onSelectFile: (path: string) => void
}

export function FileListPanel({
  files,
  activeFilePath,
  diagnosticsMap,
  onSelectFile,
}: FileListPanelProps) {
  return (
    <aside className="file-list-panel">
      <div className="file-list-title">Workspace files</div>
      <ul className="file-list">
        {files.map((file) => {
          const count = diagnosticsMap[file]?.length ?? 0
          const hasError = (diagnosticsMap[file] ?? []).some((item) => item.severity === null || item.severity === 1)
          return (
            <li key={file}>
              <button
                className={`file-item ${activeFilePath === file ? 'active' : ''}`}
                onClick={() => onSelectFile(file)}
              >
                <span className="file-path">{file}</span>
                {count > 0 && <span className={hasError ? 'diag-badge error' : 'diag-badge'}>{count}</span>}
              </button>
            </li>
          )
        })}
      </ul>
    </aside>
  )
}
