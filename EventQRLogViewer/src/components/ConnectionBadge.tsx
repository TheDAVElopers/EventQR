import type { ConnectionStatus, RetryInfo } from '../types'

const LABELS: Record<ConnectionStatus, string> = {
  connecting: 'Connecting',
  live: 'Live',
  reconnecting: 'Reconnecting',
  disconnected: 'Disconnected',
  unauthorized: 'Unauthorized',
  disabled: 'Monitoring disabled',
}

interface Props {
  status: ConnectionStatus
  retry: RetryInfo | null
}

export function ConnectionBadge({ status, retry }: Props) {
  const showRetry = retry && (status === 'reconnecting' || status === 'disconnected')
  return (
    <span className={`conn conn-${status}`} role="status">
      <span className="conn-dot" aria-hidden="true" />
      <span className="conn-label">{LABELS[status]}</span>
      {showRetry ? (
        <span className="conn-retry">
          {' '}
          (retry #{retry.attempt} in {Math.round(retry.delayMs / 100) / 10}s)
        </span>
      ) : null}
    </span>
  )
}
