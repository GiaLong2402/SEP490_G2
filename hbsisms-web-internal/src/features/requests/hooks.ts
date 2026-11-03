import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { requestsApi, type RequestQuery } from './api';

export const useRequests = (query: RequestQuery) =>
  useQuery({ queryKey: ['requests', query], queryFn: ({ signal }) => requestsApi.list(query, signal), placeholderData: keepPreviousData });
