import { api } from '@/shared/api/client';
import type { PricingRequest, PricingResult } from './types';

export const pricingApi = {
  resolve: (body: PricingRequest) => api.post<PricingResult>('/pricing/resolve', body),
};
