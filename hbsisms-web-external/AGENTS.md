# Hướng dẫn code — web external (Hải sản Hòa Bình)

File này dành cho người và AI hỗ trợ code (Cursor, Claude…) khi làm việc trong `hbsisms-web-external`. Đọc trước khi sửa code.

Web bán hàng cho **khách vãng lai (guest) và khách hàng (customer)**. Code trong `frontend/`. React 19 + Vite, **JavaScript (không TypeScript)**, CSS thuần (không Tailwind). Giao diện tiếng Việt.

## 1. Phạm vi
- **Không** thêm trang, route, menu hay chức năng cho nhân viên / chủ cửa hàng / admin. Những việc đó làm ở `hbsisms-web-internal`.
- Tài khoản nội bộ đăng nhập vào đây bị từ chối "Tài khoản không tồn tại." (`context/AuthContext.jsx`) — không sửa hành vi này.

## 2. Cấu trúc thư mục (`frontend/src`)
- `pages/storefront/<Tên>Page/` — mỗi trang một thư mục: `<Tên>Page.jsx` + `<Tên>Page.css`; component riêng của trang để trong thư mục đó.
- `components/ui/` — khối dùng chung: `Button`, `Modal`, `LoadState`, `Badge`, `ImageLightbox`.
- `components/storefront/` — khối dùng chung của web bán hàng: `AccountShell` (khung trang tài khoản + sidebar), `PageBanner`, `Pagination`, `AddressFormDialog`, `CancelOrderDialog`, `PaymentSuccessCard`, Header, Footer…
- `components/icons/Icons.jsx` — **toàn bộ icon (SVG)**. Cần icon mới thì thêm vào đây, không dùng ảnh PNG làm icon, không cài thư viện icon.
- `constants/` — `routes.js` (mọi đường dẫn + hàm tạo link như `orderDetailPath`), `orderStatus.js` (nhãn/màu trạng thái), `roles.js`.
- `api/`, `hooks/`, `context/`, `utils/` (định dạng giá, ngày, địa chỉ…), `styles/`.
- Route khai báo trong `App.jsx`; đường dẫn lấy từ `ROUTES`, không gõ chuỗi tay.

## 3. Gọi API — bắt buộc theo lớp này
```
Trang  →  hooks/useAsync  →  api/<domain>.js  →  backend (hoặc mock khi VITE_USE_MOCK=true)
                                  ↓
                     api/mappers/<domain>.js   (DTO backend → dữ liệu cho giao diện)
```
- Trang/component **không gọi `fetch` hay `api.get` trực tiếp**. Dùng hàm trong `api/<domain>.js` (`ordersApi`, `addressesApi`, `salesRequestsApi`, `checkoutApi`, `paymentsApi`, `productsApi`, `authApi`).
- Backend đổi URL → sửa `api/<domain>.js`. Backend đổi tên field → sửa `api/mappers/<domain>.js`. Không sửa trang vì lý do này.
- Mapper mới: khai báo field bắt buộc và gọi `checkDto(...)` (báo `[API] … thiếu field` trên Console khi dev).
- Tải dữ liệu trong trang: `const { data, loading, error, reload } = useAsync((signal) => xxxApi.list(signal), [deps])` và bọc nội dung bằng `<LoadState loading error onRetry empty emptyText>`.
- Lỗi API là `ApiError` (`status`, `message`, `payload`): hiện `message` cho khách; lỗi từng ô form nằm ở `payload.errors`.
- Thêm/đổi endpoint phía khách → **cập nhật `frontend/API_CONTRACT.md`** (URL, JSON, lỗi, người phụ trách) và dữ liệu mock tương ứng.
- **Không** đặt dữ liệu giả trong trang. Cần chạy thử khi chưa có backend: thêm vào `api/mock/` (chỉ chạy khi `VITE_USE_MOCK=true`, bản build không chứa).
- Nhãn trạng thái đơn / yêu cầu lấy từ `constants/orderStatus.js` (`ORDER_STATUS`, `REQUEST_STATUS`, `orderUiStatus`) — không tự đặt nhãn trong trang.

## 4. Giao diện & CSS
- Màu, bo góc, bóng, khoảng cách: **dùng biến trong `styles/tokens.css`** (`var(--color-brand)`, `--color-accent`, `--color-action`, `--color-price`, `--color-muted`, `--color-border`, `--color-surface`, `--color-danger`, `--color-success`, `--radius-pill`…). Không thêm màu thương hiệu mới.
- Style **chỉ một trang dùng** → file `.css` cạnh trang, trang tự import.
- Style **dùng từ 2 nơi trở lên** → `styles/shared/<khối>.css` + thêm `@import` vào `styles/shared/index.css` (xem `styles/shared/README.md`). **Không** `import '../XPage/XPage.css'` từ trang khác.
- Tên class theo kiểu BEM có tiền tố của trang/khối: `.checkout-card__title`, `.order-status--warning`. Trạng thái dùng `.is-active`, `.is-open`…
- Dùng lại trước khi viết mới: nút → `Button` (`variant="primary|ghost|danger|navy|outline"`, `pill`); hộp thoại / xác nhận → `Modal` (không dùng `window.confirm`/`alert`); trang tài khoản → `AccountShell`; phân trang → `Pagination`; chip trạng thái → class `.order-status order-status--<tone>`.
- Mọi màn phải dùng được ở **mobile 390px** (không tràn ngang) và máy tính 1440px.
- Chữ trên giao diện: tiếng Việt có dấu, ngắn gọn; giá dùng `formatPrice`, ngày dùng `utils/date.js` (`formatDate`, `formatDateTime`, `formatSchedule`).

## 5. Trước khi xong việc
Chạy trong `frontend/`:
```bash
npm run build
npx oxlint src
```
- Build không lỗi, lint không thêm cảnh báo mới.
- Mở màn vừa sửa ở 1440px và 390px; nếu có gọi API thì xem Console không còn dòng `[API]`.
- Không commit `.env`, `.env.development.local`.

## 6. Git (làm theo `GIT.md` ở gốc repo)
- Mỗi task một nhánh (`feature/external-<việc>`), không để AI commit hộ, không commit thẳng lên `main`; mở PR vào `main`, cần ít nhất 1 approve rồi mới merge, merge xong xóa nhánh.
- Commit dạng `feat(external): …` / `fix(external): …`.
