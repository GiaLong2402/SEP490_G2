import { useState } from 'react'
import Modal from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { CloseIcon } from '@/components/icons/Icons'
import { ordersApi } from '@/api/orders'
import { formatPrice } from '@/utils/formatPrice'

/**
 * Xác nhận hủy đơn — POST /me/orders/{code}/cancel.
 * - Chưa trả: nhả hàng + hủy link PayOS.
 * - Chờ tất toán (đã cọc): mất cọc + nhả hàng + hủy link tất toán.
 *
 * props: open, order (đã qua mapper), onClose, onCancelled(updatedOrder)
 */
export default function CancelOrderDialog({ open, order, onClose, onCancelled }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const forfeitDeposit =
    order?.state === 'AWAITING_SETTLEMENT' &&
    (order?.paymentStatus === 'DEPOSITED' || order?.isDeposited)
  const depositAmount = Number(order?.depositAmount) || 0

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
      description={
        forfeitDeposit
          ? 'Hàng đang giữ sẽ trả lại kho để bán bình thường. Tiền cọc đã thanh toán sẽ không được hoàn. Link tất toán bị hủy. Thao tác này không hoàn tác được.'
          : 'Hàng đang giữ cho bạn sẽ được trả lại kho và link thanh toán sẽ bị hủy. Thao tác này không hoàn tác được.'
      }
      footer={
        <>
          <Button variant="ghost" pill onClick={onClose} disabled={busy}>
            Giữ đơn
          </Button>
          <Button variant="danger" pill onClick={handleConfirm} disabled={busy}>
            {busy ? 'Đang hủy…' : forfeitDeposit ? 'Hủy và mất cọc' : 'Hủy đơn'}
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
      {forfeitDeposit && depositAmount > 0 && (
        <p className="modal-summary modal-summary--warn">
          <span>Tiền cọc sẽ mất</span>
          <strong>{formatPrice(depositAmount)}</strong>
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
