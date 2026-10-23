import { useMemo, useState } from 'react'
import { DEFAULT_FILTERS, applyFilters, type LogFilters } from '../filters'
import { formatClock } from '../format'
import { useMonitorStream } from '../hooks/useMonitorStream'
import { ConnectionBadge } from './ConnectionBadge'
import { Filters } from './Filters'
import { LogView } from './LogView'

interface Props {
  baseUrl: string
  host: string
  token: string
  onDisconnect: () => void
  onUnauthorized: () => void
}

export function Dashboard({ baseUrl, host, token, onDisconnect, onUnauthorized }: Props) {
  const stream = useMonitorStream({ baseUrl, token, onUnauthorized })
  const [filters, setFilters] = useState<LogFilters>(DEFAULT_FILTERS)

  // Stable reference between polls that add nothing, so the log does not re-render.
  const visible = useMemo(() => applyFilters(stream.entries, filters), [stream.entries, filters])
  const filtering = visible.length === 0 && stream.entries.length > 0

  let placeholder
  if (filtering) {
    placeholder = 'No events match the current filters.'
  } else if (stream.status === 'disabled') {
    placeholder =
      'Monitoring endpoint not found or disabled on the server (HTTP 404). Enable it in the backend configuration, then reconnect.'
  } else if (stream.status === 'connecting') {
    placeholder = 'Connecting to backend...'
  } else if (stream.status === 'reconnecting' || stream.status === 'disconnected') {
    placeholder = `Cannot reach the backend${stream.lastError ? ` (${stream.lastError})` : ''}. Retrying automatically...`
  } else {
    placeholder = 'Waiting for activity... perform an action in the Android app.'
  }

  const showWakeHint = stream.wakeHint && stream.lastUpdate === null
  const offline = stream.status === 'reconnecting' || stream.status === 'disconnected'

  return (
    <>
      <header className="topbar">
        <div className="topbar-main">
          <h1>EventQR Live CRUD Log</h1>
          <ConnectionBadge status={stream.status} retry={stream.retry} />
        </div>
        <dl className="meta">
          <div>
            <dt>Backend</dt>
            <dd>{host}</dd>
          </div>
          <div>
            <dt>Last update</dt>
            <dd>{stream.lastUpdate ? formatClock(stream.lastUpdate) : '-'}</dd>
          </div>
          <div>
            <dt>Events</dt>
            <dd>{stream.eventCount}</dd>
          </div>
        </dl>
        <button type="button" className="btn" onClick={onDisconnect}>
          Disconnect
        </button>
      </header>

      <Filters
        filters={filters}
        onChange={setFilters}
        shown={visible.filter((e) => e.kind === 'event').length}
        total={stream.eventCount}
      />

      {showWakeHint ? (
        <p className="banner banner-info" role="note">
          Render free tier may take ~30&ndash;60s to wake up. Still trying to reach the backend...
        </p>
      ) : null}
      {offline && stream.lastUpdate !== null ? (
        <p className="banner banner-warn" role="note">
          Connection lost{stream.lastError ? ` (${stream.lastError})` : ''}. Showing the last
          received events; retrying automatically.
        </p>
      ) : null}

      <main className="main">
        <LogView entries={visible} placeholder={placeholder} />
      </main>

      <footer className="foot">
        <p>
          Read-only monitor. The server keeps only the most recent
          {stream.bufferSize !== null ? ` ${stream.bufferSize}` : ''} events in memory; history is
          lost when the backend restarts. This page keeps at most 1000 lines.
        </p>
      </footer>
    </>
  )
}
