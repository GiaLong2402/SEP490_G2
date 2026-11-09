import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Breadcrumbs from '@/components/storefront/Breadcrumbs/Breadcrumbs'
import ProductCard from '@/components/storefront/ProductCard/ProductCard'
import { ROUTES } from '@/constants/routes'
import { useCart } from '@/context/CartContext'
import { ApiError } from '@/api/client'
import { productsApi } from '@/api/products'
import { getDefaultVariant } from '@/utils/productFilters'
import hoaPhuong from '@/assets/decorations/hoa-phuong.png'
import songLine from '@/assets/decorations/song-line.png'
import ProductGallery from './components/ProductGallery'
import ProductBuyBox from './components/ProductBuyBox'
import ProductInfoTabs from './components/ProductInfoTabs'
import './ProductDetailPage.css'

export default function ProductDetailPage() {
  const { slug } = useParams()
  const [product, setProduct] = useState(null)
  const [related, setRelated] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | missing | error
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let cancelled = false
    setStatus('loading')
    setErrorMessage('')
    ;(async () => {
      try {
        const apiProduct = await productsApi.getBySlug(slug)
        if (cancelled) return
        setProduct(apiProduct)
        try {
          const list = await productsApi.list({
            page: 1,
            pageSize: 20,
            categoryId: apiProduct.categoryId !== 'all' ? apiProduct.categoryId : undefined,
          })
          if (!cancelled) {
            setRelated(list.items.filter((p) => p.id !== apiProduct.id).slice(0, 3))
          }
        } catch {
          if (!cancelled) setRelated([])
        }
        setStatus('ready')
      } catch (err) {
        if (cancelled) return
        setProduct(null)
        setRelated([])
        if (err instanceof ApiError && err.status === 404) {
          setStatus('missing')
        } else {
          setStatus('error')
          setErrorMessage(err?.message || 'Không tải được chi tiết sản phẩm.')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [slug])

  if (status === 'loading') {
    return (
      <div className="container product-missing">
        <p>Đang tải sản phẩm…</p>
      </div>
    )
  }

  if (status === 'missing' || (!product && status !== 'error')) {
    return (
      <div className="container product-missing">
        <h1>Không tìm thấy sản phẩm</h1>
        <p>Sản phẩm không tồn tại hoặc chưa được đăng bán công khai.</p>
        <Link to={ROUTES.PRODUCTS}>← Quay lại danh sách</Link>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="container product-missing">
        <h1>Không tải được sản phẩm</h1>
        <p>{errorMessage}</p>
        <Link to={ROUTES.PRODUCTS}>← Quay lại danh sách</Link>
      </div>
    )
  }

  return <ProductDetailContent key={product.id} product={product} related={related} />
}

function ProductDetailContent({ product, related }) {
  const navigate = useNavigate()
  const { addToCart } = useCart()

  const [variantId, setVariantId] = useState(product.defaultVariantId)
  const variant = product.variants.find((v) => v.id === variantId) || getDefaultVariant(product)

  function handleAddToCart(quantity) {
    addToCart({ product, variant, quantity })
  }

  function handleBuyNow(quantity) {
    addToCart({ product, variant, quantity })
    navigate(ROUTES.CART)
  }

  return (
    <div className="product-detail">
      <img className="decoration product-detail__flowers" src={hoaPhuong} alt="" aria-hidden="true" />
      <img className="decoration product-detail__wave-top" src={songLine} alt="" aria-hidden="true" />
      <img className="decoration product-detail__wave-mid" src={songLine} alt="" aria-hidden="true" />

      <div className="container product-detail__inner">
        <Breadcrumbs
          items={[
            { label: 'Trang chủ', to: ROUTES.HOME },
            { label: 'Sản phẩm', to: ROUTES.PRODUCTS },
            { label: product.categoryName, to: `${ROUTES.PRODUCTS}?category=${product.categoryId}` },
            { label: product.name },
          ]}
        />

        <div className="product-detail__top">
          <ProductGallery images={product.galleryImages} name={product.name} />

          <ProductBuyBox
            product={product}
            variant={variant}
            onVariantChange={setVariantId}
            onAddToCart={handleAddToCart}
            onBuyNow={handleBuyNow}
          />
        </div>

        <ProductInfoTabs product={product} variant={variant} />

        {related.length > 0 ? (
          <section className="product-related">
            <h2>Sản phẩm liên quan</h2>
            <img className="product-related__wave" src={songLine} alt="" aria-hidden="true" />

            <div className="product-related__grid">
              {related.map((item) => (
                <ProductCard
                  key={item.id}
                  product={item}
                  variant={getDefaultVariant(item)}
                  cartVariant="full"
                  imageRatio="wide"
                />
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </div>
  )
}
