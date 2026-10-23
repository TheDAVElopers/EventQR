import type { MonitorResponse } from '../types'
import { parseMonitorResponse } from './parse'

export type FetchOutcome =
  | { kind: 'ok'; data: MonitorResponse }
  | { kind: 'unauthorized' }
  | { kind: 'disabled' }
  | { kind: 'error'; status: number | null; message: string; retryAfterMs?: number }
  | { kind: 'aborted' }

export interface FetchEventsArgs {
  baseUrl: string
  /** Empty string means anonymous: no Authorization header is sent at all. */
  token: string
  after: number
  limit: number
  signal?: AbortSignal
  /** Abort a hanging request (e.g. Render cold start) after this many ms. */
  timeoutMs?: number
  fetchImpl?: typeof fetch
}

/** Retry-After in delta-seconds -> ms; undefined when absent or not a plain number. */
export function parseRetryAfter(value: string | null): number | undefined {
  if (value === null || !/^\s*\d+(\.\d+)?\s*$/.test(value)) return undefined
  return Math.round(Number(value) * 1000)
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
      headers: token
        ? { Authorization: `Bearer ${token}`, Accept: 'application/json' }
        : { Accept: 'application/json' },
      signal: controller.signal,
      cache: 'no-store',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    })
    if (res.status === 401) return { kind: 'unauthorized' }
    if (res.status === 404) return { kind: 'disabled' }
    if (!res.ok) {
      const error: FetchOutcome = {
        kind: 'error',
        status: res.status,
        message:
          res.status === 429
            ? 'Rate limited by the server (HTTP 429)'
            : `Server responded with HTTP ${res.status}`,
      }
      if (res.status === 429) {
        const retryAfterMs = parseRetryAfter(res.headers.get('Retry-After'))
        if (retryAfterMs !== undefined) error.retryAfterMs = retryAfterMs
      }
      return error
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
