import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { requestsApi, type RequestQuery } from './api';

export const useRequests = (query: RequestQuery) =>
  useQuery({ queryKey: ['requests', query], queryFn: ({ signal }) => requestsApi.list(query, signal), placeholderData: keepPreviousData });

export const useRequest = (id: ID | null | undefined) =>
  useQuery({
    queryKey: ['request', id],
    queryFn: ({ signal }) => requestsApi.get(id!, signal),
    enabled: id != null && Number(id) > 0,
  });
