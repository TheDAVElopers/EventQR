import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { body, jsonResponse, makeEvent } from './test/helpers'

async function loadApp(baseUrl: string | undefined) {
  vi.resetModules()
  vi.stubEnv('VITE_API_BASE_URL', baseUrl ?? '')
  return (await import('./App')).default
}

beforeEach(() => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
  Element.prototype.scrollIntoView = () => {}
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  window.localStorage.clear()
})

const enterToken = (value: string) => {
  fireEvent.change(screen.getByLabelText('Monitor token'), { target: { value } })
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

  it('wrong token returns to the entry screen with an error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 401)))
    const App = await loadApp('https://api.example.com')
    render(<App />)
    expect((screen.getByLabelText('Monitor token') as HTMLInputElement).type).toBe('password')
    enterToken('bad')
    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('401'))
    expect(screen.getByLabelText('Monitor token')).toBeTruthy()
  })

  it('renders events, filters client-side, and Disconnect clears the token', async () => {
    const events = [
      makeEvent(1, { operation: 'CREATE', table: 'registrations', description: 'created reg' }),
      makeEvent(2, { operation: 'DELETE', table: 'rewards', result: 'FAILURE', description: '<b>nope</b>' }),
    ]
    const fetchMock = vi.fn(async () => jsonResponse(body('b1', events)))
    vi.stubGlobal('fetch', fetchMock)
    const App = await loadApp('https://api.example.com')
    render(<App />)
    enterToken('good')

    await waitFor(() => expect(screen.getByText('created reg')).toBeTruthy())
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
    // Only GET requests were made.
    for (const call of fetchMock.mock.calls as unknown as [string, RequestInit][]) {
      expect(call[1].method).toBe('GET')
    }
  })

  it('404 shows the not-found/disabled message and keeps the token session', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 404)))
    const App = await loadApp('https://api.example.com')
    render(<App />)
    enterToken('good')
    await waitFor(() =>
      expect(screen.getByText(/Monitoring endpoint not found or disabled on the server/)).toBeTruthy(),
    )
    expect(screen.getByRole('button', { name: 'Disconnect' })).toBeTruthy()
  })
})
