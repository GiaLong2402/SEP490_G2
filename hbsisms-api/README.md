# hbsisms-api

Backend Spring Boot cho HBSISMS. **Code-first** (entity JPA là nguồn sự thật của schema), PostgreSQL, kiến trúc **3 lớp** (Controller → Service → Repository), tổ chức **theo feature**.

- Java 17 · Spring Boot 3.5.7 · Spring Data JPA (Hibernate 6) · Spring Security (JWT HS256 qua OAuth2 Resource Server)
- Base URL: `http://localhost:8080/api/v1` (khớp `VITE_API_BASE_URL` của `hbsisms-web`)

## Chạy nhanh (DB trên server — `.env` đã cấu hình sẵn)

Chỉ cần **JDK 17+**. `.env` trỏ tới PostgreSQL trên server (`72.61.114.184:5434`, SSL), không cần Docker:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Mở Swagger: http://localhost:8080/api/v1/swagger-ui.html — đăng nhập `POST /auth/login`, bấm **Authorize**, dán `token`.

> `.env` chứa mật khẩu DB và JWT secret thật — không commit, không gửi công khai.

## Chạy với DB local (Docker)

```bash
cp .env.example .env          # rồi điền DB_PASSWORD, JWT_SECRET (openssl rand -base64 48), SEED_ADMIN_PASSWORD
docker compose up -d          # PostgreSQL 16 trên cổng 5432 (đọc cùng file .env)
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Lần chạy đầu Hibernate tự tạo schema `hbsisms` + bảng; seeder tạo 5 vai trò (`admin`, `owner`, `sales`, `warehouse`, `customer`) và tài khoản `admin` với mật khẩu `SEED_ADMIN_PASSWORD` (chỉ khi DB chưa có user nào).

Test (cần Docker, dùng Testcontainers):

```bash
./mvnw test
```

## Cấu trúc

```
com.hbsisms
├── common/                 # dùng chung, không chứa nghiệp vụ
│   ├── exception/          # ApiException, ErrorResponse, GlobalExceptionHandler
│   ├── persistence/        # AuditableEntity (created_at/updated_at)
│   ├── text/               # Strings (chuẩn hóa, escape LIKE)
│   └── web/                # PageResponse {items,total,page,pageSize}, PageRequests
├── config/                 # bean hạ tầng (Clock)
├── security/               # SecurityConfig, JWT encode/decode, AuthUser (principal), xử lý 401/403
└── feature/
    ├── auth/               # POST /auth/login · GET /auth/me · POST /auth/logout · AuthIdentity (Firebase)
    ├── user/               # AppUser + /admin/users (danh sách, tạo, sửa)
    ├── role/               # AppRole + Permission + /admin/roles
    │   — các feature dưới đây mới có entity (bảng), chưa có API —
    ├── customer/           # Customer, CustomerAddress
    ├── catalog/            # Category, Product, ProductVariant, VolumeTier, CustomerPrice
    ├── inventory/          # Warehouse, Location, RawMaterial, Lot, Stock, StockLedger, GoodsReceipt, Stocktake, DamageReport
    ├── sales/              # SalesRequest, Quote, SalesOrder, Delivery, SalesReturn, OrderEvent
    ├── payment/            # Payment, CustomerDebtLedger
    ├── storefront/         # Cart, CartItem, ProductReview, Voucher (cổng B2C)
    ├── approval/           # ApprovalTask
    └── system/             # Notification, AppConfig, AuditLog
```

Mỗi feature: `XxxController` (HTTP, validate) → `XxxService` (nghiệp vụ, transaction) → `XxxRepository` (Spring Data JPA); `dto/` chứa record request/response; entity nằm cùng feature.

**Thêm feature mới** (vd. `customer`): tạo `feature/customer/` với `Customer` (entity), `CustomerRepository`, `CustomerService`, `CustomerController`, `dto/`. Phân quyền bằng `@PreAuthorize("hasAuthority('customers')")` — authority chính là mã quyền trong `app_role.permissions`.

## Auth

| Endpoint | Mô tả |
|---|---|
| `POST /auth/login` `{username, password}` | → `{token, expiresAt, user}`. Sai mật khẩu → **400** (FE coi 401 là hết phiên); tài khoản khóa → 403; sai 5 lần → khóa 15 phút (429) |
| `GET /auth/me` | Người dùng hiện tại + quyền |
| `POST /auth/logout` | 204; thu hồi **mọi** token của user (tăng `token_version`) |

Gửi token qua header `Authorization: Bearer <token>`. Mỗi request nạp lại user từ DB nên khóa tài khoản / đổi vai trò / logout có hiệu lực ngay.

## User & Role (cần quyền `admin`)

| Endpoint | Mô tả |
|---|---|
| `GET /admin/users?q=&roleId=&page=1&pageSize=15` | Danh sách nhân viên (phân trang, tìm theo tên, username, email, mã NV) |
| `POST /admin/users` | Tạo nhân viên (bắt buộc `password`, 8–72 ký tự) → 201 |
| `PUT /admin/users/{id}` | Cập nhật; không thể tự khóa/đổi vai trò chính mình (409); khóa user → thu hồi token của họ |
| `GET /admin/roles` | Vai trò nội bộ (bỏ `customer`) |
| `PUT /admin/roles/{id}` `{permissions: [...]}` | Đổi quyền; vai trò `admin` luôn giữ quyền `admin` |

Lỗi trả về dạng `{status, message, errors?, path, timestamp}` — FE đọc `message`; `errors` là map field → thông báo khi validate thất bại.

## Code-first & môi trường

- `JPA_DDL_AUTO=update` (mặc định) cho dev. Production nên đặt `validate` và áp dụng thay đổi schema bằng script migration đã review (vd. thêm Flyway, sinh DDL từ entity).
- `database/schema.sql` là bản thiết kế tham khảo (DB-first); dự án này **không** chạy file đó — các bảng được sinh từ entity khi thêm dần feature.
- Bộ đếm khóa đăng nhập lưu trong bộ nhớ (theo từng instance). Nếu chạy nhiều instance, chuyển sang Redis/DB.
