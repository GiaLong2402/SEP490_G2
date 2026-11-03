import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { stocktakesApi, type StocktakeQuery } from './api';

export const useStocktakes = (query: StocktakeQuery) =>
  useQuery({ queryKey: ['stocktakes', query], queryFn: ({ signal }) => stocktakesApi.list(query, signal), placeholderData: keepPreviousData });
export const useStocktake = (id: ID) => useQuery({ queryKey: ['stocktake', id], queryFn: () => stocktakesApi.get(id) });
