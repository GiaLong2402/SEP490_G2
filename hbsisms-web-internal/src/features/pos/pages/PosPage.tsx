import { useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '@/features/auth';
import { useCustomerOptions } from '@/features/customers';
import { usePricing } from '@/features/pricing';
import { useAction } from '@/shared/api/useAction';
import { ConfirmDialog } from '@/shared/ui';
import { posApi } from '../api';
import { CartPanel } from '../components/CartPanel';
import { CatalogPanel } from '../components/CatalogPanel';
import { PosTabBar } from '../components/PosTabBar';
import { ReceiptDialog } from '../components/ReceiptDialog';
import { useCatalog } from '../hooks';
import { usePosTabs } from '../usePosTabs';
import type { CatalogItem, PosTab, Receipt } from '../types';

const isValidQty = (qty: string) => Number.isInteger(Number(qty)) && Number(qty) > 0;

/** Sẵn sàng nhận mã từ máy quét; bỏ qua màn hình cảm ứng để bàn phím ảo không tự bật lên. */
function focusScanner(input: HTMLInputElement | null) {
  if (window.matchMedia('(pointer: fine)').matches) input?.focus();
}

function Workspace({ userId }: { userId: number }) {
  const { tabs, active, addTab, selectTab, closeTab, updateTab } = usePosTabs(userId);
  const catalog = useCatalog();
  const customers = useCustomerOptions().data ?? [];
  const searchRef = useRef<HTMLInputElement>(null);
  const [closing, setClosing] = useState<PosTab | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  const items = useMemo(() => catalog.data ?? [], [catalog.data]);
  const pricedLines = active.lines
    .filter((l) => isValidQty(l.qty) && items.some((i) => i.variantId === l.variantId))
    .map((l) => ({ variantId: l.variantId, unit: l.unit, qty: Number(l.qty) }));
  const discount = active.discount === '' ? 0 : Number(active.discount);
  const pricing = usePricing({ customerId: active.customerId, paymentMethod: active.paymentMethod, discountTotal: discount, lines: pricedLines });
  // Khi không còn dòng nào, kết quả cũ của truy vấn đang tắt không còn đúng.
  const priced = pricedLines.length > 0 ? pricing.data : undefined;

  const total = priced?.total ?? 0;
  const tendered = active.tendered === '' ? null : Number(active.tendered);

  const blockReason = (() => {
    if (active.lines.length === 0) return 'Đơn chưa có sản phẩm';
    if (active.lines.some((l) => !items.some((i) => i.variantId === l.variantId))) return 'Có sản phẩm không còn bán, hãy xóa khỏi đơn';
    if (active.lines.some((l) => !isValidQty(l.qty))) return 'Số lượng phải là số nguyên dương';
    if (!priced || pricing.isFetching) return 'Đang tính giá...';
    const short = priced.lines.find((l) => l.qtyKhay > (items.find((i) => i.variantId === l.variantId)?.available ?? 0));
    if (short) return 'Có sản phẩm không đủ hàng';
    if (discount < 0 || discount > priced.subtotal) return 'Giảm giá không hợp lệ';
    if (priced.isOverLimit) return 'Đơn vượt hạn mức công nợ';
    if (active.paymentMethod === 'cash' && tendered !== null && tendered < total) return 'Tiền khách đưa chưa đủ';
    return null;
  })();

  // Tab đang thanh toán: đóng đúng tab đó dù nhân viên đã chuyển sang tab khác trong lúc chờ.
  const checkout = useAction(
    async (tab: PosTab) => {
      try {
        return await posApi.checkout({
          clientRef: tab.id,
          customerId: tab.customerId,
          paymentMethod: tab.paymentMethod,
          discountTotal: discount,
          expectedTotal: total,
          tendered: tab.paymentMethod === 'cash' ? tendered : null,
          reference: tab.paymentMethod === 'bank_transfer' ? tab.reference.trim() || null : null,
          note: tab.note.trim() || null,
          lines: pricedLines,
        });
      } catch (error) {
        // Giá hoặc tồn có thể vừa đổi: tải lại để nhân viên thấy số mới nhất.
        void pricing.refetch();
        void catalog.refetch();
        throw error;
      }
    },
    {
      onDone: (result) => {
        setReceipt(result);
      },
    },
  );

  const runCheckout = () => {
    if (blockReason || checkout.isPending) return;
    const tab = active;
    checkout.mutate(tab, { onSuccess: () => closeTab(tab.id) });
  };

  const addItem = (item: CatalogItem) => {
    const existing = active.lines.find((l) => l.variantId === item.variantId);
    updateTab(active.id, {
      lines: existing
        ? active.lines.map((l) => (l.variantId === item.variantId ? { ...l, qty: String((isValidQty(l.qty) ? Number(l.qty) : 0) + 1) } : l))
        : [...active.lines, { variantId: item.variantId, unit: 'pack', qty: '1' }],
    });
    focusScanner(searchRef.current);
  };

  const requestClose = (tab: PosTab) => {
    if (tab.lines.length > 0) setClosing(tab);
    else closeTab(tab.id);
  };

  useEffect(() => focusScanner(searchRef.current), [active.id]);

  const shortcuts = useRef<(event: KeyboardEvent) => void>(() => {});
  shortcuts.current = (event) => {
    if (document.querySelector('dialog[open]')) return;
    if (event.key === 'F2') {
      event.preventDefault();
      addTab();
    } else if (event.key === 'F4') {
      event.preventDefault();
      searchRef.current?.focus();
      searchRef.current?.select();
    } else if (event.key === 'F9') {
      event.preventDefault();
      runCheckout();
    } else if (event.altKey && /^Digit[1-9]$/.test(event.code)) {
      const target = tabs[Number(event.code.slice(5)) - 1];
      if (target) {
        event.preventDefault();
        selectTab(target.id);
      }
    }
  };
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => shortcuts.current(event);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const inCart = new Map(active.lines.map((l) => [l.variantId, isValidQty(l.qty) ? Number(l.qty) : 0]));

  return (
    <div className="flex flex-col gap-3 lg:h-[calc(100vh-84px)]">
      <div className="flex shrink-0 items-end justify-between gap-3">
        <h1 className="sr-only">Bán tại quầy</h1>
        <div className="min-w-0 flex-1"><PosTabBar tabs={tabs} activeId={active.id} onSelect={selectTab} onClose={requestClose} onAdd={addTab} /></div>
        <p className="hidden shrink-0 pb-2 text-small text-ink-faint xl:block">F2 đơn mới · F4 quét mã · F9 thanh toán · Alt+1..9 chuyển đơn</p>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1fr)_400px]">
        <CatalogPanel items={catalog.data} loading={catalog.isLoading} error={catalog.error} onRetry={catalog.refetch} inCart={inCart} onAdd={addItem} searchRef={searchRef} />
        <CartPanel
          tab={active}
          catalog={items}
          customers={customers}
          pricing={priced}
          pricingError={pricing.error}
          blockReason={blockReason}
          busy={checkout.isPending}
          onChange={(patch) => updateTab(active.id, patch)}
          onCheckout={runCheckout}
        />
      </div>
      <ConfirmDialog
        open={!!closing}
        danger
        title={`Đóng đơn ${closing?.number ?? ''}?`}
        message={`Đơn đang có ${closing?.lines.length ?? 0} sản phẩm chưa thanh toán và sẽ bị bỏ.`}
        confirmLabel="Đóng đơn"
        onConfirm={() => {
          if (closing) closeTab(closing.id);
          setClosing(null);
        }}
        onClose={() => setClosing(null)}
      />
      <ReceiptDialog
        receipt={receipt}
        onClose={() => {
          setReceipt(null);
          focusScanner(searchRef.current);
        }}
      />
    </div>
  );
}

export default function PosPage() {
  const { user } = useAuth();
  // key: đổi tài khoản thì nạp lại đơn nháp của đúng nhân viên đó.
  return user ? <Workspace key={user.id} userId={user.id} /> : null;
}
