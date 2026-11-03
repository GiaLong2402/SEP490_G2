import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { deliveriesApi, type DeliveryQuery } from './api';

export const useDeliveries = (query: DeliveryQuery) =>
  useQuery({ queryKey: ['deliveries', query], queryFn: ({ signal }) => deliveriesApi.list(query, signal), placeholderData: keepPreviousData });
export const useCouriers = () => useQuery({ queryKey: ['couriers'], queryFn: deliveriesApi.couriers });
