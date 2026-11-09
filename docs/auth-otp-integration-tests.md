# Integration test API đăng nhập OTP (Inter 2 — task 5, phần IT)

File bàn giao IT module **Đăng nhập OTP** (Khôi). Verifier **giả** (`stub.<uid>.<phone>`) — không gọi Firebase thật.

**Yêu cầu chạy:** Docker Desktop đang bật (Testcontainers Postgres).

**Nhánh gợi ý:** `feature/api-auth-otp-tests`

---

## Phần Khôi — đã làm (OTP login / register / logout / admin password)

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/AuthOtpApiIntegrationTest.java`

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=AuthOtpApiIntegrationTest"
```

Kết quả gần nhất (khi Docker bật): chạy lệnh trên rồi cập nhật dòng này — **8 tests, 0 failures** (BUILD SUCCESS) kỳ vọng.

| Test method | Hành vi (map sheet task 5) | Kết quả |
|-------------|----------------------------|---------|
| `otpLogin_provisionedStaffPhone_returns200` | Số đã cấp (NV) → 200 + JWT role warehouse | *chạy khi Docker on* |
| `otpLogin_unknownStaffPhone_returns403` | Số lạ → 403 chưa cấp TK | *chạy khi Docker on* |
| `otpRegister_newCustomer_returns201WithCustomerRole` | Khách mới → 201 + role customer | *chạy khi Docker on* |
| `otpRegister_linksWalkInCustomerByPhone` | Liên kết khách tạo tại quầy (cùng SĐT) | *chạy khi Docker on* |
| `otpLogin_lockedStaff_returns403` | Tài khoản khóa → 403 | *chạy khi Docker on* |
| `otpLogin_thenLogout_revokesToken` | Logout thu hồi token (me → 401) | *chạy khi Docker on* |
| `passwordLogin_admin_stillWorks` | Admin vẫn đăng nhập mật khẩu được | *chạy khi Docker on* |
| `otpRegister_duplicatePhone_returns409` | Đăng ký trùng SĐT → 409 | *chạy khi Docker on* |

**Ghi chú môi trường:** lúc viết docs, Docker engine chưa sẵn → IT chưa chạy được trong phiên đó. Bật Docker Desktop rồi chạy lệnh trên; nếu **8/8 pass** thì đổi cột Kết quả thành `pass` và sửa dòng “Kết quả gần nhất”.

---

## Map checklist sheet Inter 2 task 5

| Tiêu chí sheet | Cover bởi |
|----------------|-----------|
| Số đã cấp → 200 | IT `otpLogin_provisionedStaffPhone_returns200` + unit OtpLogin |
| Số lạ NV → 403 | IT `otpLogin_unknownStaffPhone_returns403` + unit |
| Khách mới → role customer | IT `otpRegister_newCustomer_…` + unit Register |
| Liên kết khách quầy | IT `otpRegister_linksWalkInCustomerByPhone` + unit |
| Tài khoản khóa → 403 | IT `otpLogin_lockedStaff_returns403` + unit |
| Logout thu hồi token | IT `otpLogin_thenLogout_revokesToken` |
| Admin vẫn login mật khẩu | IT `passwordLogin_admin_stillWorks` + unit task 4 |

---

## Đóng tracking

1. Khôi — unit: `docs/auth-otp-unit-tests.md` ✅  
2. Khôi — IT: mục trong file này (pass khi Docker on)  
