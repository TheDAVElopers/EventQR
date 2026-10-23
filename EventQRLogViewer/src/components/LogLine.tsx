import { memo } from 'react'
import { formatClock, formatTime, shortId } from '../format'
import { isFailure } from '../filters'
import { isKnownOperation, type LogEntry } from '../types'

export const LogLine = memo(function LogLine({ entry }: { entry: LogEntry }) {
  if (entry.kind === 'system') {
    return (
      <div className="line line-system">
        <span className="ts">{formatClock(entry.at)}</span>
        <span className="sys-tag">SYSTEM</span>
        <span className="desc">{entry.message}</span>
      </div>
    )
  }

  const { event } = entry
  const failed = isFailure(event.result)
  const opClass = isKnownOperation(event.operation) ? `op-${event.operation.toLowerCase()}` : 'op-unknown'
  const route = event.route ? `← ${event.route}` : ''
  const role = event.actorRole ? ` (${event.actorRole})` : ''

  return (
    <div className={`line${failed ? ' line-fail' : ''}`}>
      <span className="ts" title={event.timestamp}>
        {formatTime(event.timestamp)}
      </span>
      <span className={`op ${opClass}`}>{event.operation.slice(0, 8) || '?'}</span>
      <span className="tbl">{event.table}</span>
      <span className="eid" title={event.entityId ?? undefined}>
        {shortId(event.entityId)}
      </span>
      <span className={`res ${failed ? 'res-fail' : 'res-ok'}`}>
        {failed ? 'FAIL' : event.result === 'SUCCESS' ? 'OK' : event.result.slice(0, 8)}
      </span>
      <span className="desc">
        {event.description}
        {event.rowCount !== null ? <span className="rows"> [{event.rowCount} rows]</span> : null}
      </span>
      {route || role ? (
        <span className="src" title={`${event.route ?? ''}${role}`.trim()}>
          {route}
          {role}
        </span>
      ) : null}
    </div>
  )
})
