import type { Location, Warehouse } from '@/shared/domain/entities';

export interface WarehouseLocation extends Location {
  /** Tổng số khay đang nằm tại vị trí. */
  usedQty: number;
  /** Không xóa được khi vị trí còn dòng tồn (stock.location_id là ON DELETE RESTRICT). */
  canDelete: boolean;
}

export interface WarehouseDetail extends Warehouse {
  locations: WarehouseLocation[];
  totalQty: number;
}

export interface WarehouseInput {
  code: string;
  name: string;
  address: string | null;
  isActive: boolean;
}

export interface LocationInput {
  code: string;
  zone: string | null;
  capacity: number | null;
  note: string | null;
}
