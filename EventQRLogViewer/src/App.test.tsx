import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { body, jsonResponse, makeEvent } from './test/helpers'

async function loadApp(baseUrl: string | undefined) {
  vi.resetModules()
  vi.stubEnv('VITE_API_BASE_URL', baseUrl ?? '')
  return (await import('./App')).default
}

type FetchArgs = [string, RequestInit]

const authHeader = (init: RequestInit): string | undefined => {
  const headers = init.headers as Record<string, string> | undefined
  return headers ? headers.Authorization : undefined
}

/** Server stub: public mode accepts anonymous calls; otherwise requires the exact token. */
function serverStub(opts: { publicMode: boolean; token?: string; events?: ReturnType<typeof makeEvent>[] }) {
  return vi.fn(async (_url: unknown, init: RequestInit) => {
    const auth = authHeader(init)
    if (!opts.publicMode && auth !== `Bearer ${opts.token}`) return jsonResponse({}, 401)
    if (auth && opts.publicMode && auth !== `Bearer ${opts.token}`) return jsonResponse({}, 401)
    return jsonResponse(body('b1', opts.events ?? []))
  })
}

beforeEach(() => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  window.localStorage.clear()
})

const enterToken = async (value: string) => {
  fireEvent.change(await screen.findByLabelText('Monitor token'), { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: 'Connect' }))
}

describe('App', () => {
  it('shows a configuration error and never fetches when the base URL is unset', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const App = await loadApp(undefined)
    render(<App />)
    expect(screen.getByRole('alert').textContent).toContain('VITE_API_BASE_URL')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  describe('public mode (anonymous 200)', () => {
    it('goes straight to the dashboard without a token screen or Authorization header', async () => {
      const fetchMock = serverStub({
        publicMode: true,
        events: [makeEvent(1, { description: 'public row' })],
      })
      vi.stubGlobal('fetch', fetchMock)
      const App = await loadApp('https://api.example.com')
      render(<App />)

      await waitFor(() => expect(screen.getByText('public row')).toBeTruthy())
      expect(screen.queryByLabelText('Monitor token')).toBeNull()
      expect(screen.queryByRole('button', { name: 'Disconnect' })).toBeNull()
      expect(screen.getAllByText(/Public read-only feed/).length).toBeGreaterThan(0)
      expect(screen.getByText('api.example.com')).toBeTruthy()

      const calls = fetchMock.mock.calls as unknown as FetchArgs[]
      expect(calls.length).toBeGreaterThan(0)
      for (const [url, init] of calls) {
        expect(init.method).toBe('GET')
        expect(authHeader(init)).toBeUndefined()
        expect(url).not.toContain('token')
      }
      expect(window.localStorage.length).toBe(0)
      expect(window.sessionStorage.length).toBe(0)
    })

    it('401 after an anonymous success shows the gate with "Access now requires a token"', async () => {
      let calls = 0
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => {
          calls += 1
          return calls === 1
            ? jsonResponse(body('b1', [makeEvent(1, { description: 'public row' })]))
            : jsonResponse({}, 401)
        }),
      )
      const App = await loadApp('https://api.example.com')
      render(<App />)
      await waitFor(() => expect(screen.getByText('public row')).toBeTruthy())
      await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('Access now requires a token'), {
        timeout: 4000,
      })
      expect(screen.getByLabelText('Monitor token')).toBeTruthy()
    })
  })

  describe('private mode (anonymous 401)', () => {
    it('anonymous 401 shows the token gate without an error', async () => {
      const fetchMock = serverStub({ publicMode: false, token: 'good' })
      vi.stubGlobal('fetch', fetchMock)
      const App = await loadApp('https://api.example.com')
      render(<App />)
      expect((await screen.findByLabelText('Monitor token') as HTMLInputElement).type).toBe('password')
      expect(screen.queryByRole('alert')).toBeNull()
      const first = (fetchMock.mock.calls as unknown as FetchArgs[])[0]
      expect(authHeader(first[1])).toBeUndefined()
    })

    it('wrong token returns to the entry screen with an error', async () => {
      vi.stubGlobal('fetch', serverStub({ publicMode: false, token: 'good' }))
      const App = await loadApp('https://api.example.com')
      render(<App />)
      await enterToken('bad')
      await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('401'))
      expect(screen.getByLabelText('Monitor token')).toBeTruthy()
    })

    it('renders events, filters client-side, and Disconnect clears the token', async () => {
      const events = [
        makeEvent(1, { operation: 'CREATE', table: 'registrations', description: 'created reg' }),
        makeEvent(2, { operation: 'DELETE', table: 'rewards', result: 'FAILURE', description: '<b>nope</b>' }),
      ]
      const fetchMock = serverStub({ publicMode: false, token: 'good', events })
      vi.stubGlobal('fetch', fetchMock)
      const App = await loadApp('https://api.example.com')
      render(<App />)
      await enterToken('good')

      await waitFor(() => expect(screen.getByText('created reg')).toBeTruthy())
      expect(screen.queryByText(/Public read-only feed/)).toBeNull()
      expect(screen.getByText('api.example.com')).toBeTruthy()
      expect(screen.getByRole('log').getAttribute('aria-live')).toBe('off')
      // Strings are rendered as text, not HTML.
      expect(screen.getByText('<b>nope</b>')).toBeTruthy()
      expect(screen.getByText('FAIL')).toBeTruthy()

      fireEvent.click(screen.getByRole('button', { name: 'Failures only' }))
      expect(screen.queryByText('created reg')).toBeNull()
      expect(screen.getByText('<b>nope</b>')).toBeTruthy()

      const del = screen.getByRole('button', { name: 'DELETE' })
      expect(del.getAttribute('aria-pressed')).toBe('true')
      fireEvent.click(del)
      expect(del.getAttribute('aria-pressed')).toBe('false')
      expect(screen.getByText('No events match the current filters.')).toBeTruthy()

      // Token is never persisted.
      expect(window.localStorage.length).toBe(0)
      expect(window.sessionStorage.length).toBe(0)

      fireEvent.click(screen.getByRole('button', { name: 'Disconnect' }))
      expect(screen.getByLabelText('Monitor token')).toBeTruthy()
      // Only GET requests; Authorization only after a token was entered.
      const calls = fetchMock.mock.calls as unknown as FetchArgs[]
      expect(authHeader(calls[0][1])).toBeUndefined()
      expect(calls.some(([, init]) => authHeader(init) === 'Bearer good')).toBe(true)
      for (const [url, init] of calls) {
        expect(init.method).toBe('GET')
        expect(url).not.toContain('good')
      }
    })
  })

  it('a network error during the initial anonymous probe does not show the gate', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )
    const App = await loadApp('https://api.example.com')
    render(<App />)
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Reconnecting'))
    expect(screen.queryByLabelText('Monitor token')).toBeNull()
  })

  it('404 shows the not-found/disabled message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 404)))
    const App = await loadApp('https://api.example.com')
    render(<App />)
    await waitFor(() =>
      expect(screen.getByText(/Monitoring endpoint not found or disabled on the server/)).toBeTruthy(),
    )
    expect(screen.queryByLabelText('Monitor token')).toBeNull()
  })

  it('a 429 on the initial probe keeps Reconnecting and does not show the gate', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 429, headers: { 'Retry-After': '2' } })),
    )
    const App = await loadApp('https://api.example.com')
    render(<App />)
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Reconnecting'))
    expect(screen.queryByLabelText('Monitor token')).toBeNull()
  })
})
