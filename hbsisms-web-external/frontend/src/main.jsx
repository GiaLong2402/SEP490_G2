import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Style dùng chung nạp trước App: CSS riêng của trang (nạp sau) được ưu tiên hơn
import '@/styles/shared/index.css'
import App from './App.jsx'
import { AuthProvider } from '@/context/AuthContext'
import { CartProvider } from '@/context/CartContext'
import { ToastProvider } from '@/context/ToastContext'
import '@/styles/global.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <CartProvider>
        <ToastProvider>
          <App />
        </ToastProvider>
      </CartProvider>
    </AuthProvider>
  </StrictMode>,
)
