import type { LogFilters } from '../filters'
import { OPERATIONS, type KnownOperation } from '../types'

interface Props {
  filters: LogFilters
  onChange: (next: LogFilters) => void
  shown: number
  total: number
}

export function Filters({ filters, onChange, shown, total }: Props) {
  const toggleOp = (op: KnownOperation) => {
    const next = new Set(filters.operations)
    if (next.has(op)) next.delete(op)
    else next.add(op)
    onChange({ ...filters, operations: next })
  }

  return (
    <section className="filters" aria-label="Log filters">
      <div className="chips" role="group" aria-label="Operations">
        {OPERATIONS.map((op) => (
          <button
            key={op}
            type="button"
            className={`chip chip-${op.toLowerCase()}`}
            aria-pressed={filters.operations.has(op)}
            onClick={() => toggleOp(op)}
          >
            {op}
          </button>
        ))}
        <button
          type="button"
          className="chip chip-fail"
          aria-pressed={filters.failuresOnly}
          onClick={() => onChange({ ...filters, failuresOnly: !filters.failuresOnly })}
        >
          Failures only
        </button>
      </div>
      <label className="tablefilter">
        <span>Table</span>
        <input
          type="search"
          value={filters.table}
          placeholder="filter by table name"
          autoComplete="off"
          spellCheck={false}
          onChange={(e) => onChange({ ...filters, table: e.target.value })}
        />
      </label>
      <p className="count" aria-live="off">
        Showing {shown} of {total} events
      </p>
    </section>
  )
}
