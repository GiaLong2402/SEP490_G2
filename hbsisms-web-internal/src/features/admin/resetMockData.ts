/** Import động để bản build gọi backend thật không kéo theo dữ liệu giả lập. */
export async function resetMockData() {
  if (import.meta.env.VITE_API_MODE === 'http') return;
  const { resetDb } = await import('@/shared/mock/db');
  resetDb();
  window.location.reload();
}
