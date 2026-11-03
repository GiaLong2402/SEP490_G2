import { api } from '@/shared/api/client';
import type { CatalogItem, CheckoutInput, Receipt } from './types';

export const posApi = {
  catalog: () => api.get<CatalogItem[]>('/pos/catalog'),
  checkout: (body: CheckoutInput) => api.post<Receipt>('/pos/checkout', body),
};
