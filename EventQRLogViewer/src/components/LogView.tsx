import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import type { LogEntry } from '../types'
import { LogLine } from './LogLine'

const STICK_THRESHOLD_PX = 24

interface Props {
  entries: LogEntry[]
  /** Shown instead of the log when there is nothing to display. */
  placeholder: ReactNode
}

export function LogView({ entries, placeholder }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const [following, setFollowing] = useState(true)
  const followingRef = useRef(true)

  const setFollow = useCallback((value: boolean) => {
    followingRef.current = value
    setFollowing(value)
  }, [])

  const scrollToBottom = useCallback(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [])

  useLayoutEffect(() => {
    if (followingRef.current) scrollToBottom()
  }, [entries, scrollToBottom])

  const onScroll = () => {
    const el = scrollRef.current
    if (!el) return
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= STICK_THRESHOLD_PX
    if (atBottom !== followingRef.current) setFollow(atBottom)
  }

  const resume = () => {
    setFollow(true)
    scrollToBottom()
    scrollRef.current?.focus({ preventScroll: true })
  }

  return (
    <div className="logwrap">
      <div className="logbar">
        <button
          type="button"
          className="btn btn-small"
          data-paused={!following}
          onClick={() => (following ? setFollow(false) : resume())}
        >
          {following ? 'Pause auto-scroll' : 'Resume auto-scroll'}
        </button>
      </div>
      <div
        ref={scrollRef}
        className="log"
        role="log"
        aria-live="off"
        aria-label="Database operations log"
        tabIndex={0}
        onScroll={onScroll}
      >
        {entries.length === 0 ? (
          <div className="placeholder">{placeholder}</div>
        ) : (
          entries.map((entry) => <LogLine key={entry.id} entry={entry} />)
        )}
      </div>
      {!following ? (
        <button type="button" className="btn jump" onClick={resume}>
          Jump to latest &darr;
        </button>
      ) : null}
    </div>
  )
}
