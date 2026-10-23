import { describe, expect, it } from 'vitest'
import { resolveApiConfig } from './config'

describe('resolveApiConfig', () => {
  it('reports a configuration error when unset or blank', () => {
    expect(resolveApiConfig(undefined).ok).toBe(false)
    expect(resolveApiConfig('   ').ok).toBe(false)
  })

  it('rejects invalid and non-http URLs', () => {
    expect(resolveApiConfig('not a url').ok).toBe(false)
    expect(resolveApiConfig('javascript:alert(1)').ok).toBe(false)
  })

  it('strips trailing slashes and exposes the hostname only', () => {
    expect(resolveApiConfig('https://svc.onrender.com:443/base//')).toEqual({
      ok: true,
      baseUrl: 'https://svc.onrender.com:443/base',
      host: 'svc.onrender.com',
    })
  })

  it('requires https except for localhost and 127.0.0.1', () => {
    expect(resolveApiConfig('http://svc.onrender.com').ok).toBe(false)
    expect(resolveApiConfig('http://192.168.1.5:8080').ok).toBe(false)
    expect(resolveApiConfig('http://localhost:8080').ok).toBe(true)
    expect(resolveApiConfig('http://127.0.0.1:8080').ok).toBe(true)
    expect(resolveApiConfig('https://svc.onrender.com').ok).toBe(true)
  })
})
