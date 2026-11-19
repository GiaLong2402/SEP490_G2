import { Printer } from 'lucide-react';
import { date, dateTime, num } from '@/shared/lib/format';
import { Button, Modal, PageLoading } from '@/shared/ui';
import type { OrderPickingSlip } from '../types';

export function PickingSlipDialog({
  open,
  slip,
  loading,
  error,
  onClose,
  onRetry,
}: {
  open: boolean;
  slip: OrderPickingSlip | null;
  loading?: boolean;
  error?: Error | null;
  onClose: () => void;
  onRetry?: () => void;
}) {
  return (
    <Modal
      open={open}
      title="Phiếu soạn hàng"
      onClose={onClose}
      width="lg"
      footer={
        <>
          <Button icon={<Printer size={14} />} disabled={!slip?.lines.length} onClick={() => window.print()}>
            In phiếu
          </Button>
          <Button variant="primary" autoFocus onClick={onClose}>Đóng</Button>
        </>
      }
    >
      {loading && !slip && <PageLoading />}
      {error && !slip && (
        <p className="text-center text-ink-muted">
          {error.message}{' '}
          {onRetry && (
            <button type="button" className="text-primary hover:underline" onClick={onRetry}>
              Thử lại
            </button>
          )}
        </p>
      )}
      {slip && (
        <div className="print-receipt">
          <div className="text-center">
            <div className="text-h2">Hải sản Hòa Bình</div>
            <div className="text-small font-medium text-ink-muted">Phiếu soạn hàng</div>
            <div className="num mt-1">{slip.orderCode}</div>
            <div className="mt-1">Khách: {slip.customerName ?? '—'}</div>
            <div className="text-small text-ink-muted">{dateTime(slip.generatedAt)}</div>
          </div>
          {slip.lines.length === 0 ? (
            <p className="mt-4 text-center text-ink-muted">Không còn HOLD để soạn cho đơn này.</p>
          ) : (
            <table className="mt-3 w-full border-y border-line text-left text-small">
              <thead>
                <tr className="border-b border-line text-ink-muted">
                  <th className="py-1.5 pr-2 font-medium">Vị trí</th>
                  <th className="py-1.5 pr-2 font-medium">SKU / SP</th>
                  <th className="py-1.5 pr-2 font-medium">Lô · HSD</th>
                  <th className="py-1.5 text-right font-medium">Khay</th>
                </tr>
              </thead>
              <tbody>
                {slip.lines.map((l, i) => (
                  <tr key={`${l.lotId}-${l.locationId}-${i}`} className="border-t border-line/60 align-top">
                    <td className="py-1.5 pr-2 num whitespace-nowrap">{l.locationCode ?? '—'}{l.zone ? ` · ${l.zone}` : ''}</td>
                    <td className="py-1.5 pr-2">
                      <div className="font-medium">{l.productName ?? '—'}</div>
                      <div className="num text-ink-faint">{l.sku}{l.packLabel ? ` · ${l.packLabel}` : ''}</div>
                    </td>
                    <td className="py-1.5 pr-2">
                      <div className="num">{l.lotCode ?? '—'}</div>
                      <div className="text-ink-muted">{date(l.expDate)}</div>
                    </td>
                    <td className="py-1.5 text-right num font-medium">{num(l.qtyKhay)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-line">
                  <td colSpan={3} className="py-2 text-right text-ink-muted">Tổng khay</td>
                  <td className="py-2 text-right num font-semibold">{num(slip.lines.reduce((s, l) => s + l.qtyKhay, 0))}</td>
                </tr>
              </tfoot>
            </table>
          )}
          <p className="mt-3 text-center text-micro text-ink-faint">In từ HOLD còn hiệu lực — sắp FEFO</p>
        </div>
      )}
    </Modal>
  );
}
