import './App.css'
import { WorldScene } from './scene/WorldScene'
import { diagnosticsForItem, useDeltaLeanState } from './state/useDeltaLeanState'
import { DiagnosticsPanel } from './ui/DiagnosticsPanel'
import { EditorPanel } from './ui/EditorPanel'
import { FileListPanel } from './ui/FileListPanel'
import { WorkspaceToolbar } from './ui/WorkspaceToolbar'

function App() {
  const {
    workspaceRoot,
    nodes,
    world,
    selectedItemId,
    selectedItem,
    editorContent,
    isOpeningWorkspace,
    isSaving,
    isDirty,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectItem,
    moveNode,
    setEditorContent,
    saveSelectedItem,
    clearError,
  } = useDeltaLeanState()

  return (
    <div className="app-shell">
      <WorkspaceToolbar
        workspaceRoot={workspaceRoot}
        isOpening={isOpeningWorkspace}
        onWorkspaceRootChange={setWorkspaceRoot}
        onDirectorySelect={setWorkspaceRootFromDirectorySelection}
        onOpenWorkspace={openWorkspace}
      />

      {error && (
        <div className="error-banner">
          <span>{error}</span>
          <button onClick={clearError}>Dismiss</button>
        </div>
      )}

      <main className="main-layout">
        <FileListPanel
          items={world?.files.flatMap((file) => file.items) ?? []}
          selectedItemId={selectedItemId}
          onSelectItem={selectItem}
        />

        <section className="world-area">
          <WorldScene
            nodes={nodes}
            selectedItemId={selectedItemId}
            onSelectItem={selectItem}
            onMoveNode={moveNode}
          />
          <div className="world-status">Items: {nodes.length}</div>
        </section>

        <aside className="side-panel">
          <EditorPanel
            selectedItemTitle={selectedItem?.title ?? null}
            selectedFilePath={selectedItem?.filePath ?? null}
            content={editorContent}
            isSaving={isSaving}
            isDirty={isDirty}
            onContentChange={setEditorContent}
            onSave={saveSelectedItem}
          />
          <DiagnosticsPanel
            diagnostics={diagnosticsForItem(selectedItem)}
            selectedItemTitle={selectedItem?.title ?? null}
          />
        </aside>
      </main>
    </div>
  )
}

export default App
