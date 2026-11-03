import { useState } from 'react'
import { formatTrayLabel } from '@/utils/formatPrice'
import songLine from '@/assets/decorations/song-line.png'
import './ProductInfoTabs.css'

// Danh sách tab. Muốn thêm tab: thêm 1 dòng ở đây + 1 khối {activeTab === '...'} bên dưới
const TABS = [
  { id: 'info', label: 'Thông tin sản phẩm' },
  { id: 'storage', label: 'Hướng dẫn bảo quản' },
  { id: 'reviews', label: 'Đánh giá' },
]

/**
 * Khung tab thông tin dưới phần ảnh + mua hàng.
 *
 * props:
 * - product: sản phẩm đang xem
 * - variant: quy cách đang chọn (để hiện ở dòng "Quy cách đang chọn")
 */
export default function ProductInfoTabs({ product, variant }) {
  const [activeTab, setActiveTab] = useState('info')

  return (
    <section className="info-tabs">
      {/* Thanh tab */}
      <div className="info-tabs__list" role="tablist" aria-label="Thông tin chi tiết">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={activeTab === tab.id ? 'is-active' : ''}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Nội dung từng tab. key={activeTab} để hiệu ứng hiện dần chạy lại mỗi lần đổi tab */}
      <div key={activeTab} className="info-tabs__panel" role="tabpanel">
        {activeTab === 'info' && (
          <div className="info-tabs__grid">
            <div>
              <h2>Thông tin sản phẩm</h2>
              <p className="info-tabs__desc">{product.description}</p>

              <table className="info-tabs__table">
                <tbody>
                  <tr>
                    <th>Tên sản phẩm</th>
                    <td>{product.specs.productName}</td>
                  </tr>
                  <tr>
                    <th>Danh mục</th>
                    <td>{product.specs.category}</td>
                  </tr>
                  <tr>
                    <th>Quy cách đang chọn</th>
                    <td>{formatTrayLabel(variant.weightGrams)}</td>
                  </tr>
                  <tr>
                    <th>Đơn vị bán</th>
                    <td>{product.specs.packaging}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Khung lưu ý bên phải */}
            <aside className="info-tabs__note">
              <span className="info-tabs__note-icon" aria-hidden="true">!</span>
              <div>
                <h3>Lưu ý khi nhận hàng</h3>
                <p>{product.receiveNotes}</p>
              </div>
              <img className="decoration info-tabs__note-wave" src={songLine} alt="" aria-hidden="true" />
            </aside>
          </div>
        )}

        {activeTab === 'storage' && (
          <div>
            <h2>Hướng dẫn bảo quản</h2>
            <p className="info-tabs__desc">{product.storageGuide}</p>
          </div>
        )}

        {activeTab === 'reviews' && (
          <div>
            <h2>Đánh giá</h2>
            <p className="info-tabs__desc">Chưa có đánh giá cho sản phẩm này.</p>
          </div>
        )}
      </div>
    </section>
  )
}
