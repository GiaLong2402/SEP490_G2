# Hòa Bình — Frontend (React)

Website bán hải sản **Hòa Bình** (Hải Phòng).  
Tech: **Vite + React + React Router**. Code viết kiểu beginner: rõ ràng, tách phần dùng chung, phân role.

Màu, font, khoảng cách dùng chung nằm trong `src/styles/tokens.css` — luôn dùng biến ở đây (vd: `var(--color-brand)`) thay vì viết mã màu trực tiếp. Ảnh nằm trong `src/assets/` và `public/images/`.

---

## Chạy dự án

```bash
cd frontend
npm install
npm run dev
```

Mở http://localhost:5173

Build production:

```bash
npm run build
npm run preview
```

---

## Cấu trúc thư mục (đọc từ trên xuống)

```text
frontend/
  public/images/demo-products/   # Ảnh sản phẩm demo (dùng chung mọi trang)
  src/
    assets/                      # Banner, họa tiết, ảnh about
    styles/                      # tokens.css + global.css
    constants/                   # roles, routes, categories
    data/                        # demoProducts.js (sau này thay bằng API)
    context/                     # AuthContext, CartContext
    auth/                        # ProtectedRoute, roleUtils
    utils/                       # formatPrice, productFilters
    components/
      ui/                        # Button, Badge
      icons/                     # SVG icons
      storefront/                # Header, Footer, ProductCard, ...
    layouts/                     # StorefrontLayout, StaffLayout
    pages/
      storefront/                # Home, Products, Detail, Cart, Login...
      staff/sale|warehouse/      # Khung sẵn theo role
      owner/  admin/  shared/
    App.jsx                      # Khai báo routes
    main.jsx                     # Provider + CSS
```

### Cách nghĩ khi code thêm màn hình mới

1. **Dữ liệu / hằng số dùng chung** → `constants/` hoặc `data/`
2. **Logic dùng lại** → `utils/` hoặc `context/`
3. **UI nhỏ dùng lại** → `components/ui` hoặc `components/storefront`
4. **Trang bán hàng** → `pages/storefront/...` + dùng `StorefrontLayout`
5. **Trang nội bộ theo role** → `pages/staff|owner|admin` + `StaffLayout` + `ProtectedRoute`

---

## Roles

| Role | Ai dùng | Vào đâu |
|------|---------|---------|
| `guest` | Chưa đăng nhập | Storefront |
| `customer` | Khách hàng | Storefront |
| `sale_staff` | NV bán hàng | `/nv-ban-hang` |
| `warehouse_staff` | NV kho | `/nv-kho` |
| `owner` | Chủ cửa hàng | `/chu-cua-hang` |
| `admin` | Quản trị | `/quan-tri` |

Test nhanh: vào **Đăng nhập** → chọn role demo → hệ thống đưa về đúng khu vực.

---

## Màn hình đã có

- Trang chủ (`/`)
- Danh sách sản phẩm (`/san-pham`) — bộ lọc, sort, phân trang
- Chi tiết sản phẩm (`/san-pham/:slug`) — quy cách, số lượng, đặt số lượng lớn, tab, liên quan
- Giỏ hàng, Về Hòa Bình, Liên hệ, Đăng nhập demo
- Dashboard khung sẵn cho sale / kho / owner / admin

---

## Nối Spring Boot sau này

1. API thật thay `src/data/demoProducts.js`
2. Login thật thay `loginDemo` trong `AuthContext`
3. Proxy sẵn trong `vite.config.js`: `/api` → `http://localhost:8080`

Ví dụ sau này:

```js
const res = await fetch('/api/products')
const products = await res.json()
```

---

## Design tokens

Màu / spacing nằm ở `src/styles/tokens.css` (`--color-brand`, `--color-action`, ...).  
Đổi 1 chỗ là toàn site cập nhật.
