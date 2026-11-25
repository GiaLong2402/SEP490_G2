import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { quotesApi, type QuoteQuery } from './api';

export const useQuotes = (query: QuoteQuery) =>
  useQuery({ queryKey: ['quotes', query], queryFn: ({ signal }) => quotesApi.list(query, signal), placeholderData: keepPreviousData });
export const useQuote = (id: ID | null | undefined) =>
  useQuery({ queryKey: ['quote', id], queryFn: () => quotesApi.get(id!), enabled: id != null && id > 0 });
