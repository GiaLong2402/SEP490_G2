import { date, daysUntil, money, num } from '@/shared/lib/format';
import { Button, Chip, DataTable, Modal, type Column } from '@/shared/ui';
import { useRawMaterialLots } from '../hooks';
import type { RawMaterial, RawMaterialLot } from '../types';

/** Lô của 1 NVL theo FEFO (HSD gần nhất trước) — nguồn cho xuất NVL sản xuất và tính tiền NVL. */
export function RawMaterialLotsModal({ material, onClose }: { material: RawMaterial | null; onClose: () => void }) {
  const lots = useRawMaterialLots(material?.id ?? null);
  const unit = material?.unit ?? '';

  const columns: Column<RawMaterialLot>[] = [
    { key: 'lot', header: 'Mã lô', cell: (l) => <div><div className="num">{l.lotCode}</div>{l.receiptCode && <div className="text-micro text-ink-faint">{l.receiptCode}</div>}</div> },
    { key: 'mfg', header: 'Ngày SX', hideOnMobile: true, cell: (l) => date(l.mfgDate) },
    {
      key: 'exp', header: 'HSD',
      cell: (l) => {
        if (!l.expDate) return <span className="text-ink-faint">Không có</span>;
        const left = daysUntil(l.expDate) ?? 0;
        return <div className="whitespace-nowrap">{date(l.expDate)}{l.qtyRemaining > 0 && left <= 3 && <span className="ml-1"><Chip tone={left < 0 ? 'red' : 'amber'}>{left < 0 ? 'Hết hạn' : `Còn ${left} ngày`}</Chip></span>}</div>;
      },
    },
    { key: 'price', header: 'Đơn giá', align: 'right', cell: (l) => (l.unitPrice === null ? <span className="text-ink-faint">Tồn đầu kỳ</span> : <span className="num">{money(l.unitPrice)}</span>) },
    { key: 'in', header: 'Đã nhập', align: 'right', hideOnMobile: true, cell: (l) => <span className="num">{num(l.qtyReceived)} {unit}</span> },
    { key: 'left', header: 'Còn', align: 'right', cell: (l) => <span className={`num ${l.qtyRemaining > 0 ? 'font-medium' : 'text-ink-faint'}`}>{num(l.qtyRemaining)} {unit}</span> },
  ];

  return (
    <Modal open={!!material} title={material ? `Lô nguyên liệu · ${material.name}` : ''} width="xl" onClose={onClose} footer={<Button onClick={onClose}>Đóng</Button>}>
      {material && (
        <>
          <p className="mb-3 text-small text-ink-muted">
            Tổng tồn <span className="num font-medium text-ink">{num(material.qtyOnHand)} {unit}</span> — sắp theo HSD gần nhất trước (lô xuất trước khi sản xuất).
          </p>
          <DataTable columns={columns} rows={lots.data} rowKey={(l) => l.id} loading={lots.isLoading} error={lots.error} onRetry={lots.refetch} emptyText="Chưa có lô nào" />
        </>
      )}
    </Modal>
  );
}
