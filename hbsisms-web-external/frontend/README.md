# Hòa Bình — Frontend (React)

Website bán hải sản **Hòa Bình** (Hải Phòng).  
Tech: **Vite + React + React Router**. Code viết kiểu beginner: rõ ràng, tách phần dùng chung.

**Web này chỉ dành cho khách vãng lai (guest) và khách hàng (customer).** Nhân viên bán hàng, nhân viên kho, chủ cửa hàng, admin làm việc ở web nội bộ `hbsisms-web-internal`.

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

## Nối API backend

- Danh sách API phía khách (địa chỉ, đơn của tôi, yêu cầu, checkout, PayOS), JSON mẫu và checklist kiểm tra: **[`API_CONTRACT.md`](API_CONTRACT.md)**.
- Trang **không** gọi URL trực tiếp: trang → `src/api/<domain>.js` (URL) → `src/api/mappers/` (đổi tên field). Nối API chỉ sửa 2 chỗ này.
- Chạy thử khi backend chưa có: tạo `.env.development.local` với `VITE_USE_MOCK=true` rồi `npm run dev` (mock nằm ở `src/api/mock/`, bản build không chứa).
- Khi dev, backend trả thiếu field → Console báo `[API] … thiếu field …`.

---

## Cấu trúc thư mục (đọc từ trên xuống)

```text
frontend/
  public/images/demo-products/   # Ảnh sản phẩm demo (dùng chung mọi trang)
  src/
    assets/                      # Banner, họa tiết, ảnh about
    styles/                      # tokens.css + global.css
    constants/                   # roles, routes, categories
    api/                         # Gọi backend: <domain>.js + mappers/ (+ mock/ chỉ khi dev)
    hooks/                       # useAsync, useStoreCatalog
    context/                     # AuthContext, CartContext
    auth/                        # mapRole, roleUtils, đăng nhập OTP
    utils/                       # formatPrice, productFilters
    components/
      ui/                        # Button, Badge
      icons/                     # SVG icons
      storefront/                # Header, Footer, ProductCard, ...
    layouts/                     # StorefrontLayout, AuthLayout
    pages/
      storefront/                # Home, Products, Detail, Cart, Login...
      shared/                    # NotFound
    App.jsx                      # Khai báo routes
    main.jsx                     # Provider + CSS
```

### Cách nghĩ khi code thêm màn hình mới

1. **Hằng số dùng chung** → `constants/`; **gọi API** → `api/<domain>.js` (không gọi fetch trong trang)
2. **Logic dùng lại** → `utils/` hoặc `context/`
3. **UI nhỏ dùng lại** → `components/ui` hoặc `components/storefront`
4. **Trang bán hàng** → `pages/storefront/...` + dùng `StorefrontLayout`
5. **Không thêm trang cho nhân viên ở đây** — làm ở `hbsisms-web-internal`

---

## Roles

| Role | Ai dùng | Vào đâu |
|------|---------|---------|
| `guest` | Chưa đăng nhập | Storefront |
| `customer` | Khách hàng | Storefront |
| `sale_staff`, `warehouse_staff`, `owner`, `admin` | Tài khoản nội bộ | Không dùng web này — đăng nhập sẽ báo "Tài khoản không tồn tại." |

---

## Màn hình đã có

- Trang chủ (`/`)
- Danh sách sản phẩm (`/san-pham`) — bộ lọc, sort, phân trang
- Chi tiết sản phẩm (`/san-pham/:slug`) — quy cách, số lượng, đặt số lượng lớn, tab, liên quan
- Giỏ hàng, Về Hòa Bình, Liên hệ, Đăng nhập OTP
- Thanh toán 4 bước (`/thanh-toan`) + kết quả PayOS (`/thanh-toan/ket-qua`)
- Tài khoản, Sổ địa chỉ, Cài đặt hiển thị
- Đơn & Yêu cầu (`/don-hang`), chi tiết đơn, biên lai, chi tiết yêu cầu (`/yeu-cau/:code`)

---

## Nối Spring Boot

- Sản phẩm, danh mục, đăng nhập OTP: đã gọi API thật (`src/api/products.js`, `src/api/auth.js`).
- API phía khách (địa chỉ, đơn, yêu cầu, checkout, PayOS): xem mục **Nối API backend** ở trên và [`API_CONTRACT.md`](API_CONTRACT.md).
- Proxy sẵn trong `vite.config.js`: `/api` → `http://localhost:8080`.

---

## Design tokens

Màu / spacing nằm ở `src/styles/tokens.css` (`--color-brand`, `--color-action`, ...).  
Đổi 1 chỗ là toàn site cập nhật.
