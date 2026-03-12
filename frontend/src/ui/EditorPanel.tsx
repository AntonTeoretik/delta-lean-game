interface EditorPanelProps {
  selectedItemTitle: string | null
  selectedFilePath: string | null
  content: string
  isSaving: boolean
  isDirty: boolean
  onContentChange: (value: string) => void
  onSave: () => void
}

export function EditorPanel({
  selectedItemTitle,
  selectedFilePath,
  content,
  isSaving,
  isDirty,
  onContentChange,
  onSave,
}: EditorPanelProps) {
  if (!selectedItemTitle) {
    return <div className="panel-empty">Выберите item в сцене, чтобы редактировать код.</div>
  }

  return (
    <section className="panel-block">
      <div className="panel-header">
        <strong>{selectedItemTitle}</strong>
        <button onClick={onSave} disabled={isSaving || !isDirty}>
          {isSaving ? 'Saving...' : 'Save now'}
        </button>
      </div>

      <div className="panel-meta">{selectedFilePath}</div>

      <textarea
        className="editor-textarea"
        value={content}
        onChange={(event) => onContentChange(event.target.value)}
        spellCheck={false}
        autoCorrect="off"
        autoCapitalize="off"
        data-gramm="false"
        data-gramm_editor="false"
        data-enable-grammarly="false"
        translate="no"
      />

      <div className="panel-meta">
        {isSaving ? 'Autosave in progress...' : isDirty ? 'Autosave pending...' : 'Autosaved'}
      </div>
    </section>
  )
}
