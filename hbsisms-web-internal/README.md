# HBSISMS Web — Quản lý nội bộ Hải sản Hòa Bình

React 18, Vite, TypeScript, TanStack Query, React Router, Tailwind CSS. Đặc tả và phân tích CSDL: [`../_bmad-output/planning-artifacts/hbsisms-noi-bo-ui-spec.md`](../_bmad-output/planning-artifacts/hbsisms-noi-bo-ui-spec.md).

## Chạy

```bash
npm install
npm run dev
```

Mở http://localhost:5180. Mặc định chạy bằng dữ liệu giả lập trong trình duyệt; màn hình đăng nhập có sẵn tài khoản demo theo từng vai trò. Dữ liệu demo lưu ở localStorage, khôi phục tại **Quản trị → Cấu hình** (đăng nhập bằng tài khoản Quản trị).

## Cấu trúc

```
src/
├── app/            # main, router, khung trang, menu, nạp mock
├── shared/
│   ├── api/        # client.ts: điểm gắn API duy nhất; mockServer.ts: router giả lập
│   ├── domain/     # entities.ts: bảng CSDL; enums.ts: enum + nhãn + màu
│   ├── mock/       # CSDL demo + quy tắc nghiệp vụ giả lập
│   ├── ui/         # bộ component dùng chung
│   └── lib/        # định dạng, kiểu route
└── features/<tên>/
    ├── types.ts    # hợp đồng dữ liệu của feature
    ├── api.ts      # các lời gọi REST
    ├── hooks.ts    # TanStack Query
    ├── mock.ts     # handler giả lập, xóa khi có backend
    ├── pages/      # màn hình
    ├── components/ # component riêng của feature
    ├── routes.tsx  # route và quyền
    └── index.ts    # phần công khai; feature khác chỉ import từ đây
```

Quy tắc phụ thuộc: `app → features → shared`. Feature dùng feature khác qua `index.ts`, `shared` không import `features`.

## Gắn API thật

Trang, hook và `api.ts` đã gọi đúng URL REST, nên chỉ cần đổi biến môi trường:

```bash
cp .env.example .env.local
```

```
VITE_API_MODE=http
VITE_API_BASE_URL=https://api.example.com/api/v1
```

Backend cần tuân theo:

- Danh sách endpoint: [`API_ENDPOINTS.md`](API_ENDPOINTS.md). Kiểu dữ liệu: `features/*/types.ts`.
- JSON dạng camelCase. Danh sách trả `{ items, total, page, pageSize }`, nhận `q`, `page`, `pageSize` và bộ lọc.
- `POST /auth/login` trả `{ token, user }`; các request sau gửi `Authorization: Bearer <token>`. Trả 401 sẽ tự đăng xuất.
- Lỗi trả `{ "message": "..." }` bằng tiếng Việt; nội dung này hiển thị trực tiếp cho người dùng.
- Cho phép CORS từ domain của web, hoặc đặt web và API cùng domain.

Backend phải tự thực thi các quy tắc nghiệp vụ và kiểm tra quyền; quy tắc đang nằm ở `shared/mock/services.ts` và `features/*/mock.ts` chỉ để giả lập. Ẩn menu ở frontend không phải là biện pháp bảo mật.

Có thể gắn dần: bản build `http` không chứa dữ liệu giả lập. Khi đã gắn xong toàn bộ, xóa `app/mockServer.ts`, `shared/mock/`, `shared/api/mockServer.ts`, `features/*/mock.ts`, `features/admin/resetMockData.ts`, các tài khoản demo ở `LoginPage` và nhánh mock trong `app/main.tsx`.

## Thêm feature mới

1. Tạo `features/<tên>/` theo cấu trúc trên.
2. Thêm routes vào `app/routes.tsx`, mục menu vào `app/navigation.ts`.
3. Nếu cần giả lập, thêm `import '@/features/<tên>/mock'` vào `app/mockServer.ts`.
