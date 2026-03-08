import bodyParser from 'body-parser'
import cors from 'cors'
import express from 'express'
import { spawn } from 'node:child_process'
import { promises as fs } from 'node:fs'
import path from 'node:path'
import type { Request, Response } from 'express'

const app = express()
const PORT = 3001

const leanDir = path.resolve(process.cwd(), '..', 'lean')
const leanFilePath = path.join(leanDir, 'Main.lean')

app.use(cors())
app.use(bodyParser.json())

app.get('/api/lean/file', async (_req: Request, res: Response) => {
  try {
    const content = await fs.readFile(leanFilePath, 'utf8')
    res.json({ content })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    res.status(500).json({ error: message })
  }
})

app.post('/api/lean/file', async (req: Request, res: Response) => {
  const content = req.body?.content

  if (typeof content !== 'string') {
    res.status(400).json({ error: 'content must be a string' })
    return
  }

  try {
    await fs.writeFile(leanFilePath, content, 'utf8')
    res.json({ status: 'ok' })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    res.status(500).json({ error: message })
  }
})

app.post('/api/lean/check', async (_req: Request, res: Response) => {
  try {
    const output = await runLeanCheck()
    res.json(output)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    res.status(500).json({ stdout: '', stderr: message })
  }
})

app.listen(PORT, () => {
  console.log(`Lean backend listening on http://localhost:${PORT}`)
})

function runLeanCheck(): Promise<{ stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn('lake', ['env', 'lean', 'Main.lean'], {
      cwd: leanDir,
      shell: process.platform === 'win32',
    })

    let stdout = ''
    let stderr = ''

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString()
    })

    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString()
    })

    child.on('error', (error) => {
      reject(error)
    })

    child.on('close', () => {
      resolve({ stdout, stderr })
    })
  })
}
