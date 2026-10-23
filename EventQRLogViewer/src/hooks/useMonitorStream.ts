import { useEffect, useRef, useState } from 'react'
import { fetchMonitorEvents } from '../api/monitorClient'
import type { ConnectionStatus, LogEntry, RetryInfo } from '../types'

export const POLL_INTERVAL_MS = 1500
export const BACKOFF_CAP_MS = 10_000
export const MAX_LINES = 1000
/** Backend clamps `limit` to 100 in public mode. */
export const PAGE_LIMIT = 100
/** Upper bound for an honoured Retry-After. */
export const RETRY_AFTER_CAP_MS = 30_000
/** Consecutive failures before the badge flips from Reconnecting to Disconnected. */
export const DISCONNECTED_AFTER_FAILURES = 5
/** First-connection duration after which the cold-start hint is shown. */
export const SLOW_START_MS = 5000

export function backoffDelay(failures: number): number {
  return Math.min(BACKOFF_CAP_MS, POLL_INTERVAL_MS * 2 ** failures)
}

export interface MonitorStreamState {
  status: ConnectionStatus
  entries: LogEntry[]
  eventCount: number
  lastUpdate: number | null
  bufferSize: number | null
  latestSeq: number | null
  retry: RetryInfo | null
  lastError: string | null
  wakeHint: boolean
}

const INITIAL: MonitorStreamState = {
  status: 'connecting',
  entries: [],
  eventCount: 0,
  lastUpdate: null,
  bufferSize: null,
  latestSeq: null,
  retry: null,
  lastError: null,
  wakeHint: false,
}

const countEvents = (entries: LogEntry[]) =>
  entries.reduce((n, e) => (e.kind === 'event' ? n + 1 : n), 0)

export interface UnauthorizedInfo {
  /** True when at least one request had already succeeded before the 401. */
  everConnected: boolean
}

export interface UseMonitorStreamOptions {
  baseUrl: string
  /** Empty string polls anonymously (public mode); no Authorization header is sent. */
  token: string
  onUnauthorized?: (info: UnauthorizedInfo) => void
}

/**
 * Polls the read-only monitor feed with a setTimeout chain. Remount (via `key`)
 * to start over with a different token; the token never leaves memory.
 */
