import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import ProductCard from '@/components/storefront/ProductCard/ProductCard'
import { ChevronLeftIcon, ChevronRightIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { useStoreCatalog } from '@/hooks/useStoreCatalog'
import { getBranchRootCategories, getRootCategories } from '@/utils/categoryTree'
import { getDefaultVariant } from '@/utils/productFilters'

import heroSeafood from '@/assets/banners/hero-seafood.png'
import collectionSoChe from '@/assets/banners/collection-so-che.png'
import collectionCheBien from '@/assets/banners/collection-che-bien.png'
import bannerChaMuc from '@/assets/banners/banner-cha-muc.png'
import storePacking from '@/assets/about/store-packing.png'
import hoaPhuong from '@/assets/decorations/hoa-phuong.png'
import songLine from '@/assets/decorations/song-line.png'
import songThuyen from '@/assets/decorations/song-thuyen.png'
import benefitSoChe from '@/assets/icons/benefit-so-che.png'
import benefitDongKhay from '@/assets/icons/benefit-dong-khay.png'
import benefitGiaoHang from '@/assets/icons/benefit-giao-hang.png'

import './HomePage.css'

/* ---------- Dữ liệu tĩnh của trang chủ ---------- */

// Muốn thêm slide: chỉ cần thêm 1 object vào mảng này
const HERO_SLIDES = [
  {
    id: 1,
    image: heroSeafood,
    titleLine1: 'Hải sản ngon,',
    titleLine2: 'bữa nhà thêm trọn vị',
    description: 'Sơ chế gọn gàng, đóng khay tiện lợi tại Hòa Bình.',
  },
  {
    id: 2,
    image: bannerChaMuc,
    titleLine1: 'Chả mực Hòa Bình,',
    titleLine2: 'vàng giòn đậm vị',
    description: 'Món ngon chế biến sẵn cho bữa cơm gia đình.',
  },
]

/*
  Icon PNG trong src/assets/icons.
  scale: chỉnh nhẹ nếu họa tiết trong vòng tròn nhìn nhỏ hơn icon khác.
*/
const BENEFITS = [
  {
    image: benefitSoChe,
    scale: 1.08,
    title: 'Chăm chút từ khâu sơ chế',
    description: 'Làm sạch, cắt khúc, phi lê... tiện lợi cho bữa ăn gia đình.',
  },
  {
    image: benefitDongKhay,
    scale: 1.18,
    title: 'Đóng khay rõ khối lượng',
    description: 'Sản phẩm được đóng khay, ghi rõ trọng lượng, dễ dàng lựa chọn.',
  },
  {
    image: benefitGiaoHang,
    scale: 1.14,
    title: 'Đặt hàng thuận tiện',
    description: 'Chọn sản phẩm yêu thích, đặt hàng nhanh chóng.',
  },
]

const COLLECTIONS = [
  {
    id: 'so-che',
    title: 'Hải sản sơ chế',
    description: 'Gọn khâu chuẩn bị, tiện bữa cơm nhà',
    linkText: 'Xem sản phẩm',
    to: `${ROUTES.PRODUCTS}?category=muc`,
    image: collectionSoChe,
  },
  {
    id: 'che-bien',
    title: 'Hải sản chế biến',
    description: 'Thêm lựa chọn cho bữa ăn gia đình',
    linkText: 'Khám phá ngay',
    to: `${ROUTES.PRODUCTS}?category=che-bien`,
    image: collectionCheBien,
  },
]

const CATEGORY_PAGE_SIZE = 5
const FEATURED_LIMIT = 8

export default function HomePage() {
  const navigate = useNavigate()
  const [slideIndex, setSlideIndex] = useState(0)
  const [catPage, setCatPage] = useState(0)
  const { products, categories, loading: catalogLoading } = useStoreCatalog({ pageSize: 24 })

  const slide = HERO_SLIDES[slideIndex]

  // Chỉ danh mục cha có imageUrl (không fallback ảnh local)
  const homeCategories = useMemo(
    () =>
      getRootCategories(categories)
        .filter((c) => Boolean(c.imageUrl?.trim()))
        .map((c) => ({
          id: c.id,
          name: c.name,
          image: c.imageUrl.trim(),
        })),
    [categories],
  )

  // Chỉ các danh mục cha có nhánh con (Các loại chả/cá/mực, Hải sản một nắng…)
  const featuredTabs = useMemo(
    () => [
      { id: 'all', label: 'Tất cả' },
      ...getBranchRootCategories(categories).map((c) => ({ id: c.id, label: c.name })),
    ],
    [categories],
  )

  const catPageCount = Math.max(1, Math.ceil(homeCategories.length / CATEGORY_PAGE_SIZE))
  const safeCatPage = Math.min(catPage, catPageCount - 1)
  const visibleCategories = homeCategories.slice(
    safeCatPage * CATEGORY_PAGE_SIZE,
    safeCatPage * CATEGORY_PAGE_SIZE + CATEGORY_PAGE_SIZE,
  )
  const showCatNav = homeCategories.length > CATEGORY_PAGE_SIZE

  useEffect(() => {
    setCatPage(0)
  }, [homeCategories.length])

  const featured = useMemo(() => products.slice(0, FEATURED_LIMIT), [products])

  function handleFeaturedTab(tabId) {
    if (tabId === 'all') return
    navigate(`${ROUTES.PRODUCTS}?category=${encodeURIComponent(tabId)}`)
  }

  function prevSlide() {
    setSlideIndex((i) => (i - 1 + HERO_SLIDES.length) % HERO_SLIDES.length)
  }

  function nextSlide() {
    setSlideIndex((i) => (i + 1) % HERO_SLIDES.length)
  }

  function prevCatPage() {
    setCatPage((i) => (i - 1 + catPageCount) % catPageCount)
  }

  function nextCatPage() {
    setCatPage((i) => (i + 1) % catPageCount)
  }

  return (
    <div className="home-page">
      {/* ===== 1. Hero banner (full màn hình) ===== */}
      <section
        className="home-hero"
        aria-roledescription="carousel"
        aria-label="Banner trang chủ"
      >
        <img
          key={slide.id}
          className="home-hero__image"
          src={slide.image}
          alt=""
          fetchPriority="high"
        />
        <div className="home-hero__overlay" />
        <img
          className="decoration home-hero__wave"
          src={songLine}
          alt=""
          aria-hidden="true"
        />

        <div className="container home-hero__inner">
          <div className="home-hero__content">
            <p className="home-hero__eyebrow">
              HẢI SẢN HÒA BÌNH
              <span className="home-hero__eyebrow-line" aria-hidden="true" />
            </p>
            <h1>
              <span className="home-hero__title-white">{slide.titleLine1}</span>
              <span className="home-hero__title-cyan">{slide.titleLine2}</span>
            </h1>
            <p className="home-hero__desc">{slide.description}</p>

            <div className="home-hero__actions">
              <Link to={ROUTES.PRODUCTS} className="home-btn-orange">
                Khám phá sản phẩm <span aria-hidden="true">→</span>
              </Link>
              <p className="home-hero__script">
                Hải Phòng
                <br />
                vị biển quê hương
              </p>
            </div>
          </div>
        </div>

        <button
          type="button"
          className="home-hero__arrow is-prev"
          aria-label="Slide trước"
          onClick={prevSlide}
        >
          <ChevronLeftIcon />
        </button>
        <button
          type="button"
          className="home-hero__arrow is-next"
          aria-label="Slide sau"
          onClick={nextSlide}
        >
          <ChevronRightIcon />
        </button>

        <div className="home-hero__dots" role="tablist" aria-label="Chọn slide">
          {HERO_SLIDES.map((s, index) => (
            <button
              key={s.id}
              type="button"
              role="tab"
              aria-selected={index === slideIndex}
              aria-label={`Slide ${index + 1}`}
              className={index === slideIndex ? 'is-active' : ''}
              onClick={() => setSlideIndex(index)}
            />
          ))}
        </div>
      </section>

      {/* ===== 2. Ba lợi ích (dính liền dưới hero) ===== */}
      <section className="home-benefits" aria-label="Lợi ích">
        <div className="container home-benefits__grid">
          {BENEFITS.map((item) => (
            <div key={item.title} className="home-benefits__item">
              <span
                className="home-benefits__icon"
                aria-hidden="true"
                style={{ '--benefit-icon-scale': item.scale }}
              >
                <img src={item.image} alt="" />
              </span>
              <div>
                <h3>{item.title}</h3>
                <p>{item.description}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ===== 3. Danh mục dạng ảnh tròn ===== */}
      <section className="home-categories">
        <img
          className="decoration home-categories__flowers"
          src={hoaPhuong}
          alt=""
          aria-hidden="true"
        />
        <img
          className="decoration home-categories__wave"
          src={songLine}
          alt=""
          aria-hidden="true"
        />

        <div className="container">
          <div className="home-section-head">
            <h2>Bạn đang tìm món gì?</h2>
            <Link to={ROUTES.PRODUCTS} className="home-link-more">
              Xem tất cả danh mục <span aria-hidden="true">→</span>
            </Link>
          </div>

          {homeCategories.length === 0 && !catalogLoading ? (
            <p className="home-categories__empty">
              Chưa có danh mục cha có ảnh (imageUrl) trên hệ thống.
            </p>
          ) : (
            <div className="home-categories__carousel">
              {showCatNav && (
                <button
                  type="button"
                  className="home-categories__arrow is-prev"
                  aria-label="Danh mục trước"
                  onClick={prevCatPage}
                >
                  <ChevronLeftIcon />
                </button>
              )}

              <div
                className="home-categories__grid"
                data-count={visibleCategories.length}
                aria-live="polite"
              >
                {visibleCategories.map((cat) => (
                  <Link
                    key={cat.id}
                    to={`${ROUTES.PRODUCTS}?category=${cat.id}`}
                    className="home-cat-card"
                  >
                    <span className="home-cat-card__image">
                      <img src={cat.image} alt="" />
                    </span>
                    <span className="home-cat-card__name">{cat.name}</span>
                  </Link>
                ))}
              </div>

              {showCatNav && (
                <button
                  type="button"
                  className="home-categories__arrow is-next"
                  aria-label="Danh mục sau"
                  onClick={nextCatPage}
                >
                  <ChevronRightIcon />
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      {/* ===== 4. Hai banner bộ sưu tập ===== */}
      <section className="home-collections container" aria-label="Bộ sưu tập">
        {COLLECTIONS.map((item) => (
          <Link
            key={item.id}
            to={item.to}
            className={`home-collection home-collection--${item.id}`}
          >
            <img className="home-collection__image" src={item.image} alt="" />
            <div className="home-collection__text">
              <h3>{item.title}</h3>
              <p>{item.description}</p>
              <span className="home-collection__link">
                {item.linkText} <span aria-hidden="true">→</span>
              </span>
            </div>
          </Link>
        ))}
      </section>

      {/* ===== 5. Sản phẩm nổi bật ===== */}
      <section className="home-featured container">
        <div className="home-section-head">
          <div className="home-featured__title">
            <h2>Sản phẩm nổi bật</h2>
            <div className="home-tabs" role="tablist" aria-label="Lọc theo danh mục">
              {featuredTabs.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  role="tab"
                  aria-selected={t.id === 'all'}
                  className={t.id === 'all' ? 'is-active' : ''}
                  onClick={() => handleFeaturedTab(t.id)}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>
          <Link to={ROUTES.PRODUCTS} className="home-link-more">
            Xem tất cả sản phẩm <span aria-hidden="true">→</span>
          </Link>
        </div>

        <div className="home-featured__grid">
          {catalogLoading ? (
            <p>Đang tải sản phẩm…</p>
          ) : featured.length === 0 ? (
            <p>Chưa có sản phẩm published trên hệ thống. Hãy publish SP trong app nội bộ.</p>
          ) : (
            featured.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                variant={getDefaultVariant(product)}
                cartVariant="icon"
                imageRatio="landscape"
              />
            ))
          )}
        </div>

        {!catalogLoading && featured.length > 0 ? (
          <div className="home-featured__more">
            <Link to={ROUTES.PRODUCTS} className="home-btn-orange">
              Xem tất cả sản phẩm <span aria-hidden="true">→</span>
            </Link>
          </div>
        ) : null}
      </section>

      {/* ===== 6. Câu chuyện cửa hàng ===== */}
      <section className="home-story container">
        <div className="home-story__panel">
          <div className="home-story__media">
            <img
              src={storePacking}
              alt="Nhân viên Hòa Bình đóng khay hải sản"
              loading="lazy"
            />
          </div>

          <div className="home-story__body">
            <img
              className="decoration home-story__flowers"
              src={hoaPhuong}
              alt=""
              aria-hidden="true"
            />
            <img
              className="decoration home-story__boat"
              src={songThuyen}
              alt=""
              aria-hidden="true"
            />

            <p className="home-story__eyebrow">
              CÂU CHUYỆN HÒA BÌNH
              <span className="home-story__eyebrow-line" aria-hidden="true" />
            </p>
            <h2>Hòa Bình – Hải sản từ Hải Phòng</h2>
            <p className="home-story__desc">
              Chăm chút từ sơ chế đến đóng khay,
              <br />
              giúp bạn chuẩn bị bữa ăn thuận tiện hơn.
            </p>
            <Link to={ROUTES.ABOUT} className="home-btn-orange">
              Tìm hiểu cửa hàng <span aria-hidden="true">→</span>
            </Link>
          </div>
        </div>
      </section>

      {/* ===== 7. Banner món ăn cuối trang ===== */}
      <section className="home-promo container">
        <div className="home-promo__banner">
          <img src={bannerChaMuc} alt="" loading="lazy" />
          <div className="home-promo__shade" />
          <div className="home-promo__content">
            <h2>
              <span className="is-white">Đậm vị biển,</span>
              <span className="is-cyan">ấm bữa cơm nhà</span>
            </h2>
            <p>Khám phá những món ngon từ Hòa Bình.</p>
            <Link to={ROUTES.PRODUCTS} className="home-btn-orange">
              Xem sản phẩm <span aria-hidden="true">→</span>
            </Link>
          </div>
        </div>
      </section>
    </div>
  )
}
