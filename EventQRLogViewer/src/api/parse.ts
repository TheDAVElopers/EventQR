import type { MonitorEvent, MonitorResponse } from '../types'

const isRecord = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

const strOrNull = (v: unknown): string | null =>
  typeof v === 'string' ? v : typeof v === 'number' ? String(v) : null

const numOrNull = (v: unknown): number | null =>
  typeof v === 'number' && Number.isFinite(v) ? v : null

export function parseEvent(raw: unknown): MonitorEvent | null {
  if (!isRecord(raw)) return null
  const { seq } = raw
  if (typeof seq !== 'number' || !Number.isSafeInteger(seq) || seq < 0) return null
  if (typeof raw.operation !== 'string' || typeof raw.table !== 'string') return null
  return {
    seq,
    timestamp: typeof raw.timestamp === 'string' ? raw.timestamp : '',
    operation: raw.operation.trim().toUpperCase(),
    table: raw.table,
    entityId: strOrNull(raw.entityId),
    result: typeof raw.result === 'string' ? raw.result.trim().toUpperCase() : 'UNKNOWN',
    description: typeof raw.description === 'string' ? raw.description : '',
    route: strOrNull(raw.route),
    actorRole: strOrNull(raw.actorRole),
    rowCount: numOrNull(raw.rowCount),
  }
}

/** Defensive validation of the monitor response; null when the shape is unusable. */
export function parseMonitorResponse(raw: unknown): MonitorResponse | null {
  if (!isRecord(raw)) return null
  if (typeof raw.bootId !== 'string' || raw.bootId === '') return null
  if (typeof raw.latestSeq !== 'number' || !Number.isFinite(raw.latestSeq)) return null
  if (!Array.isArray(raw.events)) return null
  const events: MonitorEvent[] = []
  for (const item of raw.events) {
    const ev = parseEvent(item)
    if (ev) events.push(ev)
  }
  return {
    bootId: raw.bootId,
    serverTime: typeof raw.serverTime === 'string' ? raw.serverTime : null,
    latestSeq: raw.latestSeq,
    bufferSize: numOrNull(raw.bufferSize),
    events,
  }
}
