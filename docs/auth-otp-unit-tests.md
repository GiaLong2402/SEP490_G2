# Unit test đăng nhập OTP (Inter 2 — task 5, phần unit)

File bàn giao / báo cáo unit test module **Đăng nhập OTP** (Khôi). Verifier **giả** (`StubFirebaseTokenVerifier`) — không gọi Firebase thật.

**Nhánh gợi ý:** `feature/api-auth-otp-tests`

---

## Phần Khôi — đã làm + báo cáo

**Mục tiêu:** cover login mật khẩu (kèm siết admin-only), OTP login/register (stub), stub token parse.

**Lệnh chạy phần Khôi**

```powershell
cd hbsisms-api
.\mvnw.cmd test "-Dtest=AuthServiceTest,AuthServiceOtpLoginTest,AuthServiceOtpRegisterTest,StubFirebaseTokenVerifierTest,LoginAttemptServiceTest"
```

Kết quả gần nhất: **23 tests, 0 failures** (BUILD SUCCESS) — AuthService **9** + OtpLogin **5** + OtpRegister **4** + StubVerifier **2** + LoginAttempt **3**.

### AuthServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/AuthServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `login_validCredentials_returnsTokenAndRecordsLogin` | username/password đúng → JWT + lastLogin | pass |
| `login_wrongPassword_returns400AndKeepsAttemptCounted` | sai mật khẩu → 400 | pass |
| `login_unknownUser_stillChecksDummyHashAndFails` | user không tồn tại → 400 (không lộ username) | pass |
| `login_passwordOver72Bytes_failsWithoutCallingBcrypt` | password quá dài → 400 | pass |
| `login_lockedAccount_returns403WithoutIssuingToken` | isActive=false → 403 | pass |
| `login_tooManyAttempts_returns429WithoutQueryingUser` | quá số lần thử → 429 | pass |
| `login_nonAdmin_whenPasswordLoginRestricted_returns403` | ALL_ROLES=false + sales → 403 (dùng OTP) | pass |
| `login_admin_whenPasswordLoginRestricted_stillSucceeds` | ALL_ROLES=false + admin → vẫn JWT | pass |
| `logout_incrementsTokenVersionAtomically` | logout → tăng tokenVersion | pass |

### AuthServiceOtpLoginTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/AuthServiceOtpLoginTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `loginWithOtp_existingIdentity_issuesJwtAndUpdatesTimestamps` | đã có auth_identity → 200 + cập nhật thời gian | pass |
| `loginWithOtp_noIdentity_linksByPhoneThenIssuesJwt` | SĐT đã cấp (NV) → link identity + JWT | pass |
| `loginWithOtp_unknownPhone_returns403WithoutCreatingUser` | SĐT lạ → 403, không tạo user | pass |
| `loginWithOtp_lockedAccount_returns403` | tài khoản khóa → 403 | pass |
| `loginWithOtp_userAlreadyLinkedToOtherFirebase_returns409` | user đã link uid khác → 409 | pass |

### AuthServiceOtpRegisterTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/AuthServiceOtpRegisterTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `registerWithOtp_newCustomer_createsUserAndB2c` | khách mới → user customer + KH B2C | pass |
| `registerWithOtp_walkInCustomer_linksExistingProfile` | khách quầy cùng SĐT → link account_user + audit | pass |
| `registerWithOtp_phoneAlreadyHasAccount_returns409` | SĐT đã có TK → 409 | pass |
| `registerWithOtp_firebaseAlreadyLinked_returns409` | firebase uid đã đăng ký → 409 | pass |

### StubFirebaseTokenVerifierTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/firebase/StubFirebaseTokenVerifierTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `verify_validStubToken_returnsUidAndPhone` | `stub.uid.phone` → uid + phone | pass |
| `verify_invalidToken_returns400` | token sai format → 400 | pass |

### LoginAttemptServiceTest

File: `hbsisms-api/src/test/java/com/hbsisms/feature/auth/LoginAttemptServiceTest.java`

| Test method | Hành vi | Kết quả |
|-------------|---------|---------|
| `blocksAfterMaxAttempts` | đủ lần sai → chặn | pass |
| `unlocksAfterLockDurationWithAFreshWindow` | hết thời gian khóa → mở lại | pass |
| `successClearsAttempts` | login thành công → xóa đếm | pass |

---

## Đóng tracking unit (task 5 — phần unit)

1. Khôi — mục trong file này ✅  
2. IT concurrent: xem `docs/auth-otp-integration-tests.md`
