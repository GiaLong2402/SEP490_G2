import Breadcrumbs from '@/components/storefront/Breadcrumbs/Breadcrumbs'
import hoaPhuong from '@/assets/decorations/hoa-phuong.png'
import songLine from '@/assets/decorations/song-line.png'
import songThuyen from '@/assets/decorations/song-thuyen.png'
import './PageBanner.css'

/**
 * Banner tiêu đề đầu trang (nền xanh nhạt, sóng biển, thuyền, hoa phượng).
 * Dùng chung cho: Sản phẩm, Về Hòa Bình, Liên hệ...
 *
 * props:
 * - title:       chữ to, ví dụ "Sản phẩm"
 * - breadcrumbs: [{ label, to? }] — xem component Breadcrumbs
 *
 * Ví dụ:
 *   <PageBanner
 *     title="Sản phẩm"
 *     breadcrumbs={[{ label: 'Trang chủ', to: '/' }, { label: 'Sản phẩm' }]}
 *   />
 */
export default function PageBanner({ title, breadcrumbs = [] }) {
  return (
    <section className="page-banner">
      <img className="decoration page-banner__boat" src={songThuyen} alt="" aria-hidden="true" />
      <img className="decoration page-banner__wave" src={songLine} alt="" aria-hidden="true" />
      <img className="decoration page-banner__flowers" src={hoaPhuong} alt="" aria-hidden="true" />

      <div className="container page-banner__content">
        <h1>{title}</h1>
        <Breadcrumbs items={breadcrumbs} />
      </div>
    </section>
  )
}
