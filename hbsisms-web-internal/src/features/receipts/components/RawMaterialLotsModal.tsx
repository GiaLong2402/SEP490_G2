import { useState } from 'react';
import { date, daysUntil, money, num } from '@/shared/lib/format';
import { Button, Chip, DataTable, InfoTip, Modal, type Column } from '@/shared/ui';
import { useRawMaterialLots, useUnitLabel } from '../hooks';
import type { RawMaterial, RawMaterialLot } from '../types';
import { DiscardLotDialog } from './DiscardLotDialog';

export const isLotExpired = (l: Pick<RawMaterialLot, 'expDate'>) => !!l.expDate && (daysUntil(l.expDate) ?? 0) < 0;
/** Lô trả lại từ lệnh sản xuất (2C): mã lô gốc + "-TL", ghi chú "Dư từ LSX-…". */
const returnedFrom = (l: RawMaterialLot) => (l.lotCode.endsWith('-TL') ? l.note?.match(/LSX-[\w-]+/)?.[0] ?? 'lệnh sản xuất' : null);

/** Lô của 1 NVL theo FEFO (HSD gần nhất trước) — nguồn cho xuất NVL sản xuất và tính tiền NVL. */
export type LotsModalMaterial = Pick<RawMaterial, 'id' | 'name' | 'unit' | 'qtyOnHand'> & { code?: string };

export function RawMaterialLotsModal({ material, onClose }: { material: LotsModalMaterial | null; onClose: () => void }) {
  const lots = useRawMaterialLots(material?.id ?? null);
  const unit = useUnitLabel()(material?.unit);
  const [discarding, setDiscarding] = useState<RawMaterialLot | null>(null);
  const expiredQty = (lots.data ?? []).filter(isLotExpired).reduce((sum, l) => sum + l.qtyRemaining, 0);

  const columns: Column<RawMaterialLot>[] = [
    {
      key: 'lot', header: 'Mã lô',
      cell: (l) => {
        const from = returnedFrom(l);
        return <div><div className="num">{l.lotCode}</div>{from ? <div className="text-micro text-ink-faint">Trả lại · {from}</div> : l.receiptCode && <div className="text-micro text-ink-faint">{l.receiptCode}</div>}</div>;
      },
    },
    { key: 'mfg', header: 'Ngày sản xuất', hideOnMobile: true, cell: (l) => date(l.mfgDate) },
    {
      key: 'exp', header: 'Hạn sử dụng',
      cell: (l) => {
        if (!l.expDate) return <span className="text-ink-faint">Không có</span>;
        const left = daysUntil(l.expDate) ?? 0;
        return <div className="whitespace-nowrap">{date(l.expDate)}{l.qtyRemaining > 0 && left <= 3 && <span className="ml-1"><Chip tone={left < 0 ? 'red' : 'amber'}>{left < 0 ? 'Hết hạn' : `Còn ${left} ngày`}</Chip></span>}</div>;
      },
    },
    { key: 'price', header: 'Đơn giá', align: 'right', cell: (l) => (l.unitPrice === null ? <span className="text-ink-faint">Tồn đầu kỳ</span> : <span className="num">{money(l.unitPrice)}</span>) },
    { key: 'in', header: 'Đã nhập', align: 'right', hideOnMobile: true, cell: (l) => <span className="num">{num(l.qtyReceived)} {unit}</span> },
    { key: 'left', header: 'Còn', align: 'right', cell: (l) => <span className={`num ${l.qtyRemaining > 0 ? 'font-medium' : 'text-ink-faint'}`}>{num(l.qtyRemaining)} {unit}</span> },
    {
      key: 'discard', header: '', align: 'right',
      cell: (l) => (l.qtyRemaining > 0 && isLotExpired(l) ? <Button variant="danger" className="!h-7 !px-2.5 text-small" onClick={() => setDiscarding(l)}>Bỏ lô</Button> : null),
    },
  ];

  return (
    <Modal open={!!material} title={material ? `Lô nguyên liệu · ${material.name}` : ''} width="xl" onClose={onClose} footer={<Button onClick={onClose}>Đóng</Button>}>
      {material && (
        <>
          <p className="mb-3 flex flex-wrap items-center gap-1 text-small text-ink-muted">
            Tổng còn <span className="font-semibold tabular-nums text-ink">{num(material.qtyOnHand)} {unit}</span>
            {expiredQty > 0 && <> · dùng được <span className="font-semibold tabular-nums text-ink">{num(material.qtyOnHand - expiredQty)} {unit}</span> · {num(expiredQty)} {unit} đã hết hạn</>}
            <InfoTip label="Ghi chú thứ tự lô">
              <span className="block">Lô sắp hết hạn đứng trên cùng và sẽ được dùng trước, nên lô trả lại (-TL) từ mẻ trước luôn được dùng trước.</span>
              <span className="block">Lô đã quá hạn không được xuất cho sản xuất. Nếu đã vứt thì bấm Bỏ lô để trừ khỏi tồn.</span>
            </InfoTip>
          </p>
          <DataTable columns={columns} rows={lots.data} rowKey={(l) => l.id} loading={lots.isLoading} error={lots.error} onRetry={lots.refetch} emptyText="Chưa có lô nào" />
        </>
      )}
      <DiscardLotDialog lot={discarding} materialName={material?.name ?? ''} unit={unit} qtyOnHand={material?.qtyOnHand} onClose={() => setDiscarding(null)} />
    </Modal>
  );
}
