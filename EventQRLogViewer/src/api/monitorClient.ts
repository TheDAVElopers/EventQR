import type { MonitorResponse } from '../types'
import { parseMonitorResponse } from './parse'

export type FetchOutcome =
  | { kind: 'ok'; data: MonitorResponse }
  | { kind: 'unauthorized' }
  | { kind: 'disabled' }
  | { kind: 'error'; status: number | null; message: string }
  | { kind: 'aborted' }

export interface FetchEventsArgs {
  baseUrl: string
  token: string
  after: number
  limit: number
  signal?: AbortSignal
  /** Abort a hanging request (e.g. Render cold start) after this many ms. */
  timeoutMs?: number
  fetchImpl?: typeof fetch
}

/**
 * Read-only GET of the monitor feed. Never throws; every outcome is a value.
 * The token is only placed in the Authorization header and never in messages.
 */
export async function fetchMonitorEvents(args: FetchEventsArgs): Promise<FetchOutcome> {
  const { baseUrl, token, after, limit, signal, timeoutMs = 30_000 } = args
  const doFetch = args.fetchImpl ?? globalThis.fetch.bind(globalThis)
  const query = new URLSearchParams({ after: String(after), limit: String(limit) })
  const url = `${baseUrl}/api/v1/monitor/events?${query.toString()}`

  if (signal?.aborted) return { kind: 'aborted' }
  const controller = new AbortController()
  let timedOut = false
  const onAbort = () => controller.abort()
  signal?.addEventListener('abort', onAbort, { once: true })
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)

  try {
    const res = await doFetch(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      signal: controller.signal,
      cache: 'no-store',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    })
    if (res.status === 401) return { kind: 'unauthorized' }
    if (res.status === 404) return { kind: 'disabled' }
    if (!res.ok) {
      return { kind: 'error', status: res.status, message: `Server responded with HTTP ${res.status}` }
    }
    let body: unknown
    try {
      body = await res.json()
    } catch {
      if (signal?.aborted) return { kind: 'aborted' }
      return { kind: 'error', status: res.status, message: 'Response was not valid JSON' }
    }
    const data = parseMonitorResponse(body)
    if (!data) {
      return { kind: 'error', status: res.status, message: 'Unexpected response format' }
    }
    return { kind: 'ok', data }
  } catch {
    if (signal?.aborted) return { kind: 'aborted' }
    if (timedOut) return { kind: 'error', status: null, message: 'Request timed out' }
    return { kind: 'error', status: null, message: 'Network error: backend unreachable' }
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', onAbort)
  }
}
