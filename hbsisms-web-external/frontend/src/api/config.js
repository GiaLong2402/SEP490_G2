/** Base URL API — mặc định đi qua Vite proxy tới Spring Boot. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/**
 * Dev: bật dữ liệu giả cho các API phía khách chưa có backend.
 * Đặt VITE_USE_MOCK=true trong .env.development.local (file này không commit).
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
