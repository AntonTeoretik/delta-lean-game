interface EditorPanelProps {
  selectedPath: string | null
  isOpened: boolean
  content: string
  isLoadingFile: boolean
  isSaving: boolean
  isDirty: boolean
  onContentChange: (value: string) => void
  onSave: () => void
}

export function EditorPanel({
  selectedPath,
  isOpened,
  content,
  isLoadingFile,
  isSaving,
  isDirty,
  onContentChange,
  onSave,
}: EditorPanelProps) {
  if (!selectedPath) {
    return <div className="panel-empty">Выберите узел в сцене, чтобы редактировать файл.</div>
  }

  if (!isOpened) {
    return <div className="panel-empty">Нажмите на узел в центре, чтобы открыть код файла.</div>
  }

  return (
    <section className="panel-block">
      <div className="panel-header">
        <strong>{selectedPath}</strong>
        <button onClick={onSave} disabled={isSaving || isLoadingFile || !isDirty}>
          {isSaving ? 'Saving...' : 'Save now'}
        </button>
      </div>

      <textarea
        className="editor-textarea"
        value={content}
        onChange={(event) => onContentChange(event.target.value)}
        disabled={isLoadingFile}
      />

      <div className="panel-meta">
        {isSaving ? 'Autosave in progress...' : isDirty ? 'Autosave pending...' : 'Autosaved'}
      </div>
    </section>
  )
}
