export const OPERATIONS = ['CREATE', 'READ', 'UPDATE', 'DELETE'] as const
export type KnownOperation = (typeof OPERATIONS)[number]

export function isKnownOperation(value: string): value is KnownOperation {
  return (OPERATIONS as readonly string[]).includes(value)
}

/** One database operation reported by the backend monitor. */
export interface MonitorEvent {
  seq: number
  timestamp: string
  /** Normally one of OPERATIONS; kept as string so unknown values render neutrally. */
  operation: string
  table: string
  entityId: string | null
  /** Normally SUCCESS or FAILURE. */
  result: string
  description: string
  route: string | null
  actorRole: string | null
  rowCount: number | null
}

export interface MonitorResponse {
  bootId: string
  serverTime: string | null
  latestSeq: number
  bufferSize: number | null
  events: MonitorEvent[]
}

export type ConnectionStatus =
  | 'connecting'
  | 'live'
  | 'reconnecting'
  | 'disconnected'
  | 'unauthorized'
  | 'disabled'

export type LogEntry =
  | { kind: 'event'; id: string; event: MonitorEvent }
  | { kind: 'system'; id: string; message: string; at: number }

export interface RetryInfo {
  attempt: number
  delayMs: number
}
