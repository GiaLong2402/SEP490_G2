import { api } from './client';

export interface GeoShop {
  latitude: number;
  longitude: number;
  deliveryRadiusKm: number;
}

export interface GeoLocate {
  latitude: number;
  longitude: number;
  distanceKm: number;
  inDeliveryRadius: boolean;
}

export interface GeoReverse {
  latitude: number;
  longitude: number;
  distanceKm: number;
  inDeliveryRadius: boolean;
  line1: string | null;
  ward: string | null;
  district: string | null;
  province: string | null;
  formattedAddress: string | null;
}

export const geoApi = {
  shop: (signal?: AbortSignal) => api.get<GeoShop>('/geo/shop', undefined, signal),
  geocode: (address: string, signal?: AbortSignal) =>
    api.get<GeoLocate>('/geo/geocode', { address }, signal),
  reverse: (lat: number, lng: number, signal?: AbortSignal) =>
    api.get<GeoReverse>('/geo/reverse', { lat, lng }, signal),
};
