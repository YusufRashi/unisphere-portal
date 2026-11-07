import { getAccessToken, refreshSession } from './client'

export interface StreamEvent {
  event: string
  data: string
}

/**
 * Splits Server-Sent Events text into events. `buffer` holds whatever has arrived but is not yet a complete event,
 * so a chunk that ends mid-event is finished by the next one. Comment lines (": ping") are ignored.
 */
export function parseEvents(buffer: string): { events: StreamEvent[]; rest: string } {
  const normalised = buffer.replace(/\r\n/g, '\n')
  const blocks = normalised.split('\n\n')
  const rest = blocks.pop() ?? ''
  const events = blocks.flatMap((block) => {
    let event = 'message'
    const data: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
    }
    return data.length > 0 ? [{ event, data: data.join('\n') }] : []
  })
  return { events, rest }
}

/**
 * Listens to /api/notifications/stream until `signal` aborts, calling `onEvent` for each event. Uses fetch rather
 * than EventSource because the access token travels in a header, never in the URL. When the server ends the
 * stream (the token it was opened with is about to expire) it reconnects with a fresh token; after a failure it
 * waits a little longer each time, up to 30 seconds.
 */
export async function listenToNotifications(onEvent: (e: StreamEvent) => void, signal: AbortSignal): Promise<void> {
  let failures = 0
  while (!signal.aborted) {
    try {
      let token = getAccessToken()
      if (!token) {
        await refreshSession()
        token = getAccessToken()
      }
      const response = await fetch('/api/notifications/stream', {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal,
      })
      if (response.status === 401) {
        await refreshSession()
        continue
      }
      if (!response.ok || !response.body) throw new Error(`Notification stream returned ${response.status}`)
      failures = 0
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        const parsed = parseEvents(buffer + decoder.decode(value, { stream: true }))
        buffer = parsed.rest
        parsed.events.forEach(onEvent)
      }
    } catch {
      if (signal.aborted) return
      failures++
      await new Promise((resolve) => setTimeout(resolve, Math.min(30_000, 1_000 * 2 ** failures)))
    }
  }
}
