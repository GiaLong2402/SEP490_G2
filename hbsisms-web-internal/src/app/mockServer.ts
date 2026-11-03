// Chỉ được nạp khi VITE_API_MODE=mock. Khi xóa mock: xóa file này, shared/mock và các features/*/mock.ts.
import { createMockTransport } from '@/shared/api/mockServer';
import { saveDb } from '@/shared/mock/db';
import '@/features/admin/mock';
import '@/features/approvals/mock';
import '@/features/auth/mock';
import '@/features/customers/mock';
import '@/features/damage/mock';
import '@/features/dashboard/mock';
import '@/features/debt/mock';
import '@/features/deliveries/mock';
import '@/features/inventory/mock';
import '@/features/marketing/mock';
import '@/features/notifications/mock';
import '@/features/orders/mock';
import '@/features/payments/mock';
import '@/features/pos/mock';
import '@/features/pricing/mock';
import '@/features/products/mock';
import '@/features/quotes/mock';
import '@/features/receipts/mock';
import '@/features/reports/mock';
import '@/features/requests/mock';
import '@/features/returns/mock';
import '@/features/stocktakes/mock';
import '@/features/suppliers/mock';
import '@/features/warehouses/mock';

export const mockTransport = createMockTransport(saveDb);
