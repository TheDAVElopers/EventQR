import { useState } from 'react'
import { Dashboard } from './components/Dashboard'
import { TokenGate } from './components/TokenGate'
import { resolveApiConfig } from './config'

const config = resolveApiConfig(import.meta.env.VITE_API_BASE_URL as string | undefined)

export default function App() {
  // The token lives only in this component's state (memory).
  const [token, setToken] = useState<string | null>(null)
  const [authError, setAuthError] = useState<string | null>(null)

  if (!config.ok) {
    return (
      <main className="main main-center">
        <div className="gate">
          <h1>Configuration error</h1>
          <p className="error" role="alert">
            {config.error}
          </p>
        </div>
      </main>
    )
  }

  if (token === null) {
    return (
      <main className="main main-center">
        <h1 className="gate-title">EventQR Live CRUD Log</h1>
        <TokenGate
          host={config.host}
          error={authError}
          onSubmit={(t) => {
            setAuthError(null)
            setToken(t)
          }}
        />
      </main>
    )
  }

  return (
    <div className="app">
      <Dashboard
        key={token}
        baseUrl={config.baseUrl}
        host={config.host}
        token={token}
        onDisconnect={() => {
          setAuthError(null)
          setToken(null)
        }}
        onUnauthorized={() => {
          setAuthError('The backend rejected this token (401 Unauthorized). Check it and try again.')
          setToken(null)
        }}
      />
    </div>
  )
}
