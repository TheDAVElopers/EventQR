import { afterEach, describe, expect, it, vi } from 'vitest'
import { body, jsonResponse, makeEvent } from '../test/helpers'
import { fetchMonitorEvents, parseRetryAfter } from './monitorClient'

const base = { baseUrl: 'https://api.example.com', token: 'secret-token', after: 7, limit: 100 }

afterEach(() => {
  vi.useRealTimers()
})

describe('fetchMonitorEvents', () => {
  it('issues a read-only GET with bearer auth and query params', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse(body('b1', [makeEvent(8)])))
    const out = await fetchMonitorEvents({ ...base, fetchImpl })
    expect(out.kind).toBe('ok')
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://api.example.com/api/v1/monitor/events?after=7&limit=100')
    expect(init.method).toBe('GET')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer secret-token')
    expect(init.body).toBeUndefined()
  })

  it('returns parsed events in order and drops malformed ones', async () => {
    const raw = body('b1', [makeEvent(1), makeEvent(2)])
    ;(raw.events as unknown[]).push({ seq: 'x' }, null, { seq: 3, operation: 'CREATE' })
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse(raw) })
    expect(out.kind).toBe('ok')
    if (out.kind === 'ok') expect(out.data.events.map((e) => e.seq)).toEqual([1, 2])
  })

  it('keeps unknown operations as-is (uppercased) for neutral rendering', async () => {
    const raw = body('b1', [makeEvent(1, { operation: 'merge' })])
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse(raw) })
    if (out.kind === 'ok') expect(out.data.events[0].operation).toBe('MERGE')
    else throw new Error('expected ok')
  })

  it('maps 401 to unauthorized', async () => {
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse({}, 401) })
    expect(out).toEqual({ kind: 'unauthorized' })
  })

  it('maps 404 to disabled', async () => {
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse({}, 404) })
    expect(out).toEqual({ kind: 'disabled' })
  })

  it('maps 503 to an error carrying the status', async () => {
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse({}, 503) })
    expect(out).toMatchObject({ kind: 'error', status: 503 })
  })

  it('maps a network failure to an error without leaking the token', async () => {
    const out = await fetchMonitorEvents({
      ...base,
      fetchImpl: async () => {
        throw new TypeError('Failed to fetch')
      },
    })
    expect(out).toMatchObject({ kind: 'error', status: null })
    expect(JSON.stringify(out)).not.toContain('secret-token')
  })

  it('rejects an unexpected response shape', async () => {
    const out = await fetchMonitorEvents({
      ...base,
      fetchImpl: async () => jsonResponse({ hello: 'world' }),
    })
    expect(out).toMatchObject({ kind: 'error', message: 'Unexpected response format' })
  })

  it('rejects non-JSON bodies', async () => {
    const out = await fetchMonitorEvents({
      ...base,
      fetchImpl: async () => new Response('<html>', { status: 200 }),
    })
    expect(out).toMatchObject({ kind: 'error' })
  })

  it('reports aborted when the caller aborts', async () => {
    const controller = new AbortController()
    const fetchImpl = vi.fn(
      (_url: unknown, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('x', 'AbortError')))
        }),
    )
    const pending = fetchMonitorEvents({ ...base, fetchImpl: fetchImpl as unknown as typeof fetch, signal: controller.signal })
    controller.abort()
    expect(await pending).toEqual({ kind: 'aborted' })
  })

  it('times out hanging requests as an error', async () => {
    vi.useFakeTimers()
    const fetchImpl = vi.fn(
      (_url: unknown, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('x', 'AbortError')))
        }),
    )
    const pending = fetchMonitorEvents({ ...base, fetchImpl: fetchImpl as unknown as typeof fetch, timeoutMs: 1000 })
    await vi.advanceTimersByTimeAsync(1000)
    expect(await pending).toMatchObject({ kind: 'error', message: 'Request timed out' })
  })

  it('sends no Authorization header at all when the token is empty (anonymous)', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse(body('b1', [makeEvent(1)])))
    const out = await fetchMonitorEvents({ ...base, token: '', fetchImpl })
    expect(out.kind).toBe('ok')
    const init = (fetchImpl.mock.calls[0] as unknown as [string, RequestInit])[1]
    const headers = init.headers as Record<string, string>
    expect(Object.keys(headers).map((k) => k.toLowerCase())).not.toContain('authorization')
    expect(init.method).toBe('GET')
  })

  it('429 with Retry-After surfaces a transient error carrying the delay', async () => {
    const out = await fetchMonitorEvents({
      ...base,
      fetchImpl: async () => new Response('{}', { status: 429, headers: { 'Retry-After': '7' } }),
    })
    expect(out).toMatchObject({ kind: 'error', status: 429, retryAfterMs: 7000 })
  })

  it('429 without Retry-After has no delay hint', async () => {
    const out = await fetchMonitorEvents({ ...base, fetchImpl: async () => jsonResponse({}, 429) })
    expect(out).toMatchObject({ kind: 'error', status: 429 })
    expect(out).not.toHaveProperty('retryAfterMs')
  })

  it('parseRetryAfter accepts delta-seconds only', () => {
    expect(parseRetryAfter('3')).toBe(3000)
    expect(parseRetryAfter('1.5')).toBe(1500)
    expect(parseRetryAfter(null)).toBeUndefined()
    expect(parseRetryAfter('Wed, 21 Oct 2026 07:28:00 GMT')).toBeUndefined()
  })
})
