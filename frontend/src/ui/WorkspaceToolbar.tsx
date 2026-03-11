interface WorkspaceToolbarProps {
  workspaceRoot: string
  isOpening: boolean
  onWorkspaceRootChange: (value: string) => void
  onDirectorySelect: (files: FileList | null) => void
  onOpenWorkspace: () => void
}

export function WorkspaceToolbar({
  workspaceRoot,
  isOpening,
  onWorkspaceRootChange,
  onDirectorySelect,
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
      <label className="toolbar-file-picker">
        Select folder
        <input
          type="file"
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore webkitdirectory is not in standard input typings
          webkitdirectory=""
          onChange={(event) => onDirectorySelect(event.target.files)}
        />
      </label>
      <button className="toolbar-button" onClick={onOpenWorkspace} disabled={isOpening}>
        {isOpening ? 'Открытие...' : 'Open workspace'}
      </button>
    </header>
  )
}
