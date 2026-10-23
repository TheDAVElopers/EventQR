const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined

export default function App() {
  return (
    <main style={{ padding: 16 }}>
      <h1 style={{ fontSize: 18 }}>EventQR Live CRUD Log Viewer</h1>
      <p style={{ color: 'var(--muted)' }}>
        Read-only monitor. Backend: {API_BASE_URL ?? 'VITE_API_BASE_URL not set'}
      </p>
    </main>
  )
}
