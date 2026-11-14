import { api } from './client'

/** Shop center + geocode / reverse for map address forms. */
export const geoApi = {
  shop(signal) {
    return api.get('/geo/shop', null, signal)
  },
  geocode(address, signal) {
    return api.get('/geo/geocode', { address }, signal)
  },
  reverse(lat, lng, signal) {
    return api.get('/geo/reverse', { lat, lng }, signal)
  },
}
