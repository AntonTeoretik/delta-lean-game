import type { FileDiagnostics, FileDocument } from '../model/types'

interface OpenWorkspaceResponse {
  success: boolean
  fileCount: number
}

interface FilesListResponse {
  files: string[]
}

interface DiagnosticsResponse {
  files: FileDiagnostics[]
}

function toFilePathUrl(path: string): string {
  const encodedPath = path
    .split('/')
    .map((segment) => encodeURIComponent(segment))
    .join('/')
  return `/api/files/${encodedPath}`
}

export class BackendClient {
  private readonly baseUrl: string

  constructor(baseUrl = import.meta.env.VITE_BACKEND_URL ?? '') {
    this.baseUrl = baseUrl.replace(/\/$/, '')
  }

  async openWorkspace(rootPath: string): Promise<OpenWorkspaceResponse> {
    return this.requestJson<OpenWorkspaceResponse>('/api/workspace/open', {
      method: 'POST',
      body: JSON.stringify({ rootPath }),
    })
  }

  async listFiles(): Promise<string[]> {
    const response = await this.requestJson<FilesListResponse>('/api/files')
    return response.files
  }

  async getFile(path: string): Promise<FileDocument> {
    return this.requestJson<FileDocument>(toFilePathUrl(path))
  }

  async updateFile(path: string, content: string): Promise<void> {
    await this.requestVoid(toFilePathUrl(path), {
      method: 'PUT',
      body: JSON.stringify({ content }),
    })
  }

  async getDiagnostics(path?: string): Promise<FileDiagnostics[]> {
    const query = path ? `?path=${encodeURIComponent(path)}` : ''
    const response = await this.requestJson<DiagnosticsResponse>(`/api/diagnostics${query}`)
    return response.files
  }

  private async requestJson<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(this.buildUrl(path), {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
    }).catch((error: unknown) => {
      throw new Error(
        `Failed to fetch backend API. Ensure backend is running on http://127.0.0.1:8081. ${String(error)}`,
      )
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(errorText || `Request failed with status ${response.status}`)
    }

    return response.json() as Promise<T>
  }

  private async requestVoid(path: string, init?: RequestInit): Promise<void> {
    const response = await fetch(this.buildUrl(path), {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
    }).catch((error: unknown) => {
      throw new Error(
        `Failed to fetch backend API. Ensure backend is running on http://127.0.0.1:8081. ${String(error)}`,
      )
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(errorText || `Request failed with status ${response.status}`)
    }
  }

  private buildUrl(path: string): string {
    if (!this.baseUrl) {
      return path
    }
    return `${this.baseUrl}${path}`
  }
}

export const backendClient = new BackendClient()
