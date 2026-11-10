import { useState } from 'react'
import { Link } from 'react-router-dom'
import Modal from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { CheckIcon, InfoIcon } from '@/components/icons/Icons'
import { salesRequestsApi } from '@/api/salesRequests'
import { requestDetailPath } from '@/constants/routes'
import { formatTrayLabel } from '@/utils/formatPrice'
import { formatDate, toIsoDate } from '@/utils/date'

/**
 * Hộp thoại khi POST /checkout trả 409 thiếu hàng.
 *
 * props:
 * - shortages: [{ variantId, requestedQty, availableQty }] (ApiError.payload.shortages)
 * - items: dòng giỏ hàng (CartContext) để lấy tên, ảnh, quy cách
 * - neededDate: ngày khách muốn nhận (Date) — dùng khi gửi yêu cầu
 * - onReduce(rows): giảm giỏ về số còn hàng (rows: [{ item, availableQty }])
 * - onClose
 */
export default function StockShortageDialog({ shortages, items, neededDate, onReduce, onClose }) {
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(null)
  const [error, setError] = useState('')

  const rows = shortages
    .map((s) => ({ ...s, item: items.find((i) => String(i.variant.id) === String(s.variantId)) }))
    .filter((r) => r.item)

  async function sendRequest() {
    setSending(true)
    setError('')
    try {
      const summary = rows
        .map((r) => `${r.item.name} · ${formatTrayLabel(r.item.variant.weightGrams)} × ${r.requestedQty} khay (còn ${r.availableQty})`)
        .join('; ')
      const created = await salesRequestsApi.create({
        summary,
        neededDate: toIsoDate(neededDate),
        requestedQty: rows.reduce((s, r) => s + r.requestedQty, 0),
        availableQty: rows.reduce((s, r) => s + r.availableQty, 0),
        items: rows.map((r) => ({ variantId: r.variantId, requestedQty: r.requestedQty, availableQty: r.availableQty })),
      })
      setSent(created)
    } catch (err) {
      setError(err.message || 'Không gửi được yêu cầu.')
    } finally {
      setSending(false)
    }
  }

  if (sent) {
    return (
      <Modal
        open
        onClose={onClose}
        size="sm"
        icon={<CheckIcon size={24} />}
        title="Đã gửi yêu cầu"
        description={`Yêu cầu ${sent.code} đã tới cửa hàng. Hòa Bình sẽ báo khả năng đáp ứng và giá cho bạn.`}
        footer={
          <>
            <Button variant="ghost" pill onClick={onClose}>
              Đóng
            </Button>
            <Link to={requestDetailPath(sent.code)} className="btn btn--primary btn--pill">
              Xem yêu cầu
            </Link>
          </>
        }
      />
    )
  }

  return (
    <Modal
      open
      onClose={onClose}
      busy={sending}
      tone="warning"
      icon={<InfoIcon size={24} />}
      title="Một số sản phẩm không đủ hàng"
      description="Cửa hàng chưa đủ số khay bạn chọn. Bạn có thể giảm về số còn hàng, hoặc gửi yêu cầu để cửa hàng chuẩn bị thêm."
      footer={
        <>
          <Button variant="ghost" pill onClick={sendRequest} disabled={sending}>
            {sending ? 'Đang gửi…' : 'Gửi yêu cầu đặt số lượng lớn'}
          </Button>
          <Button variant="primary" pill onClick={() => onReduce(rows)} disabled={sending}>
            Giảm về số còn hàng
          </Button>
        </>
      }
    >
      <ul className="shortage-list">
        {rows.map((r) => (
          <li key={r.variantId} className="shortage-row">
            <img src={r.item.image} alt="" />
            <div className="shortage-row__name">
              <strong>{r.item.name}</strong>
              <span>{formatTrayLabel(r.item.variant.weightGrams)}</span>
            </div>
            <dl className="shortage-row__qty">
              <div>
                <dt>Bạn chọn</dt>
                <dd>{r.requestedQty}</dd>
              </div>
              <div>
                <dt>Còn</dt>
                <dd>{r.availableQty}</dd>
              </div>
              <div className="is-short">
                <dt>Thiếu</dt>
                <dd>{r.requestedQty - r.availableQty}</dd>
              </div>
            </dl>
          </li>
        ))}
      </ul>
      <p className="shortage-note">
        Yêu cầu sẽ ghi ngày bạn cần nhận là <strong>{formatDate(toIsoDate(neededDate))}</strong>. Giỏ hàng của bạn
        được giữ nguyên.
      </p>
      {error && (
        <p className="modal-error" role="alert">
          {error}
        </p>
      )}
    </Modal>
  )
}
