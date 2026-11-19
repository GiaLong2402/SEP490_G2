import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  optimizeDeps: { include: ['@goongmaps/goong-js'] },
  // Chỉ máy này — không mở LAN. OTP/Firebase: http://127.0.0.1:5180
  server: { host: '127.0.0.1', port: 5180 },
});