export function useMonitorStream({
  baseUrl,
  token,
  onUnauthorized,
}: UseMonitorStreamOptions): MonitorStreamState {
  const [state, setState] = useState<MonitorStreamState>(INITIAL)
  const unauthorizedRef = useRef(onUnauthorized)
  useEffect(() => {
    unauthorizedRef.current = onUnauthorized
  })

  useEffect(() => {
    let disposed = false
    let timer: ReturnType<typeof setTimeout> | undefined
    let controller: AbortController | null = null
    let generation = 0
    let inFlight = false
    let terminal = false
    let after = 0
    let bootId: string | null = null
    let epoch = 0
    let sysCounter = 0
    let failures = 0
    let everConnected = false

    const patch = (p: Partial<MonitorStreamState>) => setState((s) => ({ ...s, ...p }))

    const wakeTimer = setTimeout(() => {
      if (!everConnected && !terminal) patch({ wakeHint: true })
    }, SLOW_START_MS)

    const schedule = (ms: number) => {
      clearTimeout(timer)
      timer = setTimeout(poll, ms)
    }

    async function poll() {
      timer = undefined
      if (disposed || terminal || inFlight || document.hidden) return
      const gen = ++generation
      inFlight = true
      controller = new AbortController()
      const result = await fetchMonitorEvents({
        baseUrl,
        token,
        after,
        limit: PAGE_LIMIT,
        signal: controller.signal,
      })
      if (disposed || gen !== generation) return
      inFlight = false
      controller = null

      switch (result.kind) {
        case 'aborted':
          return
        case 'unauthorized':
          terminal = true
          patch({ status: 'unauthorized', retry: null })
          unauthorizedRef.current?.({ everConnected })
          return
        case 'disabled':
          terminal = true
          patch({ status: 'disabled', retry: null, lastError: null })
          return
        case 'error': {
          failures += 1
          const rateLimited = result.status === 429
          // Honour Retry-After for 429 (capped); otherwise the normal exponential backoff.
          const delayMs =
            rateLimited && result.retryAfterMs !== undefined
              ? Math.min(Math.max(result.retryAfterMs, POLL_INTERVAL_MS), RETRY_AFTER_CAP_MS)
              : backoffDelay(failures)
          const coldStartLike = result.status === null || [502, 503, 504].includes(result.status)
          setState((s) => ({
            ...s,
            // A rate limit means the server is reachable: never flip to Disconnected for it.
            status:
              failures >= DISCONNECTED_AFTER_FAILURES && !rateLimited ? 'disconnected' : 'reconnecting',
            retry: { attempt: failures, delayMs },
            lastError: result.message,
            wakeHint: s.wakeHint || (!everConnected && coldStartLike),
          }))
          schedule(delayMs)
          return
        }
        case 'ok': {
          const data = result.data
          const restarted = (bootId !== null && data.bootId !== bootId) || data.latestSeq < after
          if (restarted) {
            // Server restarted: seq space and buffer reset. Drop old lines and refetch from 0.
            const bootChanged = bootId !== null && data.bootId !== bootId
            bootId = data.bootId
            after = 0
            epoch += 1
            failures = 0
            everConnected = true
            sysCounter += 1
            const line: LogEntry = {
              kind: 'system',
              id: `s${sysCounter}`,
              message: bootChanged
                ? 'Backend restarted - server log history was reset. Resuming from the start.'
                : 'Backend sequence went backwards - server log history was reset. Resuming from the start.',
              at: Date.now(),
            }
            setState((s) => ({
              ...s,
              status: 'live',
              entries: [line],
              eventCount: 0,
              latestSeq: data.latestSeq,
              bufferSize: data.bufferSize ?? s.bufferSize,
              lastUpdate: Date.now(),
              retry: null,
              lastError: null,
            }))
            schedule(0)
            return
          }

          bootId = data.bootId
          failures = 0
          everConnected = true
          const sorted = data.events.filter((e) => e.seq > after).sort((a, b) => a.seq - b.seq)
          const unique = sorted.filter((e, i) => i === 0 || e.seq !== sorted[i - 1].seq)
          const evicted = after > 0 && unique.length > 0 ? unique[0].seq - after - 1 : 0
          if (unique.length > 0) after = unique[unique.length - 1].seq
          const currentEpoch = epoch
          const added: LogEntry[] = []
          if (evicted > 0) {
            sysCounter += 1
            added.push({
              kind: 'system',
              id: `s${sysCounter}`,
              message: `${evicted} events were evicted before they could be fetched`,
              at: Date.now(),
            })
          }
          for (const event of unique) {
            added.push({ kind: 'event', id: `e${currentEpoch}-${event.seq}`, event })
          }
          setState((s) => {
            const entries = added.length ? [...s.entries, ...added].slice(-MAX_LINES) : s.entries
            return {
              ...s,
              status: 'live',
              entries,
              eventCount: added.length ? countEvents(entries) : s.eventCount,
              latestSeq: data.latestSeq,
              bufferSize: data.bufferSize ?? s.bufferSize,
              lastUpdate: Date.now(),
              retry: null,
              lastError: null,
              wakeHint: false,
            }
          })
          schedule(unique.length >= PAGE_LIMIT ? 0 : POLL_INTERVAL_MS)
        }
      }
    }

    const onVisibility = () => {
      if (disposed || terminal) return
      if (document.hidden) {
        generation += 1 // discard any in-flight result
        inFlight = false
        controller?.abort()
        controller = null
        clearTimeout(timer)
        timer = undefined
      } else if (!inFlight && timer === undefined) {
        void poll()
      }
    }
    document.addEventListener('visibilitychange', onVisibility)

    if (!document.hidden) void poll()

    return () => {
      disposed = true
      generation += 1
      controller?.abort()
      clearTimeout(timer)
      clearTimeout(wakeTimer)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [baseUrl, token])

  return state
}
