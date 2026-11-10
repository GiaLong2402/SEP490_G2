import { useState } from 'react'
import Modal from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { CloseIcon } from '@/components/icons/Icons'
import { ordersApi } from '@/api/orders'
import { formatPrice } from '@/utils/formatPrice'

/**
 * Xác nhận hủy đơn chưa thanh toán — POST /me/orders/{code}/cancel (task Duy).
 * Backend: giải phóng hàng đang giữ + hủy link PayOS (task Long).
 *
 * props: open, order (đã qua mapper), onClose, onCancelled(updatedOrder)
 */
export default function CancelOrderDialog({ open, order, onClose, onCancelled }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function handleConfirm() {
    setBusy(true)
    setError('')
    try {
      const updated = await ordersApi.cancel(order.code)
      onCancelled?.(updated)
      onClose()
    } catch (err) {
      setError(err.message || 'Không hủy được đơn. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={busy}
      size="sm"
      tone="danger"
      icon={<CloseIcon size={24} />}
      title={`Hủy đơn ${order?.displayCode || ''}?`}
      description="Hàng đang giữ cho bạn sẽ được trả lại kho và link thanh toán sẽ bị hủy. Thao tác này không hoàn tác được."
      footer={
        <>
          <Button variant="ghost" pill onClick={onClose} disabled={busy}>
            Giữ đơn
          </Button>
          <Button variant="danger" pill onClick={handleConfirm} disabled={busy}>
            {busy ? 'Đang hủy…' : 'Hủy đơn'}
          </Button>
        </>
      }
    >
      {order && (
        <p className="modal-summary">
          <span>Tổng tiền đơn</span>
          <strong>{formatPrice(order.total)}</strong>
        </p>
      )}
      {error && (
        <p className="modal-error" role="alert">
          {error}
        </p>
      )}
    </Modal>
  )
}
