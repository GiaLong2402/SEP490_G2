import { useQuery } from '@tanstack/react-query';
import { warehousesApi } from './api';

export { warehouseRoutes } from './routes';
export type { WarehouseDetail, WarehouseLocation } from './types';
import type { WarehouseDetail } from './types';

export const isRawWarehouse = (w: Pick<WarehouseDetail, 'kind'>) => w.kind === 'raw';

/** Danh sách kho kèm vị trí, sức chứa và số khay đang để (cùng cache với màn Kho và vị trí). */
export const useWarehouses = () => useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });

/** Chỉ kho thành phẩm — dùng cho ô chọn vị trí cất hàng (nhập thành phẩm, kiểm kê…). */
export const useFinishedWarehouses = () =>
  useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list, select: (list) => list.filter((w) => !isRawWarehouse(w)) });

/** Các khu cất (Khu lạnh / Khu khô…) của kho nguyên liệu. */
export const useRawZones = () =>
  useQuery({
    queryKey: ['warehouses'],
    queryFn: warehousesApi.list,
    select: (list) => list.filter((w) => w.isActive && isRawWarehouse(w)).flatMap((w) => w.locations),
  });
