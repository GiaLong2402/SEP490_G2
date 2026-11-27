import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAction } from '@/shared/api/useAction';
import { money } from '@/shared/lib/format';
import { Button, ErrorState, Modal, PageLoading } from '@/shared/ui';
import { productsApi } from '../api';
import type { VariantWithTiers } from '../types';

/** Task 59 — gợi ý theo đề bài thầy: WAC + % lời; Áp dụng mới đổi listPrice. */
export function SellPriceSuggestModal({
  open,
  variant,
  canApply,
  onClose,
}: {
  open: boolean;
  variant?: VariantWithTiers;
  canApply: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const suggestQ = useQuery({
    queryKey: ['sell-price-suggestion', variant?.id, 'WAC'],
    queryFn: () => productsApi.sellPriceSuggestion(variant!.id, 'WAC'),
    enabled: open && variant != null,
    retry: false,
  });

  const apply = useAction(
    () => productsApi.applySellPrice(variant!.id, { costSource: 'WAC' }),
    {
      success: (r) => {
        const half = Math.round(r.suggestedListPrice / 2 / 1000) * 1000;
        return `Đã áp dụng 1 kg ${money(r.suggestedListPrice)} · 0,5 kg ${money(half)}`;
      },
      onDone: () => {
        void queryClient.invalidateQueries({ queryKey: ['sell-price-history'] });
        void queryClient.invalidateQueries({ queryKey: ['product'] });
        onClose();
      },
    },
  );

  const data = suggestQ.data;

  return (
    <Modal
      open={open}
      title={variant ? `Gợi ý giá bán · ${variant.sku}` : 'Gợi ý giá bán'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Đóng</Button>
          {canApply && data ? (
            <Button variant="primary" loading={apply.isPending} onClick={() => apply.mutate()}>
              Áp dụng (1 kg + 0,5 kg)
            </Button>
          ) : null}
        </>
      }
    >
      {!variant ? null : (
        <div className="space-y-3">
          <p className="text-small text-ink-muted">
            Sau nhập TP, hệ thống gộp tồn cũ + hàng mới → <strong>ĐG bình quân WAC</strong>, rồi × (1 + %
            lời) làm tròn 1.000 ₫.
          </p>
          {suggestQ.isLoading ? (
            <PageLoading />
          ) : suggestQ.error ? (
            <ErrorState error={suggestQ.error} onRetry={suggestQ.refetch} />
          ) : data ? (
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-small">
              <dt className="text-ink-muted">Giá niêm yết hiện tại</dt>
              <dd className="num text-right">{money(data.currentListPrice)}</dd>
              <dt className="text-ink-muted">ĐG WAC / khay</dt>
              <dd className="num text-right">{money(data.unitCost)}</dd>
              <dt className="text-ink-muted">Nguồn</dt>
              <dd className="text-right">{data.costSourceLabel}</dd>
              <dt className="text-ink-muted">% lời</dt>
              <dd className="text-right">
                {data.markupPercent}% <span className="text-ink-faint">({data.markupSource})</span>
              </dd>
              <dt className="font-medium">Giá gợi ý 1 kg</dt>
              <dd className="num text-right text-base font-semibold text-primary">
                {money(data.suggestedListPrice)}
              </dd>
              <dt className="text-ink-muted">Giá 0,5 kg</dt>
              <dd className="num text-right">{money(Math.round(data.suggestedListPrice / 2 / 1000) * 1000)}</dd>
            </dl>
          ) : null}
          {canApply ? (
            <p className="text-small text-ink-faint">
              Áp dụng cập nhật cả khay 1 kg và 0,5 kg (nửa giá). Nhập TP không tự đổi giá.
            </p>
          ) : (
            <p className="text-small text-ink-faint">Chỉ Chủ / Admin được áp dụng giá niêm yết.</p>
          )}
        </div>
      )}
    </Modal>
  );
}
