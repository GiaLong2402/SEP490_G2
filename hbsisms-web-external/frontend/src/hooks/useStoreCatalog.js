import { useEffect, useState } from 'react'
import { api } from '@/api/client'
import { productsApi } from '@/api/products'

/**
 * Catalog storefront: GET /categories + GET /products (public).
 */
export function useStoreCatalog({ categoryId, q, pageSize = 50 } = {}) {
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const ac = new AbortController()
    setLoading(true)
    setError(null)

    Promise.all([
      api.get('/categories', null, ac.signal),
      productsApi.list({
        categoryId: categoryId && categoryId !== 'all' ? categoryId : undefined,
        q,
        page: 1,
        pageSize,
      }),
    ])
      .then(([cats, page]) => {
        if (ac.signal.aborted) return
        setCategories(Array.isArray(cats) ? cats : [])
        setProducts(page?.items || [])
      })
      .catch((err) => {
        if (err.name === 'AbortError') return
        setError(err)
        setProducts([])
        setCategories([])
      })
      .finally(() => {
        if (!ac.signal.aborted) setLoading(false)
      })

    return () => ac.abort()
  }, [categoryId, q, pageSize])

  return { products, categories, loading, error }
}
