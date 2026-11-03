import { useQuery } from '@tanstack/react-query';
import { posApi } from './api';

// Làm mới định kỳ vì tồn còn thay đổi do nhân viên khác bán hoặc kho nhập thêm.
export const useCatalog = () => useQuery({ queryKey: ['pos-catalog'], queryFn: posApi.catalog, refetchInterval: 60_000 });
