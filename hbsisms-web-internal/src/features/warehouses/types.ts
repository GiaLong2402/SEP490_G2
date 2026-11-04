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
  /** Tổng sức chứa đã phân bổ cho các vị trí. */
  allocatedCapacity: number;
}

export interface WarehouseInput {
  code: string;
  name: string;
  address: string | null;
  capacity: number;
  isActive: boolean;
}

export interface LocationInput {
  code: string;
  zone: string | null;
  capacity: number;
  note: string | null;
}
