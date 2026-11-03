export const API_MODE = import.meta.env.VITE_API_MODE === 'http' ? 'http' : 'mock';
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';
export const isMockMode = API_MODE === 'mock';
