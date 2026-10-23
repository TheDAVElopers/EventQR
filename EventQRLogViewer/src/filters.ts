import { isKnownOperation, type KnownOperation, type LogEntry } from './types'

export interface LogFilters {
  operations: ReadonlySet<KnownOperation>
  failuresOnly: boolean
  table: string
}

export const DEFAULT_FILTERS: LogFilters = {
  operations: new Set<KnownOperation>(['CREATE', 'READ', 'UPDATE', 'DELETE']),
  failuresOnly: false,
  table: '',
}

export function isFailure(result: string): boolean {
  return result === 'FAILURE'
}

/** Client-side filtering. System lines (restart notices) always pass. */
export function applyFilters(entries: LogEntry[], filters: LogFilters): LogEntry[] {
  const needle = filters.table.trim().toLowerCase()
  return entries.filter((entry) => {
    if (entry.kind === 'system') return true
    const { event } = entry
    if (isKnownOperation(event.operation) && !filters.operations.has(event.operation)) return false
    if (filters.failuresOnly && !isFailure(event.result)) return false
    if (needle && !event.table.toLowerCase().includes(needle)) return false
    return true
  })
}
