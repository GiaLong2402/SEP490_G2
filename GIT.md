# Quy tắc Git — SEP490_G2

Repo monorepo cho hệ thống quản lý bán hàng và kho hải sản Hòa Bình (Hải Phòng).

## Cấu trúc repo

| Thư mục | Vai trò |
|---------|---------|
| `hbsisms-api` | Backend / business logic |
| `hbsisms-web-internal` | App nội bộ: NV kho, NV bán hàng, chủ cửa hàng, admin |
| `hbsisms-web-external` | Web bán hàng: guest & customer |

Nhánh `main` luôn giữ code ổn định. Deploy/demo lấy từ `main`.

## Workflow

Nhóm dùng **GitHub Flow**: nhánh ngắn theo từng việc + **bắt buộc Pull Request** vào `main`.

```text
main → tạo nhánh → commit → push nhánh → mở PR → review → merge → xóa nhánh
```

Ruleset trên GitHub đã bảo vệ `main`: không push thẳng, bắt buộc PR và ít nhất 1 approval.

## Đặt tên nhánh

```text
feature/<module>-<ten-ngan>
fix/<module>-<ten-ngan>
chore/<ten-ngan>
```

- **module:** `api` | `internal` | `external` | `root`
- Ví dụ:
  - `feature/api-auth-login`
  - `feature/internal-pos-cart`
  - `feature/external-product-list`
  - `fix/api-stock-qty`
  - `chore/add-git-rules`

Không dùng nhánh dài theo người (`duong`, `long`…) hay theo cả module (`branch-api`). Mỗi việc một nhánh, merge xong thì xóa.

## Lệnh làm việc (PowerShell)

```powershell
cd "e:\Semester 9\SEP490_G2"

git checkout main
git pull origin main
git checkout -b feature/api-auth-login

# ... sửa code ...

git add .
git status
git commit -m "feat(api): add login rate limiting"
git push -u origin feature/api-auth-login
```

Sau đó mở Pull Request trên GitHub: **base `main`** ← **compare nhánh của bạn**.

Hoặc dùng GitHub CLI:

```powershell
gh pr create --base main --title "feat(api): add login rate limiting" --body "## Summary`n- ...`n`n## Test plan`n- [ ] ..."
```

## Pull Request

- Ít nhất **1 người approve** trước khi merge
- Mô tả: mục đích, module ảnh hưởng, cách test
- Ưu tiên **Squash and merge** để lịch sử `main` gọn
- Không merge khi còn conflict
- Sau khi merge: xóa nhánh trên GitHub và local

```powershell
git checkout main
git pull origin main
git branch -d feature/api-auth-login
```

## Commit message

Format: `type(scope): message`

| type | Khi nào |
|------|---------|
| `feat` | Tính năng mới |
| `fix` | Sửa lỗi |
| `chore` | Config, docs phụ, dọn dẹp |
| `docs` | Tài liệu |
| `refactor` | Đổi code không đổi hành vi |
| `test` | Thêm/sửa test |

- **scope:** `api` | `internal` | `external` | `root`
- Ví dụ: `feat(internal): improve POS cart`

## Cấm

- Không `git push origin main`
- Không `--force` lên `main`
- Không commit `.env`, secret, `node_modules/`, `target/`
- Không commit file binary lớn không cần thiết

## Không commit / push hộ

Mỗi thành viên **tự** `add` / `commit` / `push` / mở PR trên máy và tài khoản GitHub của mình.

- Thành viên khác hoặc AI/Cursor **không** chạy commit/push thay
- Khi hỗ trợ: chỉ **hướng dẫn bước** và **đưa nguyên lệnh** để người đó tự chạy

Lý do: đúng author trên GitHub, tránh nhầm quyền/token, mọi người nắm quy trình.

```text
Đúng: đưa lệnh để tự chạy
Sai:  commit hoặc push thay trên máy/tài khoản người khác
```

## Đồng bộ & conflict

Trước khi mở hoặc merge PR, cập nhật `main` vào nhánh của bạn:

```powershell
git fetch origin
git checkout feature/api-auth-login
git merge origin/main
# nếu conflict: sửa file → git add . → git commit
git push
```

Conflict chỉ sửa trên nhánh feature, không sửa trực tiếp trên `main`.

## Checklist trước khi mở PR

- [ ] Đang làm trên nhánh feature/fix/chore, không phải `main`
- [ ] Đã kéo `main` mới nhất và xử lý conflict (nếu có)
- [ ] Không có `.env` hoặc secret trong commit
- [ ] Tự commit/push bằng tài khoản của mình (không nhờ commit hộ)
- [ ] Đã mở PR vào `main` và có ít nhất 1 approve trước khi merge
