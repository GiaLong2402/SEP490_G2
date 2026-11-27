import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAction } from '@/shared/api/useAction';
import { money } from '@/shared/lib/format';
import { Button, ErrorState, InfoTip, Modal, PageLoading } from '@/shared/ui';
import { productsApi } from '../api';
import type { VariantWithTiers } from '../types';

function halfKgPrice(oneKg: number) {
  return Math.round(oneKg / 2 / 1000) * 1000;
}

/** Gợi ý giá bán theo WAC + % lời — chỉ đổi listPrice khi bấm Áp dụng. */
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
      success: (r) =>
        `Đã áp dụng 1 kg ${money(r.suggestedListPrice)} · 0,5 kg ${money(halfKgPrice(r.suggestedListPrice))}`,
      onDone: () => {
        void queryClient.invalidateQueries({ queryKey: ['sell-price-history'] });
        void queryClient.invalidateQueries({ queryKey: ['product'] });
        onClose();
      },
    },
  );

  const data = suggestQ.data;
  const half = data ? halfKgPrice(data.suggestedListPrice) : null;

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
              Áp dụng cả 2 quy cách
            </Button>
          ) : null}
        </>
      }
    >
      {!variant ? null : suggestQ.isLoading ? (
        <PageLoading />
      ) : suggestQ.error ? (
        <ErrorState error={suggestQ.error} onRetry={suggestQ.refetch} />
      ) : data ? (
        <div className="space-y-4">
          <div className="flex items-center gap-1.5 text-small text-ink-muted">
            <span>
              WAC / 1 kg + {data.markupPercent}% lời
              {data.packLabel ? ` · neo ${data.packLabel}` : ''}
            </span>
            <InfoTip label="Cách tính giá gợi ý">
              <span className="block">
                Đơn giá vốn bình quân (WAC) hiện hành × (1 + % lời), làm tròn 1.000 ₫ theo khay 1 kg.
                Khay 0,5 kg = nửa giá. Chỉ đổi giá niêm yết khi bấm Áp dụng.
              </span>
            </InfoTip>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-md border border-primary/25 bg-primary-soft/40 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 1 kg</div>
              <div className="mt-1 text-h2 tabular-nums text-primary">{money(data.suggestedListPrice)}</div>
              <div className="mt-1 text-small text-ink-faint">
                Đang bán {money(data.currentListPrice)}
              </div>
            </div>
            <div className="rounded-md border border-line bg-rowalt/60 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 0,5 kg</div>
              <div className="mt-1 text-h2 tabular-nums text-ink">{money(half)}</div>
              <div className="mt-1 text-small text-ink-faint">= nửa giá 1 kg</div>
            </div>
          </div>

          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 border-t border-line pt-3 text-small">
            <dt className="text-ink-muted">Giá vốn WAC / 1 kg</dt>
            <dd className="num text-right text-ink">{money(data.unitCost)}</dd>
            <dt className="text-ink-muted">% lời</dt>
            <dd className="text-right text-ink">
              {data.markupPercent}%
              <span className="text-ink-faint"> · {data.markupSource}</span>
            </dd>
          </dl>

          {!canApply && (
            <p className="text-small text-ink-faint">Chỉ Chủ / Admin được áp dụng giá niêm yết.</p>
          )}
        </div>
      ) : null}
    </Modal>
  );
}
