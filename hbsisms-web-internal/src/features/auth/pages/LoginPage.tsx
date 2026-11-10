import { useEffect, useState, type FormEvent } from 'react';
import { Fish } from 'lucide-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '@/shared/api/client';
import { isMockMode } from '@/shared/api/config';
import { isValidVietnamPhone } from '@/shared/utils/phone';
import { Alert, Button, Field, Input } from '@/shared/ui';
import OtpCodeInput from '../components/OtpCodeInput';
import { useAuth } from '../context';
import { homePathForRole } from '../homePath';
import {
  clearPhoneAuthSession,
  confirmPhoneOtp,
  mapFirebaseAuthError,
  sendPhoneOtp,
} from '../phoneAuth';

const DEMO_ACCOUNTS = [
  ['owner', 'Chủ cửa hàng'],
  ['sales1', 'Bán hàng'],
  ['kho1', 'Thủ kho'],
  ['giao1', 'Giao hàng'],
  ['admin', 'Quản trị'],
] as const;

const RESEND_SECONDS = 45;

type Mode = 'otp' | 'password';
type OtpStep = 'phone' | 'code';

export default function LoginPage() {
  const { user, login, loginWithOtp } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const from = (location.state as { from?: string } | null)?.from;

  const [mode, setMode] = useState<Mode>('otp');

  // --- password ---
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  // --- otp ---
  const [otpStep, setOtpStep] = useState<OtpStep>('phone');
  const [phone, setPhone] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [otpResetKey, setOtpResetKey] = useState(0);
  const [otpMode, setOtpMode] = useState<'stub' | 'firebase'>('stub');
  const [otpBusy, setOtpBusy] = useState(false);
  const [otpError, setOtpError] = useState<string | null>(null);
  const [resendLeft, setResendLeft] = useState(0);

  useEffect(() => {
    if (resendLeft <= 0) return undefined;
    const timer = window.setInterval(() => {
      setResendLeft((s) => (s <= 1 ? 0 : s - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendLeft]);

  if (user) {
    return <Navigate to={from ?? homePathForRole(user.roleCode)} replace />;
  }

  const switchMode = (next: Mode) => {
    setMode(next);
    setPasswordError(null);
    setOtpError(null);
    if (next === 'password') {
      clearPhoneAuthSession();
      setOtpStep('phone');
      setOtpCode('');
      setResendLeft(0);
    }
  };

  const submitPassword = async (event: FormEvent) => {
    event.preventDefault();
    setPasswordTouched(true);
    if (!username.trim() || !password) return;
    setPasswordBusy(true);
    setPasswordError(null);
    try {
      const loggedIn = await login({ username: username.trim(), password });
      navigate(from ?? homePathForRole(loggedIn.roleCode), { replace: true });
    } catch (e) {
      setPasswordError(e instanceof Error ? e.message : 'Không đăng nhập được');
    } finally {
      setPasswordBusy(false);
    }
  };

  const submitPhone = async (event: FormEvent) => {
    event.preventDefault();
    if (otpBusy) return;
    if (!isValidVietnamPhone(phone)) {
      setOtpError('Vui lòng nhập số điện thoại Việt Nam hợp lệ.');
      return;
    }
    setOtpBusy(true);
    setOtpError(null);
    try {
      const { mode: sentMode } = await sendPhoneOtp(phone);
      setOtpMode(sentMode);
      setOtpStep('code');
      setOtpCode('');
      setOtpResetKey((k) => k + 1);
      setResendLeft(RESEND_SECONDS);
    } catch (e) {
      setOtpError(mapFirebaseAuthError(e));
      clearPhoneAuthSession();
    } finally {
      setOtpBusy(false);
    }
  };

  const submitOtp = async (event: FormEvent) => {
    event.preventDefault();
    if (otpBusy) return;
    const code = otpCode.replace(/\D/g, '');
    if (code.length !== 6) {
      setOtpError('Vui lòng nhập đủ 6 số OTP.');
      return;
    }
    setOtpBusy(true);
    setOtpError(null);
    try {
      const idToken = await confirmPhoneOtp(code, phone);
      const loggedIn = await loginWithOtp({ idToken });
      clearPhoneAuthSession();
      navigate(from ?? homePathForRole(loggedIn.roleCode), { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setOtpError(e.message || 'Tài khoản chưa được cấp. Liên hệ Admin để gắn số điện thoại.');
      } else {
        setOtpError(e instanceof ApiError ? e.message : mapFirebaseAuthError(e));
      }
    } finally {
      setOtpBusy(false);
    }
  };

  const resendOtp = async () => {
    if (otpBusy || resendLeft > 0) return;
    setOtpBusy(true);
    setOtpError(null);
    try {
      const { mode: sentMode } = await sendPhoneOtp(phone);
      setOtpMode(sentMode);
      setOtpCode('');
      setOtpResetKey((k) => k + 1);
      setResendLeft(RESEND_SECONDS);
    } catch (e) {
      setOtpError(mapFirebaseAuthError(e));
      clearPhoneAuthSession();
    } finally {
      setOtpBusy(false);
    }
  };

  const backToPhone = () => {
    clearPhoneAuthSession();
    setOtpStep('phone');
    setOtpCode('');
    setResendLeft(0);
    setOtpError(null);
  };

  return (
    <main className="flex min-h-full items-center justify-center bg-sidebar p-4">
      <div id="recaptcha-container" />
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

        <div className="mb-4 flex gap-1 rounded-md bg-sidebar p-1" role="tablist" aria-label="Hình thức đăng nhập">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'otp'}
            className={`flex-1 rounded px-3 py-2 text-small font-medium ${mode === 'otp' ? 'bg-card text-ink shadow-sm' : 'text-ink-muted'}`}
            onClick={() => switchMode('otp')}
          >
            OTP (SĐT)
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'password'}
            className={`flex-1 rounded px-3 py-2 text-small font-medium ${mode === 'password' ? 'bg-card text-ink shadow-sm' : 'text-ink-muted'}`}
            onClick={() => switchMode('password')}
          >
            Mật khẩu
          </button>
        </div>

        {mode === 'otp' ? (
          otpStep === 'phone' ? (
            <form onSubmit={submitPhone} noValidate className="space-y-3">
              {otpError && <Alert tone="danger">{otpError}</Alert>}
              {isMockMode && (
                <Alert tone="warning">
                  OTP cần API thật (`VITE_API_MODE=http`). Chế độ mock chỉ dùng tab Mật khẩu.
                </Alert>
              )}
              <Field label="Số điện thoại" required>
                <Input
                  autoFocus
                  inputMode="numeric"
                  autoComplete="tel"
                  placeholder="0901234567"
                  value={phone}
                  onChange={(e) => {
                    setPhone(e.target.value.replace(/\D/g, '').slice(0, 10));
                    if (otpError) setOtpError(null);
                  }}
                />
              </Field>
              <Button type="submit" variant="primary" loading={otpBusy} className="w-full" disabled={isMockMode}>
                Nhận mã OTP
              </Button>
            </form>
          ) : (
            <form onSubmit={submitOtp} noValidate className="space-y-3">
              {otpError && <Alert tone="danger">{otpError}</Alert>}
              <p className="text-small text-ink-muted">
                {otpMode === 'firebase'
                  ? `Mã đã gửi tới ${phone}.`
                  : `Stub: nhập bất kỳ 6 số cho ${phone}.`}{' '}
                <button type="button" className="text-primary hover:underline" onClick={backToPhone}>
                  Nhập lại số
                </button>
              </p>
              <OtpCodeInput
                value={otpCode}
                resetKey={otpResetKey}
                disabled={otpBusy}
                onChange={(code) => {
                  setOtpCode(code);
                  if (otpError) setOtpError(null);
                }}
              />
              <Button type="submit" variant="primary" loading={otpBusy} className="w-full">
                Xác nhận
              </Button>
              <p className="text-center text-small text-ink-muted">
                {resendLeft > 0 ? (
                  <span>
                    Nhận lại mã ({String(Math.floor(resendLeft / 60)).padStart(2, '0')}:
                    {String(resendLeft % 60).padStart(2, '0')})
                  </span>
                ) : (
                  <button
                    type="button"
                    className="text-primary hover:underline disabled:opacity-50"
                    onClick={resendOtp}
                    disabled={otpBusy}
                  >
                    Nhận lại mã
                  </button>
                )}
              </p>
            </form>
          )
        ) : (
          <form onSubmit={submitPassword} noValidate className="space-y-3">
            {passwordError && <Alert tone="danger">{passwordError}</Alert>}
            <Field
              label="Tên đăng nhập"
              required
              error={passwordTouched && !username.trim() ? 'Vui lòng nhập tên đăng nhập' : undefined}
            >
              <Input
                autoFocus
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </Field>
            <Field
              label="Mật khẩu"
              required
              error={passwordTouched && !password ? 'Vui lòng nhập mật khẩu' : undefined}
            >
              <Input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
            <Button type="submit" variant="primary" loading={passwordBusy} className="w-full">
              Đăng nhập
            </Button>
          </form>
        )}

        {isMockMode && mode === 'password' && (
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
