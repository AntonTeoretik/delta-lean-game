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
    files,
    selectedFilePath,
    nodes,
    containers,
    selectedItemId,
    selectedItem,
    isOpeningWorkspace,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectFile,
    selectItem,
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
          files={files}
          selectedFilePath={selectedFilePath}
          onSelectFile={selectFile}
        />

        <section className="world-area">
          <WorldScene
            nodes={nodes}
            containers={containers}
            selectedItemId={selectedItemId}
            onSelectItem={selectItem}
          />
          <div className="world-status">Items: {nodes.length}</div>
        </section>

        <aside className="side-panel">
          <EditorPanel
            selectedItemTitle={selectedItem?.title ?? null}
            selectedFilePath={selectedItem?.filePath ?? null}
            selectedItemKind={selectedItem?.kind ?? null}
            content={selectedItem?.code ?? ''}
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
