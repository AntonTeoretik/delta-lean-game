import { useEffect, useRef } from 'react'
import { checkLean, getLeanFile, writeLeanFile } from './api/lean'
import PixiViewport from './scene/PixiViewport'

function App() {
  const hasRunRef = useRef(false)

  useEffect(() => {
    if (hasRunRef.current) {
      return
    }

    hasRunRef.current = true

    const runLeanSmokeTest = async () => {
      try {
        const file = await getLeanFile()
        console.log('Lean file content from backend:', file.content)

        const content = 'theorem id (A : Prop) : A -> A := fun x => x\n'
        await writeLeanFile(content)

        const result = await checkLean()
        console.log('Lean check stdout:', result.stdout)
        console.log('Lean check stderr:', result.stderr)
      } catch (error) {
        console.error('Lean backend request failed:', error)
      }
    }

    void runLeanSmokeTest()
  }, [])

  return <PixiViewport />
}

export default App
