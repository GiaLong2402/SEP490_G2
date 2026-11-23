import { useQuery } from '@tanstack/react-query';
import { warehousesApi } from './api';

export { warehouseRoutes } from './routes';
export type { WarehouseDetail, WarehouseLocation } from './types';

/** Danh sách kho kèm vị trí, sức chứa và số khay đang để (cùng cache với màn Kho và vị trí). */
export const useWarehouses = () => useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
