import type { DiagnosticItem } from '../model/types'

interface DiagnosticsPanelProps {
  diagnostics: DiagnosticItem[]
  selectedPath: string | null
}

function severityText(severity: number | null): string {
  if (severity === 1 || severity === null) {
    return 'error'
  }
  if (severity === 2) {
    return 'warning'
  }
  return `severity:${severity}`
}

export function DiagnosticsPanel({ diagnostics, selectedPath }: DiagnosticsPanelProps) {
  return (
    <section className="panel-block diagnostics-block">
      <div className="panel-header">
        <strong>Diagnostics</strong>
        <span>{selectedPath ?? 'No file selected'}</span>
      </div>

      {diagnostics.length === 0 ? (
        <div className="panel-empty">Диагностик нет.</div>
      ) : (
        <ul className="diagnostics-list">
          {diagnostics.map((item, index) => (
            <li key={`${item.message}-${index}`} className="diagnostic-item">
              <div className="diagnostic-severity">[{severityText(item.severity)}]</div>
              <div className="diagnostic-message">{item.message}</div>
              <div className="diagnostic-range">
                ({item.range.start.line}:{item.range.start.character}) - ({item.range.end.line}:
                {item.range.end.character})
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
