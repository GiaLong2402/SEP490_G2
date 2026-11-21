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

  const tabClass = (active: boolean) =>
    [
      'flex-1 rounded-md px-3 py-2 text-small font-medium transition-colors',
      active
        ? 'bg-card text-ink shadow-sm'
        : 'text-ink-muted hover:text-ink',
    ].join(' ');

  return (
    <main className="flex min-h-full flex-col md:flex-row">
      <div id="recaptcha-container" />

      {/* Brand panel */}
      <section
        className="login-brand relative flex shrink-0 flex-col justify-center overflow-hidden px-6 py-10 text-white md:min-h-full md:w-[48%] md:px-12 md:py-12 lg:w-[46%] lg:px-16"
        aria-label="Thương hiệu"
      >
        <div className="login-brand-grid pointer-events-none absolute inset-0" aria-hidden />
        <div className="login-brand-orb login-brand-orb--a" aria-hidden />
        <div className="login-brand-orb login-brand-orb--b" aria-hidden />
        <svg className="login-brand-wave" viewBox="0 0 800 180" preserveAspectRatio="none" aria-hidden>
          <path
            fill="rgba(255,255,255,0.06)"
            d="M0 80 C 120 20, 220 140, 360 90 C 500 40, 620 130, 800 70 L 800 180 L 0 180 Z"
          />
          <path
            fill="rgba(4, 31, 42, 0.35)"
            d="M0 110 C 160 60, 280 150, 420 105 C 560 60, 680 140, 800 100 L 800 180 L 0 180 Z"
          />
        </svg>

        <div className="login-fade-up relative z-[1]">
          <span className="inline-flex items-center gap-2 rounded-md bg-white/10 px-2.5 py-1 text-[11px] font-medium tracking-[0.12em] text-white/80 ring-1 ring-inset ring-white/15">
            HBSISMS
          </span>
        </div>

        <div className="login-fade-up-delay relative z-[1] max-w-md py-10 md:py-0">
          <span className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/12 ring-1 ring-inset ring-white/30 md:mb-8 md:h-[4.5rem] md:w-[4.5rem]">
            <Fish size={34} className="md:h-11 md:w-11" strokeWidth={1.6} aria-hidden />
          </span>
          <h1 className="login-brand-title text-[2rem] md:text-[2.55rem] lg:text-[2.9rem]">
            Hải sản
            <br className="hidden sm:block" />
            {' '}
            Hòa Bình
          </h1>
          <p className="mt-4 max-w-[22rem] text-[14px] leading-relaxed text-white/75 md:mt-5 md:text-[15.5px]">
            Hệ thống quản lý kho và bán hàng nội bộ — lạnh, tồn, đơn, giao.
          </p>
        </div>

      </section>

      {/* Form panel */}
      <section className="login-form-panel relative flex flex-1 items-center justify-center px-4 py-12 md:px-12 lg:px-16">
        <div className="login-fade-up-delay login-form-card w-full max-w-[400px] px-7 py-8 md:max-w-[428px] md:px-9 md:py-9">
          <header className="mb-6">
            <h2 className="login-brand-title text-[1.3rem] text-ink md:text-[1.45rem]">Đăng nhập</h2>
          </header>

          <div
            className="mb-6 flex gap-1 rounded-xl bg-[#e8f1f5] p-1"
            role="tablist"
            aria-label="Hình thức đăng nhập"
          >
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'otp'}
              className={tabClass(mode === 'otp')}
              onClick={() => switchMode('otp')}
            >
              OTP (SĐT)
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'password'}
              className={tabClass(mode === 'password')}
              onClick={() => switchMode('password')}
            >
              Mật khẩu
            </button>
          </div>

          <div key={`${mode}-${otpStep}`} className="login-fade-up">
            {mode === 'otp' ? (
              otpStep === 'phone' ? (
                <form onSubmit={submitPhone} noValidate className="space-y-4">
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
                  <Button type="submit" variant="primary" loading={otpBusy} className="login-submit w-full" disabled={isMockMode}>
                    Nhận mã OTP
                  </Button>
                </form>
              ) : (
                <form onSubmit={submitOtp} noValidate className="space-y-4">
                  {otpError && <Alert tone="danger">{otpError}</Alert>}
                  <p className="text-small text-ink-muted">
                    {otpMode === 'firebase'
                      ? `Mã đã gửi tới ${phone}.`
                      : `Stub: nhập bất kỳ 6 số cho ${phone}.`}{' '}
                    <button type="button" className="font-medium text-primary hover:underline" onClick={backToPhone}>
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
                  <Button type="submit" variant="primary" loading={otpBusy} className="login-submit w-full">
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
                        className="font-medium text-primary hover:underline disabled:opacity-50"
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
              <form onSubmit={submitPassword} noValidate className="space-y-4">
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
                <Button type="submit" variant="primary" loading={passwordBusy} className="login-submit w-full">
                  Đăng nhập
                </Button>
              </form>
            )}
          </div>

          {isMockMode && mode === 'password' && (
            <div className="mt-6 border-t border-line pt-5">
              <p className="mb-2.5 text-small text-ink-muted">Chế độ demo — chọn vai trò:</p>
              <div className="flex flex-wrap gap-1.5">
                {DEMO_ACCOUNTS.map(([name, label]) => (
                  <button
                    key={name}
                    type="button"
                    className="rounded-md border border-field bg-[#f4f8fa] px-2.5 py-1 text-small text-ink-muted transition-colors hover:border-primary hover:bg-primary-soft hover:text-primary"
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
      </section>
    </main>
  );
}
