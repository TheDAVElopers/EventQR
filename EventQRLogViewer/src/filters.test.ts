import { describe, expect, it } from 'vitest'
import { DEFAULT_FILTERS, applyFilters } from './filters'
import { makeEvent } from './test/helpers'
import type { KnownOperation, LogEntry } from './types'

const entry = (seq: number, over = {}): LogEntry => ({
  kind: 'event',
  id: `e0-${seq}`,
  event: makeEvent(seq, over),
})

const entries: LogEntry[] = [
  entry(1, { operation: 'CREATE', table: 'events' }),
  entry(2, { operation: 'READ', table: 'users' }),
  entry(3, { operation: 'DELETE', table: 'events', result: 'FAILURE' }),
  entry(4, { operation: 'MERGE', table: 'misc' }),
  { kind: 'system', id: 's1', message: 'restarted', at: 0 },
]

const ids = (list: LogEntry[]) => list.map((e) => e.id)

describe('applyFilters', () => {
  it('passes everything by default', () => {
    expect(applyFilters(entries, DEFAULT_FILTERS)).toHaveLength(5)
  })

  it('hides toggled-off operations but keeps unknown ops and system lines', () => {
    const operations = new Set<KnownOperation>(['CREATE'])
    expect(ids(applyFilters(entries, { ...DEFAULT_FILTERS, operations }))).toEqual(['e0-1', 'e0-4', 's1'])
  })

  it('failures only', () => {
    expect(ids(applyFilters(entries, { ...DEFAULT_FILTERS, failuresOnly: true }))).toEqual(['e0-3', 's1'])
  })

  it('table text filter is case-insensitive substring', () => {
    expect(ids(applyFilters(entries, { ...DEFAULT_FILTERS, table: ' EVEN ' }))).toEqual(['e0-1', 'e0-3', 's1'])
  })
})
