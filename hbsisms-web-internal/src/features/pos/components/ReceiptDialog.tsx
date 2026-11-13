import { CheckCircle2, Printer } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { dateTime, money } from '@/shared/lib/format';
import { Button, Modal } from '@/shared/ui';
import { posPaymentLabels, type Receipt } from '../types';

export function ReceiptDialog({ receipt, onClose }: { receipt: Receipt | null; onClose: () => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  const orderPath = receipt ? `/orders/${receipt.orderId}` : '';

  /** Đóng biên lai; nếu đang ở trang khác (vd. POS) thì chuyển sang chi tiết đơn. */
  const viewOrderDetail = () => {
    onClose();
    if (receipt && location.pathname !== orderPath) {
      navigate(orderPath);
    }
  };

  return (
    <Modal
      open={!!receipt}
      title={<span className="flex items-center gap-2"><CheckCircle2 size={18} className="text-green-fg" aria-hidden />Biên lai bán hàng</span>}
      onClose={onClose}
      footer={
        <>
          <Button icon={<Printer size={14} />} onClick={() => window.print()}>In biên lai</Button>
          <Button variant="primary" autoFocus onClick={onClose}>Đóng</Button>
        </>
      }
    >
      {receipt && (
        <>
          <div className="print-receipt">
            <div className="text-center">
              <div className="text-h2">Hải sản Hòa Bình</div>
              <div className="text-small font-medium text-ink-muted">Biên lai bán hàng</div>
              <div className="num">{receipt.code}</div>
              <div className="text-small text-ink-muted">{dateTime(receipt.placedAt)} · {receipt.cashierName}</div>
              <div className="mt-1">Khách: {receipt.customerName}</div>
            </div>
            <ul className="mt-3 border-y border-line py-2">
              {receipt.lines.map((line) => (
                <li key={line.variantId} className="py-1">
                  <div className="font-medium">{line.productName} · {line.packLabel}</div>
                  <div className="flex justify-between text-small text-ink-muted"><span>{line.qtyLabel} × {money(line.unitPrice)}</span><span className="num text-ink">{money(line.lineTotal)}</span></div>
                </li>
              ))}
            </ul>
            <dl className="mt-2 space-y-1">
              {receipt.discountTotal > 0 && (
                <>
                  <div className="flex justify-between"><dt className="text-ink-muted">Tạm tính</dt><dd className="num">{money(receipt.subtotal)}</dd></div>
                  <div className="flex justify-between"><dt className="text-ink-muted">Giảm giá</dt><dd className="num">−{money(receipt.discountTotal)}</dd></div>
                </>
              )}
              <div className="flex justify-between text-h2"><dt>Tổng cộng</dt><dd className="tabular-nums">{money(receipt.total)}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-muted">Thanh toán</dt><dd>{posPaymentLabels[receipt.paymentMethod] ?? receipt.paymentMethod}</dd></div>
              {receipt.tendered !== null && <div className="flex justify-between"><dt className="text-ink-muted">Khách đưa</dt><dd className="num">{money(receipt.tendered)}</dd></div>}
              {receipt.change !== null && <div className="flex justify-between font-semibold"><dt>Tiền thối</dt><dd className="num text-body">{money(receipt.change)}</dd></div>}
              {receipt.debtAfter !== null && <div className="flex justify-between"><dt className="text-ink-muted">Nợ sau đơn này</dt><dd className="num">{money(receipt.debtAfter)}</dd></div>}
            </dl>
            <p className="mt-3 text-center text-micro text-ink-faint">Biên lai bán hàng — không phải hóa đơn điện tử</p>
          </div>
          <p className="mt-3 text-center text-small">
            <button type="button" onClick={viewOrderDetail} className="text-primary hover:underline">
              Xem chi tiết đơn
            </button>
          </p>
        </>
      )}
    </Modal>
  );
}
