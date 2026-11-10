# styles/shared — style dùng ở từ 2 nơi trở lên

Các file ở đây được nạp **một lần cho cả web** qua `styles/shared/index.css`, import trong `main.jsx` **trước** `App` — nên CSS riêng của trang (nạp sau) luôn được ưu tiên khi muốn chỉnh lại. Trang và component **không import** các file này.

| File | Khối style | Dùng ở |
|---|---|---|
| `summary-box.css` | `.cart-layout`, `.cart-summary*` (bố cục 2 cột + khung tóm tắt đơn) | Giỏ hàng, Thanh toán |
| `result-card.css` | `.checkout-done*`, `.paid-info*`, `.paid-home` (thẻ thông báo giữa trang) | Thanh toán (giỏ trống / chưa đăng nhập), Kết quả thanh toán, `PaymentSuccessCard` |
| `order.css` | `.order-status--<tone>` (chip trạng thái), `.order-card__reason` (ô lý do) | Đơn & Yêu cầu, Chi tiết đơn, Chi tiết yêu cầu |
| `detail-page.css` | `.od-*` (khung trang chi tiết) | Chi tiết đơn, Chi tiết yêu cầu |
| `account.css` | `.account-card`, `.account-toggle*` (thẻ trắng, công tắc) | Tài khoản, Sổ địa chỉ, Cài đặt hiển thị |
| `simple-page.css` | `.simple-page*` | Về Hòa Bình, Liên hệ |

## Quy tắc

- Style **chỉ một trang dùng** → để file `.css` cạnh trang đó, trang tự import.
- Style **từ 2 nơi trở lên** → đặt ở đây và thêm `@import` vào `styles/shared/index.css`. **Không** import CSS của trang khác (`import '../XPage/XPage.css'`).
- Màu, bo góc, khoảng cách lấy từ biến trong `styles/tokens.css` (`var(--color-brand)`…).
- Component tái sử dụng có CSS riêng thì đặt CSS cạnh component (vd. `components/ui/Modal.css`).
