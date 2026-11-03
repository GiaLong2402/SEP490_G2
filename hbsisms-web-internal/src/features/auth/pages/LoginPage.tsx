import { useState, type FormEvent } from 'react';
import { Fish } from 'lucide-react';
import { Navigate, useLocation } from 'react-router-dom';
import { isMockMode } from '@/shared/api/config';
import { Alert, Button, Field, Input } from '@/shared/ui';
import { useAuth } from '../context';

const DEMO_ACCOUNTS = [
  ['owner', 'Chủ cửa hàng'],
  ['sales1', 'Bán hàng'],
  ['kho1', 'Thủ kho'],
  ['giao1', 'Giao hàng'],
  ['admin', 'Quản trị'],
] as const;

export default function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/'} replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    if (!username.trim() || !password) return;
    setBusy(true);
    setError(null);
    try {
      await login({ username, password });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không đăng nhập được');
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="flex min-h-full items-center justify-center bg-sidebar p-4">
      <div className="w-full max-w-[380px] rounded-lg bg-card p-6 shadow-pop">
        <div className="mb-5 flex items-center gap-2.5">
          <span className="flex h-10 w-10 items-center justify-center rounded-md bg-primary text-white">
            <Fish size={22} aria-hidden />
          </span>
          <div>
            <h1 className="text-h1">Hải sản Hòa Bình</h1>
            <p className="text-small text-ink-muted">Hệ thống quản lý kho và bán hàng</p>
          </div>
        </div>
        <form onSubmit={submit} noValidate className="space-y-3">
          {error && <Alert tone="danger">{error}</Alert>}
          <Field label="Tên đăng nhập" required error={touched && !username.trim() ? 'Vui lòng nhập tên đăng nhập' : undefined}>
            <Input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
          </Field>
          <Field label="Mật khẩu" required error={touched && !password ? 'Vui lòng nhập mật khẩu' : undefined}>
            <Input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <Button type="submit" variant="primary" loading={busy} className="w-full">
            Đăng nhập
          </Button>
        </form>
        {isMockMode && (
          <div className="mt-5 border-t border-line pt-4">
            <p className="mb-2 text-small text-ink-muted">Chế độ demo, chọn tài khoản theo vai trò:</p>
            <div className="flex flex-wrap gap-1.5">
              {DEMO_ACCOUNTS.map(([name, label]) => (
                <button
                  key={name}
                  type="button"
                  className="rounded-full border border-field px-2.5 py-1 text-small text-ink-muted hover:border-primary hover:text-primary"
                  onClick={() => {
                    setUsername(name);
                    setPassword('demo123');
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </main>
  );
}
