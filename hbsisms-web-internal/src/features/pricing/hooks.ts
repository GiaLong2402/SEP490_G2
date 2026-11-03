import { useQuery } from '@tanstack/react-query';
import { pricingApi } from './api';
import type { PricingRequest } from './types';

/** Xem trước giá do máy chủ tính; form chỉ truyền các dòng đã nhập đủ. */
export function usePricing(request: PricingRequest) {
  const enabled = request.lines.length > 0;
  return useQuery({
    queryKey: ['pricing', request],
    queryFn: () => pricingApi.resolve(request),
    enabled,
    placeholderData: (previous) => (enabled ? previous : undefined),
  });
}
