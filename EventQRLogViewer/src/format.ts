const pad = (n: number, width = 2) => String(n).padStart(width, '0')

/** Local HH:mm:ss.SSS, or a placeholder for unparsable timestamps. */
export function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '--:--:--.---'
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

/** Local HH:mm:ss for epoch millis. */
export function formatClock(epochMs: number): string {
  const d = new Date(epochMs)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function shortId(id: string | null): string {
  if (!id) return '-'
  return id.length > 8 ? id.slice(0, 8) : id
}
