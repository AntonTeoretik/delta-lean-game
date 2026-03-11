import './App.css'
import { WorldScene } from './scene/WorldScene'
import { useDeltaLeanState } from './state/useDeltaLeanState'
import { DiagnosticsPanel } from './ui/DiagnosticsPanel'
import { EditorPanel } from './ui/EditorPanel'
import { FileListPanel } from './ui/FileListPanel'
import { WorkspaceToolbar } from './ui/WorkspaceToolbar'

function App() {
  const {
    workspaceRoot,
    files,
    activeFilePath,
    activeFileStatus,
    activeNodePosition,
    isNodeOpened,
    activeDiagnostics,
    diagnosticsMap,
    editorContent,
    isOpeningWorkspace,
    isLoadingFile,
    isSaving,
    isDirty,
    error,
    setWorkspaceRoot,
    setWorkspaceRootFromDirectorySelection,
    openWorkspace,
    selectFile,
    openActiveNode,
    moveActiveNode,
    setEditorContent,
    saveSelectedFile,
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
          activeFilePath={activeFilePath}
          diagnosticsMap={diagnosticsMap}
          onSelectFile={selectFile}
        />

        <section className="world-area">
          <WorldScene
            activePath={activeFilePath}
            status={activeFileStatus}
            nodePosition={activeNodePosition}
            isOpened={isNodeOpened}
            onOpenNode={openActiveNode}
            onMoveNode={moveActiveNode}
          />
          <div className="world-status">Files: {files.length}</div>
        </section>

        <aside className="side-panel">
          <EditorPanel
            selectedPath={activeFilePath}
            isOpened={isNodeOpened}
            content={editorContent}
            isLoadingFile={isLoadingFile}
            isSaving={isSaving}
            isDirty={isDirty}
            onContentChange={setEditorContent}
            onSave={saveSelectedFile}
          />
          <DiagnosticsPanel diagnostics={activeDiagnostics} selectedPath={activeFilePath} />
        </aside>
      </main>
    </div>
  )
}

export default App
