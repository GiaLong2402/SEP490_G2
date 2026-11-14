import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// Vite config — alias @ trỏ vào thư mục src để import ngắn gọn
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  optimizeDeps: {
    include: ['@goongmaps/goong-js'],
  },
  server: {
    // Chỉ máy này — không mở LAN (192.168.x.x). OTP/Firebase: mở http://127.0.0.1:5173
    host: '127.0.0.1',
    port: 5173,
    // Sau này gọi Spring Boot qua proxy (vd: /api -> http://localhost:8080)
    proxy: {
      '/api': {
        // Spring Boot mặc định :8080 (xem hbsisms-api application.yml)
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
