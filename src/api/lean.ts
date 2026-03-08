const API_BASE = 'http://localhost:3001/api/lean'

export interface LeanFileResponse {
  content: string
}

export interface LeanWriteResponse {
  status: string
}

export interface LeanCheckResponse {
  stdout: string
  stderr: string
}

export async function getLeanFile(): Promise<LeanFileResponse> {
  const response = await fetch(`${API_BASE}/file`)

  if (!response.ok) {
    throw new Error(`Failed to read Lean file: ${response.status}`)
  }

  return (await response.json()) as LeanFileResponse
}

export async function writeLeanFile(content: string): Promise<LeanWriteResponse> {
  const response = await fetch(`${API_BASE}/file`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ content }),
  })

  if (!response.ok) {
    throw new Error(`Failed to write Lean file: ${response.status}`)
  }

  return (await response.json()) as LeanWriteResponse
}

export async function checkLean(): Promise<LeanCheckResponse> {
  const response = await fetch(`${API_BASE}/check`, {
    method: 'POST',
  })

  if (!response.ok) {
    throw new Error(`Failed to run Lean check: ${response.status}`)
  }

  return (await response.json()) as LeanCheckResponse
}
