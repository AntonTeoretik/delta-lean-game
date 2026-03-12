import type { WorldSnapshotDto } from '../model/types'

interface OpenWorkspaceResponse {
  success: boolean
  fileCount: number
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

  async getWorld(): Promise<WorldSnapshotDto> {
    return this.requestJson<WorldSnapshotDto>('/api/world')
  }

  async updateItemCode(id: string, code: string): Promise<void> {
    await this.requestVoid(`/api/items/${encodeURIComponent(id)}/code`, {
      method: 'PUT',
      body: JSON.stringify({ code }),
    })
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
