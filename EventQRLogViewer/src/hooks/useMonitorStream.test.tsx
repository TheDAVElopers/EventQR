import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { body, jsonResponse, makeEvent } from '../test/helpers'
import {
  BACKOFF_CAP_MS,
  DISCONNECTED_AFTER_FAILURES,
  MAX_LINES,
  PAGE_LIMIT,
  POLL_INTERVAL_MS,
  RETRY_AFTER_CAP_MS,
  SLOW_START_MS,
  useMonitorStream,
} from './useMonitorStream'

type Handler = (url: URL) => Response | Promise<Response>

let fetchMock: ReturnType<typeof vi.fn>
const queue: Handler[] = []

const requested = () =>
  fetchMock.mock.calls.map((c) => {
    const u = new URL(String(c[0]))
    return { after: Number(u.searchParams.get('after')), limit: Number(u.searchParams.get('limit')) }
  })

const setHidden = (hidden: boolean) => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
  document.dispatchEvent(new Event('visibilitychange'))
}

const tick = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

const mount = (onUnauthorized?: (info: { everConnected: boolean }) => void, token = 't') =>
  renderHook(() => useMonitorStream({ baseUrl: 'https://api.example.com', token, onUnauthorized }))

beforeEach(() => {
  vi.useFakeTimers()
  queue.length = 0
  fetchMock = vi.fn(async (input: unknown) => {
    const next = queue.shift()
    if (!next) throw new TypeError('no more responses queued')
    return next(new URL(String(input)))
  })
  vi.stubGlobal('fetch', fetchMock)
  setHidden(false)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('useMonitorStream', () => {
  it('first poll uses after=0&limit=100 and goes live', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    const { result } = mount()
    expect(result.current.status).toBe('connecting')
    await tick(0)
    expect(requested()[0]).toEqual({ after: 0, limit: PAGE_LIMIT })
    expect(result.current.status).toBe('live')
    expect(result.current.eventCount).toBe(1)
  })

  it('appends new events in order without duplicates', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1), makeEvent(2)])))
    queue.push(() => jsonResponse(body('b1', [makeEvent(2), makeEvent(3), makeEvent(4)])))
    queue.push(() => jsonResponse(body('b1', [], 4)))
    const { result } = mount()
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    await tick(POLL_INTERVAL_MS)
    const seqs = result.current.entries.map((e) => (e.kind === 'event' ? e.event.seq : -1))
    expect(seqs).toEqual([1, 2, 3, 4])
    expect(requested().map((r) => r.after)).toEqual([0, 2, 4])
  })

  it('detects a bootId change: system line, cleared log, restart from after=0', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1), makeEvent(2), makeEvent(3)])))
    queue.push(() => jsonResponse(body('b2', [makeEvent(1)])))
    queue.push(() => jsonResponse(body('b2', [makeEvent(1, { description: 'after restart' }), makeEvent(2)])))
    const { result } = mount()
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    expect(result.current.entries).toHaveLength(1)
    expect(result.current.entries[0]).toMatchObject({ kind: 'system' })
    // Immediate refetch from 0 against the new boot.
    await tick(1)
    expect(requested().map((r) => r.after)).toEqual([0, 3, 0])
    const kinds = result.current.entries.map((e) => e.kind)
    expect(kinds).toEqual(['system', 'event', 'event'])
    expect(result.current.eventCount).toBe(2)
    expect(result.current.status).toBe('live')
  })

  it('401 -> unauthorized, notifies once and stops polling', async () => {
    queue.push(() => jsonResponse({}, 401))
    const onUnauthorized = vi.fn()
    const { result } = mount(onUnauthorized)
    await tick(0)
    expect(result.current.status).toBe('unauthorized')
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    await tick(60_000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('404 -> disabled and stops polling', async () => {
    queue.push(() => jsonResponse({}, 404))
    const { result } = mount()
    await tick(0)
    expect(result.current.status).toBe('disabled')
    await tick(60_000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('network error -> reconnecting with exponential backoff, then recovers', async () => {
    const fail: Handler = () => {
      throw new TypeError('Failed to fetch')
    }
    queue.push(fail, fail, fail)
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    const { result } = mount()
    await tick(0)
    expect(result.current.status).toBe('reconnecting')
    expect(result.current.retry).toEqual({ attempt: 1, delayMs: 3000 })
    expect(result.current.wakeHint).toBe(true)

    await tick(2999)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await tick(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(result.current.retry).toEqual({ attempt: 2, delayMs: 6000 })

    await tick(6000)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(result.current.retry).toEqual({ attempt: 3, delayMs: BACKOFF_CAP_MS })

    await tick(BACKOFF_CAP_MS)
    expect(result.current.status).toBe('live')
    expect(result.current.retry).toBeNull()
  })

  it('flips to disconnected after repeated failures and caps the backoff', async () => {
    for (let i = 0; i < DISCONNECTED_AFTER_FAILURES + 2; i++) {
      queue.push(() => jsonResponse({}, 503))
    }
    const { result } = mount()
    for (let i = 0; i < DISCONNECTED_AFTER_FAILURES + 1; i++) await tick(BACKOFF_CAP_MS)
    expect(result.current.status).toBe('disconnected')
    expect(result.current.retry?.delayMs).toBe(BACKOFF_CAP_MS)
    expect(result.current.wakeHint).toBe(true)
  })

  it('shows the cold-start hint when the first request is slow', async () => {
    queue.push(() => new Promise<Response>(() => {}))
    const { result } = mount()
    await tick(SLOW_START_MS - 1)
    expect(result.current.wakeHint).toBe(false)
    await tick(1)
    expect(result.current.wakeHint).toBe(true)
  })

  it('keeps at most MAX_LINES lines, dropping the oldest', async () => {
    const batch = (from: number) =>
      jsonResponse(body('b1', Array.from({ length: PAGE_LIMIT }, (_, i) => makeEvent(from + i))))
    const BATCHES = 11
    for (let i = 0; i < BATCHES; i++) queue.push(() => batch(1 + i * PAGE_LIMIT))
    queue.push(() => jsonResponse(body('b1', [], BATCHES * PAGE_LIMIT)))
    const { result } = mount()
    for (let i = 0; i < BATCHES + 1; i++) await tick(1)
    expect(result.current.entries).toHaveLength(MAX_LINES)
    const first = result.current.entries[0]
    expect(first.kind === 'event' && first.event.seq).toBe(BATCHES * PAGE_LIMIT - MAX_LINES + 1)
  })

  it('pauses polling while the tab is hidden and resumes on visible', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    queue.push(() => jsonResponse(body('b1', [makeEvent(2)])))
    queue.push(() => jsonResponse(body('b1', [makeEvent(3)])))
    mount()
    await tick(0)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    act(() => setHidden(true))
    await tick(30_000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    act(() => setHidden(false))
    await tick(0)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(requested()[1].after).toBe(1)
  })

  it('stops polling on unmount', async () => {
    queue.push(() => jsonResponse(body('b1', [])))
    const { unmount } = mount()
    await tick(0)
    unmount()
    await tick(30_000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
  it('adds a system line when events were evicted before they could be fetched', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1), makeEvent(2)])))
    queue.push(() => jsonResponse(body('b1', [makeEvent(10), makeEvent(11)])))
    const { result } = mount()
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    const kinds = result.current.entries.map((e) => e.kind)
    expect(kinds).toEqual(['event', 'event', 'system', 'event', 'event'])
    const sys = result.current.entries[2]
    expect(sys.kind === 'system' && sys.message).toBe('7 events were evicted before they could be fetched')
    expect(result.current.eventCount).toBe(4)
  })

  it('does not report a gap on the first poll or for contiguous events', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(50), makeEvent(51)])))
    queue.push(() => jsonResponse(body('b1', [makeEvent(52)])))
    const { result } = mount()
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    expect(result.current.entries.every((e) => e.kind === 'event')).toBe(true)
  })

  it('resets when latestSeq goes below after within the same bootId', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1), makeEvent(2), makeEvent(3)])))
    queue.push(() => jsonResponse(body('b1', [], 1)))
    queue.push(() => jsonResponse(body('b1', [makeEvent(1, { description: 'fresh' })])))
    const { result } = mount()
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    expect(result.current.entries).toHaveLength(1)
    expect(result.current.entries[0].kind).toBe('system')
    await tick(1)
    expect(requested().map((r) => r.after)).toEqual([0, 3, 0])
    expect(result.current.entries.map((e) => e.kind)).toEqual(['system', 'event'])
  })

  it('de-duplicates repeated seq values inside one page and sorts them', async () => {
    queue.push(() =>
      jsonResponse(body('b1', [makeEvent(2), makeEvent(1), makeEvent(2), makeEvent(3)], 3)),
    )
    const { result } = mount()
    await tick(0)
    const seqs = result.current.entries.map((e) => (e.kind === 'event' ? e.event.seq : -1))
    expect(seqs).toEqual([1, 2, 3])
  })

  it('discards a stale in-flight response when the tab is hidden, then refetches the same range', async () => {
    let release: (r: Response) => void = () => {}
    queue.push(() => new Promise<Response>((resolve) => (release = resolve)))
    queue.push(() => jsonResponse(body('b1', [makeEvent(1, { description: 'fresh' })])))
    const { result } = mount()
    await tick(0)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    act(() => setHidden(true))
    // Late response for the pre-hide request arrives while hidden.
    await act(async () => {
      release(jsonResponse(body('b1', [makeEvent(1, { description: 'stale' })])))
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(result.current.entries).toHaveLength(0)
    expect(result.current.status).toBe('connecting')
    act(() => setHidden(false))
    await tick(0)
    expect(requested().map((r) => r.after)).toEqual([0, 0])
    const first = result.current.entries[0]
    expect(first.kind === 'event' && first.event.description).toBe('fresh')
    expect(result.current.entries).toHaveLength(1)
  })

  it('keeps the entries reference stable when a poll adds nothing', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    queue.push(() => jsonResponse(body('b1', [], 1)))
    const { result } = mount()
    await tick(0)
    const before = result.current.entries
    await tick(POLL_INTERVAL_MS)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(result.current.entries).toBe(before)
  })

  it('401 through the real client path stops polling and keeps the token out of state', async () => {
    queue.push((url) => {
      expect(url.pathname).toBe('/api/v1/monitor/events')
      return jsonResponse({ error: 'nope' }, 401)
    })
    const onUnauthorized = vi.fn()
    const { result } = mount(onUnauthorized)
    await tick(0)
    expect(result.current.status).toBe('unauthorized')
    expect(JSON.stringify(result.current)).not.toContain('"t"')
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('polls anonymously without an Authorization header when the token is empty', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    const { result } = mount(undefined, '')
    await tick(0)
    expect(result.current.status).toBe('live')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(Object.keys(init.headers as object).map((k) => k.toLowerCase())).not.toContain('authorization')
  })

  it('reports everConnected=false for a 401 on the first anonymous probe', async () => {
    queue.push(() => jsonResponse({}, 401))
    const onUnauthorized = vi.fn()
    mount(onUnauthorized, '')
    await tick(0)
    expect(onUnauthorized).toHaveBeenCalledWith({ everConnected: false })
  })

  it('reports everConnected=true for a 401 after a successful response', async () => {
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    queue.push(() => jsonResponse({}, 401))
    const onUnauthorized = vi.fn()
    mount(onUnauthorized, '')
    await tick(0)
    await tick(POLL_INTERVAL_MS)
    expect(onUnauthorized).toHaveBeenCalledWith({ everConnected: true })
  })

  const rateLimited = (retryAfter?: string): Handler => () =>
    new Response('{}', { status: 429, headers: retryAfter ? { 'Retry-After': retryAfter } : {} })

  it('429 with Retry-After: Reconnecting, waits the advertised seconds, then recovers', async () => {
    queue.push(rateLimited('7'))
    queue.push(() => jsonResponse(body('b1', [makeEvent(1)])))
    const onUnauthorized = vi.fn()
    const { result } = mount(onUnauthorized)
    await tick(0)
    expect(result.current.status).toBe('reconnecting')
    expect(result.current.retry).toEqual({ attempt: 1, delayMs: 7000 })
    expect(result.current.wakeHint).toBe(false)
    await tick(6999)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await tick(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(result.current.status).toBe('live')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('caps an excessive Retry-After at 30s', async () => {
    queue.push(rateLimited('120'))
    const { result } = mount()
    await tick(0)
    expect(result.current.retry?.delayMs).toBe(RETRY_AFTER_CAP_MS)
  })

  it('429 without Retry-After uses the normal backoff', async () => {
    queue.push(rateLimited())
    queue.push(rateLimited())
    const { result } = mount()
    await tick(0)
    expect(result.current.status).toBe('reconnecting')
    expect(result.current.retry).toEqual({ attempt: 1, delayMs: 3000 })
    await tick(3000)
    expect(result.current.retry).toEqual({ attempt: 2, delayMs: 6000 })
  })

  it('never shows Disconnected for repeated 429s', async () => {
    for (let i = 0; i < DISCONNECTED_AFTER_FAILURES + 2; i++) queue.push(rateLimited('1'))
    const { result } = mount()
    for (let i = 0; i < DISCONNECTED_AFTER_FAILURES + 1; i++) await tick(POLL_INTERVAL_MS)
    expect(fetchMock.mock.calls.length).toBeGreaterThan(DISCONNECTED_AFTER_FAILURES)
    expect(result.current.status).toBe('reconnecting')
  })
})
