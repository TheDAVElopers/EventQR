import type { MonitorEvent } from '../types'

export function makeEvent(seq: number, over: Partial<MonitorEvent> = {}): MonitorEvent {
  return {
    seq,
    timestamp: '2026-10-24T10:00:00.123Z',
    operation: 'READ',
    table: 'events',
    entityId: '3f2b7c1e-aaaa-bbbb-cccc-123456789abc',
    result: 'SUCCESS',
    description: `op ${seq}`,
    route: 'GET /api/v1/events',
    actorRole: 'ATTENDEE',
    rowCount: 1,
    ...over,
  }
}

export function body(bootId: string, events: MonitorEvent[], latestSeq?: number) {
  return {
    bootId,
    serverTime: '2026-10-24T10:00:01.000Z',
    latestSeq: latestSeq ?? (events.length ? events[events.length - 1].seq : 0),
    bufferSize: 5000,
    events,
  }
}

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
