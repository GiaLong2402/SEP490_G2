import { api } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { PricingRequest, PricingResult } from './types';

export const pricingApi = {
  resolve: (body: PricingRequest) => api.post<PricingResult>(API_PATHS.pricing.resolve, body),
};
