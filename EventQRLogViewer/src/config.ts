export type ApiConfig =
  | { ok: true; baseUrl: string; host: string }
  | { ok: false; error: string }

/** Validates the configured backend base URL. Pure so it can be tested. */
export function resolveApiConfig(raw: string | undefined): ApiConfig {
  const value = (raw ?? '').trim()
  if (!value) {
    return {
      ok: false,
      error:
        'VITE_API_BASE_URL is not set. Define it in the Vercel project environment (or .env.local) and rebuild.',
    }
  }
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return { ok: false, error: 'VITE_API_BASE_URL must start with http:// or https://.' }
    }
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if (url.protocol === 'http:' && !local) {
      return {
        ok: false,
        error: 'VITE_API_BASE_URL must use https:// (http:// is only allowed for localhost).',
      }
    }
    return { ok: true, baseUrl: value.replace(/\/+$/, ''), host: url.hostname }
  } catch {
    return { ok: false, error: 'VITE_API_BASE_URL is not a valid URL.' }
  }
}
