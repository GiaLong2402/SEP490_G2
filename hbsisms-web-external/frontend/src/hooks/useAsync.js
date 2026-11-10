import { useCallback, useEffect, useState } from 'react'

/**
 * Tải dữ liệu bất đồng bộ cho trang: { data, loading, error, reload, setData }.
 * fn nhận AbortSignal; deps đổi → tải lại (hủy request cũ).
 *
 *   const { data, loading, error, reload } = useAsync((signal) => ordersApi.listMine(signal), [])
 */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    const ac = new AbortController()
    let alive = true
    Promise.resolve()
      .then(() => {
        if (alive) setState((s) => ({ ...s, loading: true, error: null }))
        return fn(ac.signal)
      })
      .then((data) => {
        if (alive) setState({ data, loading: false, error: null })
      })
      .catch((error) => {
        if (!alive || error?.name === 'AbortError') return
        setState({ data: null, loading: false, error })
      })
    return () => {
      alive = false
      ac.abort()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  const setData = useCallback((updater) => {
    setState((s) => ({ ...s, data: typeof updater === 'function' ? updater(s.data) : updater }))
  }, [])

  return { ...state, reload, setData }
}
