import type { DiagnosticDto } from '../model/types'

interface DiagnosticsPanelProps {
  diagnostics: DiagnosticDto[]
  selectedItemTitle: string | null
}

function severityText(severity: string | null): string {
  return severity?.toLowerCase() ?? 'error'
}

export function DiagnosticsPanel({ diagnostics, selectedItemTitle }: DiagnosticsPanelProps) {
  return (
    <section className="panel-block diagnostics-block">
      <div className="panel-header">
        <strong>Diagnostics</strong>
        <span>{selectedItemTitle ?? 'No item selected'}</span>
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
