import { useUnitLabel } from '@/features/receipts';
import { dateTimeFull, money, num } from '@/shared/lib/format';
import { Card, Chip, DataTable, InfoTip, type Column } from '@/shared/ui';
import type { ProductionOrder, ProductionOrderMaterialLine } from '../types';

/** Bước 01 (Duy, dòng 48): nguyên liệu cần dùng theo định mức + hao hụt, đối chiếu tồn. */
export function MaterialPlanCard({ order }: { order: ProductionOrder }) {
  const unitLabel = useUnitLabel();
  const columns: Column<ProductionOrderMaterialLine>[] = [
    { key: 'name', header: 'Nguyên liệu', cell: (m) => <div><div className="font-medium">{m.rawMaterialName}</div><div className="num text-small text-ink-faint">{m.rawMaterialCode}</div></div> },
    { key: 'per', header: 'Định mức / 1', align: 'right', hideOnMobile: true, cell: (m) => <span className="num">{num(m.bomQtyPerUnit)} {unitLabel(m.unit)}</span> },
    { key: 'waste', header: 'Hao hụt', align: 'right', hideOnMobile: true, cell: (m) => <span className="num">{num(m.wastePercent)}%</span> },
    { key: 'need', header: 'Cần dùng', align: 'right', cell: (m) => <span className="num font-medium">{num(m.requiredQty)} {unitLabel(m.unit)}</span> },
    {
      key: 'stock',
      header: (
        <span className="inline-flex items-center gap-1">
          Tồn dùng được
          <InfoTip label="Ghi chú tồn dùng được">Không tính lô nguyên liệu đã hết hạn (máy không xuất lô hết hạn).</InfoTip>
        </span>
      ),
      align: 'right',
      cell: (m) => {
        const after = m.onHandQty - m.requiredQty;
        return (
          <div className="whitespace-nowrap">
            <div className="num">{num(m.onHandQty)} {unitLabel(m.unit)}</div>
            <div className={`text-small ${after < 0 ? 'text-red-fg' : 'text-ink-faint'}`}>{after < 0 ? `Thiếu ${num(-after)} ${unitLabel(m.unit)}` : `Dùng xong còn ${num(after)}`}</div>
          </div>
        );
      },
    },
    { key: 'status', header: 'Tình trạng', cell: (m) => (m.sufficient ? <Chip tone="green">Đủ</Chip> : <Chip tone="red">Thiếu</Chip>) },
    { key: 'cost', header: 'Tạm tính', align: 'right', cell: (m) => <span className="num">{money(m.lineEstimatedCost)}</span> },
  ];
  return (
    <Card
      flush
      title="Nguyên liệu cần dùng"
      actions={<span className="text-small text-ink-muted">Theo định mức của sản phẩm</span>}
    >
      <DataTable columns={columns} rows={order.materials} rowKey={(m) => m.id} emptyText="Sản phẩm chưa có định mức nguyên liệu" />
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-4 py-2.5 text-small text-ink-muted">
        <span>Đã đối chiếu tồn lúc {dateTimeFull(order.createdAt)}</span>
        <span className="inline-flex items-center gap-1">
          {order.materialLineCount} nguyên liệu · Tạm tính <b className="tabular-nums text-ink">{money(order.estimatedMaterialCost)}</b>
          <InfoTip label="Ghi chú tạm tính">Tạm tính theo giá nhập các lô sẽ được lấy. Khi xuất kho (bước 2), máy tính lại đúng theo lô thật.</InfoTip>
        </span>
      </div>
    </Card>
  );
}
