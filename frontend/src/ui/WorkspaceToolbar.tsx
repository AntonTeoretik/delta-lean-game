interface WorkspaceToolbarProps {
  workspaceRoot: string
  isOpening: boolean
  onWorkspaceRootChange: (value: string) => void
  onOpenWorkspace: () => void
}

export function WorkspaceToolbar({
  workspaceRoot,
  isOpening,
  onWorkspaceRootChange,
  onOpenWorkspace,
}: WorkspaceToolbarProps) {
  return (
    <header className="toolbar">
      <div className="toolbar-title">Delta Lean Game MVP</div>
      <input
        className="toolbar-input"
        placeholder="Абсолютный путь к Lean workspace"
        value={workspaceRoot}
        onChange={(event) => onWorkspaceRootChange(event.target.value)}
      />
      <button className="toolbar-button" onClick={onOpenWorkspace} disabled={isOpening}>
        {isOpening ? 'Открытие...' : 'Open workspace'}
      </button>
    </header>
  )
}
