import { useState, type FormEvent } from 'react'

interface Props {
  host: string
  error: string | null
  onSubmit: (token: string) => void
}

export function TokenGate({ host, error, onSubmit }: Props) {
  const [value, setValue] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const token = value.trim()
    if (!token) return
    setValue('')
    onSubmit(token)
  }

  return (
    <form className="gate" onSubmit={submit}>
      <h2>Connect to monitor</h2>
      <p className="muted">
        Read-only view of database operations on <strong>{host}</strong>. The token is kept in
        memory only and is forgotten on reload or Disconnect.
      </p>
      {error ? (
        <p className="error" role="alert">
          {error}
        </p>
      ) : null}
      <label htmlFor="monitor-token">Monitor token</label>
      <input
        id="monitor-token"
        type="password"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        autoComplete="off"
        autoCapitalize="off"
        spellCheck={false}
        autoFocus
        required
      />
      <button type="submit" className="btn btn-primary">
        Connect
      </button>
    </form>
  )
}
