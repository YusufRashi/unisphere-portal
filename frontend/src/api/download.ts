import { api } from './client'

/**
 * Downloads a file through the authenticated API client and hands it to the browser to save. The server's
 * Content-Disposition filename is used when present.
 */
export async function downloadFile(path: string, params: Record<string, unknown> | undefined, fallbackName: string): Promise<void> {
  const response = await api.get<Blob>(path, { params, responseType: 'blob' })
  const disposition = String(response.headers['content-disposition'] ?? '')
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName
  const url = URL.createObjectURL(response.data)
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.append(link)
    link.click()
    link.remove()
  } finally {
    URL.revokeObjectURL(url)
  }
}
